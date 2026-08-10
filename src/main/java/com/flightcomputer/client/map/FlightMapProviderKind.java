package com.flightcomputer.client.map;

/**
 * Provider identity exposed to the Flight Computer UI.
 *
 * There are deliberately no external map-mod provider identities here. The
 * JourneyMap-inspired implementation is owned by Flight Computer itself.
 */
public enum FlightMapProviderKind {
    NATIVE_JOURNEYMAP_INSPIRED,
    NONE
}
