package com.flightcomputer.control;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final allocator: combined controller output is converted into one physical actuator command per thruster. */
public final class ThrustAllocator {
    private static final int ITERATIONS = 8;
    private final Map<String, PropulsionSource> lastActiveSources = new LinkedHashMap<>();
    private double lastThermalLoad;
    public double getLastThermalLoad() { return lastThermalLoad; }

    public void applyCombined(ThrusterRegistry registry, VehicleState state,
                              Map<ControlAxis, Double> stabiliser, Map<ControlAxis, Double> autopilot) {
        if (state == null) { hardStop(); lastThermalLoad = 0.0D; return; }
        Quaterniond vehicleRotation = vehicleRotation(state);
        ControlWrench target = ControlWrench.fromAxes(stabiliser).add(ControlWrench.fromAxes(autopilot)).toWorld(vehicleRotation);

        Map<String, ThrusterLink> unique = collectActiveSources(registry, stabiliser, autopilot);
        for (Map.Entry<String, PropulsionSource> previous : lastActiveSources.entrySet()) {
            if (!unique.containsKey(previous.getKey())) previous.getValue().applyThrust(0.0D);
        }
        lastActiveSources.clear();
        for (Map.Entry<String, ThrusterLink> entry : unique.entrySet()) lastActiveSources.put(entry.getKey(), entry.getValue().source);

        if (target.normSquared() <= 1.0e-12 || unique.isEmpty()) {
            hardStop();
            lastThermalLoad = 0.0D;
            return;
        }

        List<ThrusterLink> sources = List.copyOf(unique.values());
        double[] commands = seedVectorBanks(sources, target, vehicleRotation);

        // Refine the bank-balanced seed against the complete six-axis wrench. This retains the
        // dynamic same-vector sharing while allowing differential thrust for pitch/roll/yaw torque.
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[] achieved = achieved(sources, commands, vehicleRotation);
            for (int i = 0; i < sources.size(); i++) {
                double available = sources.get(i).source.getAvailableThrust();
                if (available <= 0.0D) { commands[i] = 0.0D; continue; }
                double[] contribution = contribution(sources.get(i), available, vehicleRotation);
                double dot = 0.0D, magnitude = 0.0D;
                for (int k = 0; k < 6; k++) {
                    double residual = target.component(k) - achieved[k];
                    dot += residual * contribution[k];
                    magnitude += contribution[k] * contribution[k];
                }
                if (magnitude <= 1.0e-9) continue;
                double delta = dot / magnitude;
                commands[i] = clamp(commands[i] + delta, 0.0D, 1.0D);
                achieved = achieved(sources, commands, vehicleRotation);
            }
        }

        double load = 0.0D, authority = 0.0D;
        for (int i = 0; i < sources.size(); i++) {
            double max = Math.max(0.0D, sources.get(i).source.getMaxThrust());
            load += commands[i] * max;
            authority += max;
            sources.get(i).source.applyThrust(commands[i] * sources.get(i).polarity);
        }
        lastThermalLoad = authority <= 0.0D ? 0.0D : Math.min(1.0D, load / authority);
    }

    public void apply(ThrusterRegistry registry, FlightMode mode, Map<ControlAxis, Double> commands) {
        applyCombined(registry, new VehicleState(), mode == FlightMode.STABILIZE ? commands : Map.of(), mode == FlightMode.CRUISE ? commands : Map.of());
    }

    /** Immediate zero of every actuator owned by the allocator. */
    public void hardStop() {
        for (PropulsionSource source : lastActiveSources.values()) source.applyThrust(0.0D);
        lastActiveSources.clear();
    }

    /**
     * Seed each physical vector as a bank. Required force is divided by the combined authority of
     * all currently linked thrusters on that vector, so adding another same-vector thruster lowers
     * the normal per-thruster output instead of increasing the vessel's commanded acceleration.
     */
    private double[] seedVectorBanks(List<ThrusterLink> sources, ControlWrench target, Quaterniond rotation) {
        double[] commands = new double[sources.size()];
        for (VectorDirection direction : VectorDirection.values()) {
            double totalAuthority = 0.0D;
            Vector3d unitForce = null;
            for (ThrusterLink link : sources) {
                if (link.direction != direction) continue;
                double available = Math.max(0.0D, link.source.getAvailableThrust());
                if (available <= 0.0D) continue;
                totalAuthority += available;
                if (unitForce == null) {
                    unitForce = new Vector3d(direction.x(), direction.y(), direction.z());
                    rotation.transform(unitForce);
                    unitForce.mul(link.polarity);
                    double length = unitForce.length();
                    if (length > 1.0e-9) unitForce.div(length);
                }
            }
            if (unitForce == null || totalAuthority <= 0.0D) continue;

            double required = target.forceX * unitForce.x + target.forceY * unitForce.y + target.forceZ * unitForce.z;
            double fraction = clamp(required / totalAuthority, 0.0D, 1.0D);
            for (int i = 0; i < sources.size(); i++) {
                ThrusterLink link = sources.get(i);
                if (link.direction == direction && link.source.getAvailableThrust() > 0.0D)
                    commands[i] = fraction;
            }
        }
        return commands;
    }

    private Map<String, ThrusterLink> collectActiveSources(ThrusterRegistry registry, Map<ControlAxis, Double> stabiliser, Map<ControlAxis, Double> autopilot) {
        Map<String, ThrusterLink> unique = new LinkedHashMap<>();
        if (stabiliser != null && !stabiliser.isEmpty()) for (ThrusterLink link : registry.getAllLinks(FlightMode.STABILIZE)) unique.putIfAbsent(link.source.getId(), link);
        if (autopilot != null && !autopilot.isEmpty()) for (ThrusterLink link : registry.getAllLinks(FlightMode.CRUISE)) unique.putIfAbsent(link.source.getId(), link);
        return unique;
    }

    private double[] achieved(List<ThrusterLink> links, double[] commands, Quaterniond rotation) {
        double[] result = new double[6];
        for (int i = 0; i < links.size(); i++) {
            double[] contribution = contribution(links.get(i), links.get(i).source.getAvailableThrust(), rotation);
            for (int k = 0; k < 6; k++) result[k] += contribution[k] * commands[i];
        }
        return result;
    }

    private double[] contribution(ThrusterLink link, double thrust, Quaterniond rotation) {
        VectorDirection d = link.direction;
        Vector3d force = new Vector3d(d.x(), d.y(), d.z()).mul(thrust * link.polarity);
        Vector3d r = new Vector3d(link.source.getMountOffset());
        rotation.transform(force);
        rotation.transform(r);
        double fx = force.x, fy = force.y, fz = force.z;
        return new double[]{fx, fy, fz, r.y * fz - r.z * fy, r.z * fx - r.x * fz, r.x * fy - r.y * fx};
    }

    private static Quaterniond vehicleRotation(VehicleState state) {
        return new Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
