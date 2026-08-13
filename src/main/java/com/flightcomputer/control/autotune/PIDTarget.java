package com.flightcomputer.control.autotune;

/** Minimal target surface for future relay/step-response tuning. */
public interface PIDTarget {
    double getCurrentError();
    double getCurrentRate();
    void applyControl(double controlSignal);
}
