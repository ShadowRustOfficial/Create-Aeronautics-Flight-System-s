package com.flightcomputer.control;

/** Six independent degrees of freedom used by both control vector sets. */
public enum ControlAxis {
    PITCH, ROLL, YAW, VERTICAL, LONGITUDINAL, LATERAL;

    public boolean isRotational() {
        return this == PITCH || this == ROLL || this == YAW;
    }

    public boolean isTranslational() {
        return !isRotational();
    }
}
