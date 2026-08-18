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

        // Stabilisation is a hover controller. Do not trade translation for an attitude correction:
        // pitch/yaw are intentionally filtered out upstream and only roll gets lateral vector
        // authority. A large force penalty makes the allocator prefer balanced opposing vector
        // thrusters when roll correction is required instead of translating the craft.
        boolean stabiliserOnly = autopilot == null || autopilot.isEmpty();
        double forceScale = Math.max(1.0D, totalAuthority(sources)) * (stabiliserOnly ? 100.0D : 1.0D);
        double torqueScale = Math.max(1.0D, totalTorqueAuthority(sources, state, vehicleRotation));

        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[] achieved = achieved(sources, commands, vehicleRotation, state);
            for (int i = 0; i < sources.size(); i++) {
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

    public void hardStop() {
        for (PropulsionSource source : lastActiveSources.values()) source.applyThrust(0.0D);
        lastActiveSources.clear();
        resetLastWrench();
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
        if (stabiliser != null && !stabiliser.isEmpty()) addStabiliserSources(registry, stabiliser, unique);
        if (autopilot != null && !autopilot.isEmpty()) {
            for (ThrusterLink link : registry.getAllLinks(FlightMode.CRUISE)) unique.putIfAbsent(link.source.getId(), link);
        }
        return unique;
    }

    /** Stabiliser actuator policy: UP for lift, EAST/WEST only for roll. */
    private static void addStabiliserSources(ThrusterRegistry registry, Map<ControlAxis, Double> commands,
                                             Map<String, ThrusterLink> unique) {
        if (nonZero(commands.get(ControlAxis.VERTICAL))) {
            // Never fire the DOWN bank for ordinary stabilisation. Descent is achieved by reducing
            // the common upward thrust; the DOWN bank is reserved for deliberate manoeuvres.
            addDirectionSources(registry, VectorDirection.UP, unique, FlightMode.STABILIZE);
        }
        if (nonZero(commands.get(ControlAxis.ROLL))) {
            addDirectionSources(registry, VectorDirection.EAST, unique, FlightMode.STABILIZE);
            addDirectionSources(registry, VectorDirection.WEST, unique, FlightMode.STABILIZE);
        }
    }

    private static void addDirectionSources(ThrusterRegistry registry, VectorDirection direction,
                                            Map<String, ThrusterLink> unique, FlightMode mode) {
        for (ThrusterLink link : registry.getLinks(mode, direction)) {
            if (link != null && link.source != null) unique.putIfAbsent(link.source.getId(), link);
        }
    }

    private static boolean nonZero(Double value) {
        return value != null && Double.isFinite(value) && Math.abs(value) > 1.0e-6D;
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
        Vector3d r = new Vector3d(link.source.getMountOffset())
                .sub(state.comX, state.comY, state.comZ);
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
