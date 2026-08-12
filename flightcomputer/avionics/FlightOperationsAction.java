package com.flightcomputer.avionics;

import java.util.Optional;

/** Server-authoritative Phase 5.2 operations commands. */
public enum FlightOperationsAction {
    TOGGLE_MAP_CONTACT(0),
    SET_COMBAT_DEFENSIVE(1),
    SET_COMBAT_OFFENSIVE(2),
    TOGGLE_COMBAT_ASSIST(3),
    TOGGLE_LANDING_ASSIST(4),
    TOGGLE_AUTO_DOCKING(5),
    DOCKING_OVERRIDE(6),
    TOGGLE_TERRAIN_SAFETY(7),
    TOGGLE_EMERGENCY_RETURN(8),
    CLEAR_TRACKED_CONTACT(9);

    private final int networkId;

    FlightOperationsAction(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static Optional<FlightOperationsAction> fromNetworkId(int id) {
        for (FlightOperationsAction action : values()) {
            if (action.networkId == id) return Optional.of(action);
        }
        return Optional.empty();
    }
}
