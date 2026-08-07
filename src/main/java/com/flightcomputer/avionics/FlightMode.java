package com.flightcomputer.avionics;

/** The controller's high-level operating mode. The ordinal is never persisted. */
public enum FlightMode {
    DISENGAGED,
    MANUAL,
    STABILIZED,
    AUTOPILOT;

    public FlightMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
