package com.flightcomputer.avionics;

import java.util.Optional;

/**
 * The only commands accepted by a Flight Controller. Both the panel and the
 * console transmit these values, keeping validation and state transitions on
 * the server in one place.
 */
public enum FlightControllerAction {
    TOGGLE_ENGAGED(0),
    TOGGLE_STABILISER(1),
    CYCLE_MODE(2),
    PULSE_DISPLAY(3),
    TOGGLE_ALTITUDE_HOLD(4),
    TOGGLE_HEADING_HOLD(5),
    TOGGLE_POSITION_HOLD(6),
    TOGGLE_VELOCITY_HOLD(7),
    TOGGLE_NAVIGATION(8),
    START_ROUTE(9),
    ABORT_ROUTE(10),
    TOGGLE_TERRAIN(11);

    private final int networkId;

    FlightControllerAction(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static Optional<FlightControllerAction> fromNetworkId(int id) {
        for (FlightControllerAction action : values()) {
            if (action.networkId == id) return Optional.of(action);
        }
        return Optional.empty();
    }
}
