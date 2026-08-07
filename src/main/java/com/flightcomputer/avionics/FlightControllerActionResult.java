package com.flightcomputer.avionics;

/** Result returned after the authoritative state transition has completed. */
public record FlightControllerActionResult(
        boolean accepted,
        FlightControllerState state,
        FlightControllerAction action,
        String animationKey
) {
    public static FlightControllerActionResult accepted(FlightControllerState state, FlightControllerAction action, String animationKey) {
        return new FlightControllerActionResult(true, state, action, animationKey);
    }
}
