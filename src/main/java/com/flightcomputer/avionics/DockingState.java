package com.flightcomputer.avionics;

/** Explicit docking state machine. OVERRIDE must immediately return control to the pilot. */
public enum DockingState {
    IDLE,
    SCANNING,
    APPROACHING,
    ALIGNING,
    DOCKING,
    DOCKED,
    OVERRIDDEN,
    LOST_TARGET
}
