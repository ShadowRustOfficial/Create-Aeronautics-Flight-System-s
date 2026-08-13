package com.flightcomputer.control.autotune;

/** Explicit calibration lifecycle. Calibration never starts implicitly from vehicle changes. */
public enum CalibrationState {
    IDLE,
    PRECHECK,
    WAIT_STATIONARY,
    WAIT_CLEARANCE,
    WAIT_CONFIRMATION,
    RUNNING,
    VALIDATING,
    APPLYING,
    COMPLETE,
    ABORTED
}
