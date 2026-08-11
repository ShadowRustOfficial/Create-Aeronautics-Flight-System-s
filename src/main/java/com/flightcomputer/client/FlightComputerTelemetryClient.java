package com.flightcomputer.client;

import com.flightcomputer.map.FlightContact;
import com.flightcomputer.map.FlightContactRegistry;
import com.flightcomputer.network.FlightComputerNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Live authoritative telemetry cache and positional contact feed for Flight Computers. */
public final class FlightComputerTelemetryClient {
    private static final Map<UUID, FlightComputerNetwork.TelemetryPayload> SNAPSHOTS = new ConcurrentHashMap<>();
    private FlightComputerTelemetryClient() { }

    public static void accept(FlightComputerNetwork.TelemetryPayload payload) {
        if (payload == null) return;
        SNAPSHOTS.put(payload.controllerId(), payload);
        // Ship identity is deliberately not inferred from route target names.
        FlightContactRegistry.upsert(new FlightContact(payload.controllerId(), "", "", "",
                payload.x(), payload.y(), payload.z(), payload.speed(), payload.heading(), payload.pitch(), payload.roll(),
                "ACTIVE", System.currentTimeMillis() / 50L));
    }

    public static FlightComputerNetwork.TelemetryPayload get(UUID id) {
        return id == null ? null : SNAPSHOTS.get(id);
    }
}