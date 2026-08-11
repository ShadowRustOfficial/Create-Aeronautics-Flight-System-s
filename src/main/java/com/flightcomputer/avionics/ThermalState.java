package com.flightcomputer.avionics;

/** Thermal condition derived from one controller's own temperature. */
public enum ThermalState {
    NORMAL,
    WARM,
    HOT,
    CRITICAL,
    THERMAL_SHUTDOWN
}
