package com.flightcomputer.client.map;

import com.flightcomputer.map.FlightContact;
import com.flightcomputer.network.FlightControllerContactNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client contact cache. Marker rendering can remain compact while a selected marker exposes details. */
public final class FlightContactRegistry {
    private static final ConcurrentHashMap<UUID, FlightContact> CONTACTS = new ConcurrentHashMap<>();
    private FlightContactRegistry() { }

    public static void accept(FlightContact contact) {
        if (contact != null && contact.controllerId() != null) CONTACTS.put(contact.controllerId(), contact);
    }

    public static void acceptPacket(FlightControllerContactNetwork.ContactPayload payload) {
        if (payload == null || payload.controllerId() == null) return;
        if (!payload.powered() || !payload.visible()) {
            CONTACTS.remove(payload.controllerId());
            return;
        }
        CONTACTS.put(payload.controllerId(), new FlightContact(
                payload.controllerId(),
                payload.subLevelName(),
                payload.flightId(),
                "",
                payload.x(), payload.y(), payload.z(),
                0.0D, 0.0D, 0.0D, 0.0D,
                "POWERED", System.currentTimeMillis() / 50L));
    }

    public static FlightContact get(UUID id) { return id == null ? null : CONTACTS.get(id); }

    public static List<FlightContact> active(long ignoredTick) {
        long tick = System.currentTimeMillis() / 50L;
        List<FlightContact> result = new ArrayList<>();
        CONTACTS.values().removeIf(c -> c.isStale(tick));
        result.addAll(CONTACTS.values());
        result.sort(Comparator.comparing(FlightContact::displayId, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static void remove(UUID id) { if (id != null) CONTACTS.remove(id); }
    public static void clear() { CONTACTS.clear(); }
}
