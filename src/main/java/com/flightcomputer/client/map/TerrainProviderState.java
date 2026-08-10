package com.flightcomputer.client.map;

/** Stable operational state exposed by every terrain backend. */
public enum TerrainProviderState {
    OFFLINE,
    INITIALIZING,
    LOADING,
    READY,
    DEGRADED,
    ERROR
}
