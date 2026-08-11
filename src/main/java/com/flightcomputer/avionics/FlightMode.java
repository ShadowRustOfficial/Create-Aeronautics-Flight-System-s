package com.flightcomputer.avionics;

/** The controller's high-level operating mode. The ordinal is never persisted. */
public enum FlightMode {
    DISENGAGED,
    MANUAL,
    STABILIZED,
    AUTOPILOT;

    /** Cycle only usable operating modes; DISENGAGED is entered by disengaging the controller. */
    public FlightMode next() {
        return switch (this) {
            case DISENGAGED -> MANUAL;
            case MANUAL -> STABILIZED;
            case STABILIZED -> AUTOPILOT;
            case AUTOPILOT -> MANUAL;
        };
    }
}
