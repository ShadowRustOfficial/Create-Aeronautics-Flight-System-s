package com.flightcomputer.control;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Queues short independent translation pulses without altering the working autopilot guidance loop. */
public final class ManualControlBridge {
    private static final ConcurrentMap<UUID, Pulse> PENDING = new ConcurrentHashMap<>();

    private ManualControlBridge() { }

    public static void request(UUID controllerId, ControlAxis axis, double value) {
        if (controllerId == null || axis == null || !Double.isFinite(value)) return;
        PENDING.put(controllerId, new Pulse(axis, Math.max(-0.65D, Math.min(0.65D, value))));
    }

    public static Pulse consume(UUID controllerId) {
        return controllerId == null ? null : PENDING.remove(controllerId);
    }

    public record Pulse(ControlAxis axis, double value) { }
}