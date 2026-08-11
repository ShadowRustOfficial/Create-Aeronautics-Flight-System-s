package com.flightcomputer.control;

/** Central control arbitration priorities. Higher priority objectives must be able to pre-empt lower ones. */
public enum FlightObjective {
    EMERGENCY_OVERRIDE(100),
    COLLISION_SAFETY(90),
    THERMAL_POWER_PROTECTION(80),
    DEFENSIVE_RETURN(70),
    AUTO_DOCK(60),
    LANDING_ASSIST(50),
    OFFENSIVE_TRACK(40),
    ROUTE_NAVIGATION(30),
    STABILISATION(20),
    MANUAL_ASSIST(10);

    private final int priority;
    FlightObjective(int priority) { this.priority = priority; }
    public int priority() { return priority; }
}
