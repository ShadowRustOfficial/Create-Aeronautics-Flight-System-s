package com.flightcomputer.control;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final allocator: both objectives are combined before any physical output is written. */
public final class ThrustAllocator {
    private static final int ITERATIONS = 6;
    private double lastThermalLoad;
    public double getLastThermalLoad() { return lastThermalLoad; }

    public void applyCombined(ThrusterRegistry registry, Map<ControlAxis, Double> stabiliser, Map<ControlAxis, Double> autopilot) {
        ControlWrench target = ControlWrench.fromAxes(stabiliser).add(ControlWrench.fromAxes(autopilot));
        List<ThrusterLink> links = registry.getAllLinks();
        if (links.isEmpty()) { lastThermalLoad = 0.0D; return; }
        Map<String, ThrusterLink> unique = new LinkedHashMap<>();
        for (ThrusterLink link : links) unique.putIfAbsent(link.source.getId(), link);
        List<ThrusterLink> sources = List.copyOf(unique.values());
        double[] commands = new double[sources.size()];

        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[] achieved = achieved(sources, commands);
            for (int i = 0; i < sources.size(); i++) {
                double available = sources.get(i).source.getAvailableThrust();
                if (available <= 0.0D) { commands[i] = 0.0D; continue; }
                double[] contribution = contribution(sources.get(i), available);
                double dot = 0.0D, magnitude = 0.0D;
                for (int k = 0; k < 6; k++) {
                    double residual = target.component(k) - achieved[k];
                    dot += residual * contribution[k]; magnitude += contribution[k] * contribution[k];
                }
                if (magnitude <= 1.0e-9) continue;
                commands[i] += clamp(dot / magnitude, -commands[i], 1.0D - commands[i]);
                achieved = achieved(sources, commands);
            }
        }

        double load = 0.0D, authority = 0.0D;
        for (int i = 0; i < sources.size(); i++) {
            double max = Math.max(0.0D, sources.get(i).source.getMaxThrust());
            load += commands[i] * max; authority += max;
            sources.get(i).source.applyThrust(commands[i] * sources.get(i).polarity);
        }
        lastThermalLoad = authority <= 0.0D ? 0.0D : Math.min(1.0D, load / authority);
    }

    public void apply(ThrusterRegistry registry, FlightMode mode, Map<ControlAxis, Double> commands) {
        applyCombined(registry, mode == FlightMode.STABILIZE ? commands : Map.of(), mode == FlightMode.CRUISE ? commands : Map.of());
    }

    private double[] achieved(List<ThrusterLink> links, double[] commands) {
        double[] result = new double[6];
        for (int i = 0; i < links.size(); i++) {
            double[] c = contribution(links.get(i), links.get(i).source.getAvailableThrust());
            for (int k = 0; k < 6; k++) result[k] += c[k] * commands[i];
        }
        return result;
    }
    private double[] contribution(ThrusterLink link, double thrust) {
        VectorDirection d = link.direction;
        double fx = d.x() * thrust, fy = d.y() * thrust, fz = d.z() * thrust;
        double[] r = link.source.getMountOffset();
        return new double[]{fx, fy, fz, r[1] * fz - r[2] * fy, r[2] * fx - r[0] * fz, r[0] * fy - r[1] * fx};
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
