package com.flightcomputer.map;

public enum MarkerCategory {
    FLIGHT_WAYPOINT("Flight Waypoints", 0x55AAFF),
    XAERO_WAYPOINT("Xaero Waypoints", 0x55AAFF),
    WAYSTONE("Waystones", 0x55FF55),
    CLAIMED_SUBLEVEL("Claimed Sub-Levels", 0xFFAA00),
    LANDING_PAD("Landing Pads", 0x55FF55);

    private final String label;
    private final int color;

    MarkerCategory(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public int getColor() {
        return color;
    }
}