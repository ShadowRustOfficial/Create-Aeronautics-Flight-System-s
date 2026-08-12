package com.flightcomputer.client.map;

/** Explicit provider lifecycle so diagnostics can distinguish waiting from a stuck request loop. */
public enum FlightMapProviderState {
    DISCOVERING,
    READY,
    DEGRADED,
    WAITING,
    FAILED
}
