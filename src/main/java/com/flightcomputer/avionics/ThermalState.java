package com.flightcomputer.avionics;

/** Thermal condition derived from one controller's own temperature. */
public enum ThermalState {
    NORMAL,
    WARM,
    OVERHEAT_WARNING,
    THERMAL_SHUTDOWN
}
