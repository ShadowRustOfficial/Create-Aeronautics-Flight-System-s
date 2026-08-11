package com.flightcomputer.control;

/** Capability boundary for one physical propulsion block. */
public interface PropulsionSource {
    String getId();
    PropulsionType getType();
    VectorDirection getDirection();
    double getMaxThrust();
    double getAvailableThrust();
    double getCurrentThrust();
    boolean isEnabled();
    boolean isOperational();
    boolean hasPower();
    double[] getMountOffset();

    /** Apply one final physical command. Negative values are clamped by the adapter. */
    void applyThrust(double signedFraction);
}
