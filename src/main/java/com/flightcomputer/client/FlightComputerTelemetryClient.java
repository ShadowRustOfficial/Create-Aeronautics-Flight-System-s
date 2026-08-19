package com.flightcomputer.client;

import com.flightcomputer.network.FlightComputerNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache for authoritative flight-controller telemetry. */
public final class FlightComputerTelemetryClient {
    private static final Map<UUID, FlightComputerNetwork.TelemetryPayload> SNAPSHOTS = new ConcurrentHashMap<>();

    private FlightComputerTelemetryClient() { }

    public static void accept(FlightComputerNetwork.TelemetryPayload payload) {
        if (payload != null) SNAPSHOTS.put(payload.controllerId(), payload);
    }

    public static FlightComputerNetwork.TelemetryPayload get(UUID id) {
        return id == null ? null : SNAPSHOTS.get(id);
    }

    public static List<FlightComputerNetwork.TelemetryPayload> snapshots() {
        return new ArrayList<>(SNAPSHOTS.values());
    }
}
