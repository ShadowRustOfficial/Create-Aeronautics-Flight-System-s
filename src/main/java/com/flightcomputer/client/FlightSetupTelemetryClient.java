package com.flightcomputer.client;

import com.flightcomputer.network.FlightSetupTelemetryNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache for the controller's calculated hover/stabilisation setup guidance. */
public final class FlightSetupTelemetryClient {
    private static final Map<UUID, FlightSetupTelemetryNetwork.SetupPayload> SNAPSHOTS = new ConcurrentHashMap<>();

    private FlightSetupTelemetryClient() { }

    public static void accept(FlightSetupTelemetryNetwork.SetupPayload payload) {
        if (payload != null) SNAPSHOTS.put(payload.controllerId(), payload);
    }

    public static FlightSetupTelemetryNetwork.SetupPayload get(UUID controllerId) {
        return controllerId == null ? null : SNAPSHOTS.get(controllerId);
    }
}
