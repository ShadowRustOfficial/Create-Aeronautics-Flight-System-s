package com.flightcomputer.control;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final allocator: both objectives are combined before any physical output is written. */
public final class ThrustAllocator {
    private static final int ITERATIONS = 8;
    private double lastThermalLoad;
    public double getLastThermalLoad() { return lastThermalLoad; }

    /**
     * Allocate the combined wrench in world space. Only banks belonging to an active control
     * layer are considered: stabilisation uses STABILIZE links, autopilot uses CRUISE links.
     * This prevents an autopilot-only thruster from unexpectedly fighting manual/stabiliser
     * control and makes the Link Tool's mode selection authoritative.
     */
    public void applyCombined(ThrusterRegistry registry, VehicleState state,
                              Map<ControlAxis, Double> stabiliser, Map<ControlAxis, Double> autopilot) {
        if (state == null) { lastThermalLoad = 0.0D; return; }
        ControlWrench target = ControlWrench.fromAxes(stabiliser).add(ControlWrench.fromAxes(autopilot));
        if (target.normSquared() <= 1.0e-12) {
            zeroActiveSources(registry, stabiliser, autopilot);
            lastThermalLoad = 0.0D;
            return;
        }

        Map<String, ThrusterLink> unique = new LinkedHashMap<>();
        if (stabiliser != null && !stabiliser.isEmpty()) {
            for (ThrusterLink link : registry.getAllLinks(FlightMode.STABILIZE)) unique.putIfAbsent(link.source.getId(), link);
        }
        if (autopilot != null && !autopilot.isEmpty()) {
            for (ThrusterLink link : registry.getAllLinks(FlightMode.CRUISE)) unique.putIfAbsent(link.source.getId(), link);
        }
        List<ThrusterLink> sources = List.copyOf(unique.values());
        if (sources.isEmpty()) { lastThermalLoad = 0.0D; return; }

        double[] commands = new double[sources.size()];
        Quaterniond vehicleRotation = vehicleRotation(state);

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
                commands[i] += clamp(dot / magnitude, -commands[i], 1.0D - commands[i]);
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

    private void zeroActiveSources(ThrusterRegistry registry, Map<ControlAxis, Double> stabiliser, Map<ControlAxis, Double> autopilot) {
        Map<String, ThrusterLink> unique = new LinkedHashMap<>();
        if (stabiliser != null && !stabiliser.isEmpty()) for (ThrusterLink link : registry.getAllLinks(FlightMode.STABILIZE)) unique.putIfAbsent(link.source.getId(), link);
        if (autopilot != null && !autopilot.isEmpty()) for (ThrusterLink link : registry.getAllLinks(FlightMode.CRUISE)) unique.putIfAbsent(link.source.getId(), link);
        for (ThrusterLink link : unique.values()) link.source.applyThrust(0.0D);
    }

    private double[] achieved(List<ThrusterLink> links, double[] commands, Quaterniond rotation) {
        double[] result = new double[6];
        for (int i = 0; i < links.size(); i++) {
            double[] c = contribution(links.get(i), links.get(i).source.getAvailableThrust(), rotation);
            for (int k = 0; k < 6; k++) result[k] += c[k] * commands[i];
        }
        return result;
    }

    private double[] contribution(ThrusterLink link, double thrust, Quaterniond rotation) {
        VectorDirection d = link.direction;
        Vector3d force = new Vector3d(d.x(), d.y(), d.z()).mul(thrust);
        Vector3d r = new Vector3d(link.source.getMountOffset());
        rotation.transform(force);
        rotation.transform(r);
        double fx = force.x, fy = force.y, fz = force.z;
        return new double[]{
                fx, fy, fz,
                r.y * fz - r.z * fy,
                r.z * fx - r.x * fz,
                r.x * fy - r.y * fx
        };
    }

    private static Quaterniond vehicleRotation(VehicleState state) {
        return new Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll);
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
