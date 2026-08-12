package com.flightcomputer.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side live contact cache. Rendering code can consume snapshots without owning contact state. */
public final class FlightContactRegistry {
    private static final Map<UUID, FlightContact> CONTACTS = new ConcurrentHashMap<>();

    private FlightContactRegistry() { }

    public static void upsert(FlightContact contact) {
        if (contact != null && contact.controllerId() != null) CONTACTS.put(contact.controllerId(), contact);
    }

    public static FlightContact get(UUID controllerId) {
        return controllerId == null ? null : CONTACTS.get(controllerId);
    }

    public static List<FlightContact> snapshot() {
        return List.copyOf(new ArrayList<>(CONTACTS.values()));
    }

    public static void remove(UUID controllerId) {
        if (controllerId != null) CONTACTS.remove(controllerId);
    }

    public static void clear() { CONTACTS.clear(); }
}
