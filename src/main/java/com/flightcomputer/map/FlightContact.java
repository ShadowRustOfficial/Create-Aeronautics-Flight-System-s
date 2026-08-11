package com.flightcomputer.map;

import java.util.UUID;

/** Compact live Flight Computer contact. Detailed identity is intentionally shown only after marker interaction. */
public record FlightContact(
        UUID controllerId,
        String shipName,
        String callsign,
        String owner,
        double x,
        double y,
        double z,
        double velocity,
        double heading,
        double pitch,
        double roll,
        String flightStatus,
        long lastUpdateTick) {

    public boolean isStale(long currentTick) {
        return currentTick - lastUpdateTick > 40;
    }

    public double distanceTo(double ox, double oy, double oz) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public String displayName() {
        if (callsign != null && !callsign.isBlank()) return callsign;
        if (shipName != null && !shipName.isBlank()) return shipName;
        return "FLIGHT CONTACT";
    }
}
