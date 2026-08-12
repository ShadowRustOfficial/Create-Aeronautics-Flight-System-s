package com.flightcomputer.client;

import com.flightcomputer.map.FlightContact;
import com.flightcomputer.map.FlightContactRegistry;
import com.flightcomputer.network.FlightComputerNetwork;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache for authoritative flight-controller telemetry.
 *
 * The data is deliberately not rendered as a global HUD. Navigation telemetry belongs to the
 * Navigation Console, while object-specific diagnostics are exposed through Create goggles.
 */
public final class FlightComputerTelemetryClient {
    private static final Map<UUID, FlightComputerNetwork.TelemetryPayload> SNAPSHOTS = new ConcurrentHashMap<>();

    private FlightComputerTelemetryClient() { }

    public static void accept(FlightComputerNetwork.TelemetryPayload payload) {
        if (payload == null) return;
        SNAPSHOTS.put(payload.controllerId(), payload);
        FlightContactRegistry.upsert(new FlightContact(
                payload.controllerId(), "", "", "",
                payload.x(), payload.y(), payload.z(), payload.speed(), payload.heading(),
                payload.pitch(), payload.roll(), "ACTIVE", System.currentTimeMillis() / 50L));
    }

    public static FlightComputerNetwork.TelemetryPayload get(UUID id) {
        return id == null ? null : SNAPSHOTS.get(id);
    }
}
