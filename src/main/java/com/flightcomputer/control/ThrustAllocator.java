package com.flightcomputer.control;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final allocator: combined controller output is converted into one physical actuator command per thruster. */
public final class ThrustAllocator {
    private static final int ITERATIONS = 10;
    private final Map<String, PropulsionSource> lastActiveSources = new LinkedHashMap<>();
    private double lastThermalLoad;
    private double lastWorldForceX, lastWorldForceY, lastWorldForceZ;
    private double lastWorldTorqueX, lastWorldTorqueY, lastWorldTorqueZ;

    public double getLastThermalLoad() { return lastThermalLoad; }
    public double getLastWorldForceX() { return lastWorldForceX; }
    public double getLastWorldForceY() { return lastWorldForceY; }
    public double getLastWorldForceZ() { return lastWorldForceZ; }
    public double getLastWorldTorqueX() { return lastWorldTorqueX; }
    public double getLastWorldTorqueY() { return lastWorldTorqueY; }
    public double getLastWorldTorqueZ() { return lastWorldTorqueZ; }

    /** Immediately removes all commanded thrust and clears allocator state. */
    public void hardStop() {
        for (PropulsionSource source : lastActiveSources.values()) {
            try { source.applyThrust(0.0D); } catch (RuntimeException ignored) { }
        }
        lastActiveSources.clear();
        lastThermalLoad = 0.0D;
        resetLastWrench();
    }

    public void applyCombined(ThrusterRegistry registry, VehicleState state,
                              Map<ControlAxis, Double> stabiliser, Map<ControlAxis, Double> autopilot) {
        if (state == null) { hardStop(); lastThermalLoad = 0.0D; resetLastWrench(); return; }
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
            resetLastWrench();
            return;
        }

        List<ThrusterLink> sources = List.copyOf(unique.values());
        double[] commands = seedVectorBanks(sources, target, vehicleRotation);
        boolean stabilizerOnly = autopilot == null || autopilot.isEmpty();
        boolean[] lockedVerticalSources = stabilizerOnly && stabiliser != null && !stabiliser.isEmpty()
                ? lockStabilizerVerticalBank(sources, commands, stabiliser.getOrDefault(ControlAxis.VERTICAL, 0.0D))
                : new boolean[sources.size()];

        double forceScale = Math.max(1.0D, totalAuthority(sources));
        double torqueScale = Math.max(1.0D, totalTorqueAuthority(sources, state, vehicleRotation));

        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[] achieved = achieved(sources, commands, vehicleRotation, state);
            for (int i = 0; i < sources.size(); i++) {
                if (lockedVerticalSources[i]) continue;
                double available = sources.get(i).source.getAvailableThrust();
                if (available <= 0.0D) { commands[i] = 0.0D; continue; }
                double[] contribution = contribution(sources.get(i), available, state, vehicleRotation);
                double dot = 0.0D, magnitude = 0.0D;
                for (int k = 0; k < 6; k++) {
                    double scale = k < 3 ? forceScale : torqueScale;
                    double residual = (target.component(k) - achieved[k]) / scale;
                    double weightedContribution = contribution[k] / scale;
                    dot += residual * weightedContribution;
                    magnitude += weightedContribution * weightedContribution;
                }
                if (magnitude <= 1.0e-12) continue;
                commands[i] = clamp(commands[i] + dot / magnitude, 0.0D, 1.0D);
            }
        }

        double load = 0.0D, authority = 0.0D;
        for (int i = 0; i < sources.size(); i++) {
            double max = Math.max(0.0D, sources.get(i).source.getMaxThrust());
            load += commands[i] * max;
            authority += max;
            sources.get(i).source.applyThrust(commands[i] * sources.get(i).polarity);
        }

        double[] finalAchieved = achieved(sources, commands, vehicleRotation, state);
        lastWorldForceX = finalAchieved[0]; lastWorldForceY = finalAchieved[1]; lastWorldForceZ = finalAchieved[2];
        lastWorldTorqueX = finalAchieved[3]; lastWorldTorqueY = finalAchieved[4]; lastWorldTorqueZ = finalAchieved[5];
        lastThermalLoad = authority <= 0.0D ? 0.0D : Math.min(1.0D, load / authority);
    }

    public void apply(ThrusterRegistry registry, FlightMode mode, Map<ControlAxis, Double> commands) {
        applyCombined(registry, new VehicleState(), mode == FlightMode.STABILIZE ? commands : Map.of(), mode == FlightMode.CRUISE ? commands : Map.of());
    }

    /**
     * STABILIZE/altitude-hold vertical motion is common-mode lift only. Every physically upward
     * vertical thruster receives the same command fraction; the six-axis solver is not allowed to
     * steal thrust from one side of the lift bank to manufacture pitch/roll torque. That prevents
     * a vertical ascent from becoming a pitch manoeuvre and keeps the vessel level as it rises.
     */
    private boolean[] lockStabilizerVerticalBank(List<ThrusterLink> sources, double[] commands, double requestedVerticalForce) {
        boolean[] locked = new boolean[sources.size()];
        double upwardAuthority = 0.0D;
        for (int i = 0; i < sources.size(); i++) {
            ThrusterLink link = sources.get(i);
            double bodyY = link.direction.y() * link.polarity;
            if (Math.abs(link.direction.y()) < 1.0e-9 || bodyY <= 0.0D) continue;
            double available = Math.max(0.0D, link.source.getAvailableThrust());
            if (available <= 0.0D) continue;
            upwardAuthority += available * bodyY;
            locked[i] = true;
        }
        if (upwardAuthority <= 0.0D) return locked;
        double fraction = clamp(Math.max(0.0D, requestedVerticalForce) / upwardAuthority, 0.0D, 1.0D);
        for (int i = 0; i < sources.size(); i++) if (locked[i]) commands[i] = fraction;
        return locked;
    }

    /** Seed same-vector thrusters from the combined vector authority before six-axis correction. */
    private double[] seedVectorBanks(List<ThrusterLink> sources, ControlWrench target, Quaterniond rotation) {
        double[] commands = new double[sources.size()];
        for (VectorDirection direction : VectorDirection.values()) {
            double authority = 0.0D;
            Vector3d unitForce = null;
            for (ThrusterLink link : sources) {
                if (link.direction != direction) continue;
                double available = Math.max(0.0D, link.source.getAvailableThrust());
                if (available <= 0.0D) continue;
                authority += available;
                if (unitForce == null) {
                    unitForce = new Vector3d(direction.x(), direction.y(), direction.z());
                    rotation.transform(unitForce).mul(link.polarity);
                    double length = unitForce.length();
                    if (length > 1.0e-9) unitForce.div(length);
                }
            }
            if (unitForce == null || authority <= 0.0D) continue;
            double required = target.forceX * unitForce.x + target.forceY * unitForce.y + target.forceZ * unitForce.z;
            double fraction = clamp(required / authority, 0.0D, 1.0D);
            for (int i = 0; i < sources.size(); i++) {
                ThrusterLink link = sources.get(i);
                if (link.direction == direction && link.source.getAvailableThrust() > 0.0D) commands[i] = fraction;
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

    private double[] achieved(List<ThrusterLink> links, double[] commands, Quaterniond rotation, VehicleState state) {
        double[] result = new double[6];
        for (int i = 0; i < links.size(); i++) {
            double[] c = contribution(links.get(i), links.get(i).source.getAvailableThrust(), state, rotation);
            for (int k = 0; k < 6; k++) result[k] += c[k] * commands[i];
        }
        return result;
    }

    private double[] contribution(ThrusterLink link, double thrust, VehicleState state, Quaterniond rotation) {
        VectorDirection d = link.direction;
        Vector3d force = new Vector3d(d.x(), d.y(), d.z()).mul(thrust * link.polarity);
        Vector3d r = new Vector3d(link.source.getMountOffset()).sub(state.comX, state.comY, state.comZ);
        rotation.transform(force);
        rotation.transform(r);
        double fx = force.x, fy = force.y, fz = force.z;
        return new double[]{fx, fy, fz, r.y * fz - r.z * fy, r.z * fx - r.x * fz, r.x * fy - r.y * fx};
    }

    private static double totalAuthority(List<ThrusterLink> sources) {
        double total = 0.0D;
        for (ThrusterLink link : sources) total += Math.max(0.0D, link.source.getAvailableThrust());
        return total;
    }

    private static double totalTorqueAuthority(List<ThrusterLink> sources, VehicleState state, Quaterniond rotation) {
        double total = 0.0D;
        for (ThrusterLink link : sources) {
            double thrust = Math.max(0.0D, link.source.getAvailableThrust());
            if (thrust <= 0.0D) continue;
            Vector3d force = new Vector3d(link.direction.x(), link.direction.y(), link.direction.z()).mul(thrust * link.polarity);
            Vector3d r = new Vector3d(link.source.getMountOffset()).sub(state.comX, state.comY, state.comZ);
            rotation.transform(force); rotation.transform(r);
            total += r.cross(force, new Vector3d()).length();
        }
        return total;
    }

    private void resetLastWrench() {
        lastWorldForceX = lastWorldForceY = lastWorldForceZ = 0.0D;
        lastWorldTorqueX = lastWorldTorqueY = lastWorldTorqueZ = 0.0D;
    }

    private static Quaterniond vehicleRotation(VehicleState state) {
        return new Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
