package com.flightcomputer.avionics;

/** Result returned after a Phase 5.2 operations transition. */
public record FlightOperationsActionResult(
        boolean accepted,
        FlightOperationsState state,
        FlightOperationsAction action,
        String reason
) {
    public static FlightOperationsActionResult accepted(FlightOperationsState state, FlightOperationsAction action) {
        return new FlightOperationsActionResult(true, state, action, "");
    }

    public static FlightOperationsActionResult rejected(FlightOperationsState state, FlightOperationsAction action, String reason) {
        return new FlightOperationsActionResult(false, state, action, reason);
    }
}
