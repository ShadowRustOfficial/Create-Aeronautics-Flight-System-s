package com.flightcomputer.client;

import com.flightcomputer.network.FlightRouteTelemetryNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side cache for authoritative Route/Flight Control state. */
public final class FlightRouteTelemetryClient {
    private static final Map<UUID, FlightRouteTelemetryNetwork.RouteStatePayload> SNAPSHOTS = new ConcurrentHashMap<>();

    private FlightRouteTelemetryClient() { }

    public static void accept(FlightRouteTelemetryNetwork.RouteStatePayload payload) {
        if (payload != null) SNAPSHOTS.put(payload.controllerId(), payload);
    }

    public static FlightRouteTelemetryNetwork.RouteStatePayload get(UUID controllerId) {
        return controllerId == null ? null : SNAPSHOTS.get(controllerId);
    }
}
