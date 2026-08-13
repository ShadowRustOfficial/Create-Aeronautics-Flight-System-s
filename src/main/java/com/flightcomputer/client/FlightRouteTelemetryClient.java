package com.flightcomputer.client;

import com.flightcomputer.avionics.FlightMode;
import com.flightcomputer.network.FlightRouteTelemetryNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side cache for authoritative Route/Flight Control state. */
public final class FlightRouteTelemetryClient {
    private static final long MAX_AGE_MS = 2000L;
    private static final Map<UUID, FlightRouteTelemetryNetwork.RouteStatePayload> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SEEN = new ConcurrentHashMap<>();

    private FlightRouteTelemetryClient() { }

    public static void accept(FlightRouteTelemetryNetwork.RouteStatePayload payload) {
        if (payload == null) return;
        SNAPSHOTS.put(payload.controllerId(), payload);
        LAST_SEEN.put(payload.controllerId(), System.currentTimeMillis());
    }

    public static FlightRouteTelemetryNetwork.RouteStatePayload get(UUID controllerId) {
        if (controllerId == null) return null;
        Long seen = LAST_SEEN.get(controllerId);
        if (seen == null || System.currentTimeMillis() - seen > MAX_AGE_MS) {
            SNAPSHOTS.remove(controllerId);
            LAST_SEEN.remove(controllerId);
            return null;
        }
        return SNAPSHOTS.get(controllerId);
    }

    public static boolean isStabiliserActive(UUID controllerId) {
        FlightRouteTelemetryNetwork.RouteStatePayload payload = get(controllerId);
        return payload != null && payload.stabiliser();
    }

    public static boolean isAutopilotActive(UUID controllerId) {
        FlightRouteTelemetryNetwork.RouteStatePayload payload = get(controllerId);
        return payload != null && payload.engaged() && payload.mode() == FlightMode.AUTOPILOT.ordinal();
    }

    /** True when either stabilisation or autopilot is actively controlling the vessel. */
    public static boolean isFlightControlActive(UUID controllerId) {
        return isStabiliserActive(controllerId) || isAutopilotActive(controllerId);
    }
}
