package com.flightcomputer.client.map;

/** Provider identity used by the Flight Computer without exposing provider-specific APIs to the UI. */
public enum FlightMapProviderKind {
    NATIVE,
    XAERO,
    JOURNEYMAP,
    VOXELMAP,
    NONE
}
