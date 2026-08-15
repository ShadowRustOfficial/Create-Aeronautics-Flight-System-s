package com.flightcomputer.avionics;

import java.util.Optional;

/** Server-authoritative commands accepted by the Flight Controller. */
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
    TOGGLE_TERRAIN(11),
    EMERGENCY_SHUTDOWN(12),
    TOGGLE_AUTOPILOT(13),
    PUSH_FORWARD(14),
    PUSH_BACKWARD(15),
    PUSH_UP(16),
    PUSH_DOWN(17),
    PUSH_LEFT(18),
    PUSH_RIGHT(19);

    private final int networkId;
    FlightControllerAction(int networkId) { this.networkId = networkId; }
    public int networkId() { return networkId; }

    public static Optional<FlightControllerAction> fromNetworkId(int id) {
        for (FlightControllerAction action : values()) if (action.networkId == id) return Optional.of(action);
        return Optional.empty();
    }

    public boolean isIndependentPush() {
        return switch (this) {
            case PUSH_FORWARD, PUSH_BACKWARD, PUSH_UP, PUSH_DOWN, PUSH_LEFT, PUSH_RIGHT -> true;
            default -> false;
        };
    }
}