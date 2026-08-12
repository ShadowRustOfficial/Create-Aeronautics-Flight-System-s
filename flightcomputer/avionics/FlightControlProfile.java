package com.flightcomputer.avionics;

/** Non-exclusive control profiles layered over the primary MANUAL/STABILIZED/AUTOPILOT mode. */
public enum FlightControlProfile {
    NORMAL,
    COMBAT,
    LANDING,
    EMERGENCY
}
