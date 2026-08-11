package com.flightcomputer.client.map;

/** Immutable world-space marker consumed by the Flight Map renderer. */
public record FlightMapMarker(Type type, String label, double worldX, double worldY, double worldZ) {
    public enum Type {
        WAYPOINT,
        WAYSTONE
    }
}
