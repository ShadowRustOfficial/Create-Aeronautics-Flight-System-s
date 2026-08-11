package com.flightcomputer.control;

/** Adapter for one physical thruster/propulsor. */
public interface PropulsionSource {
    String getId();
    PropulsionType getType();
    double getMaxThrust();
    void applyThrust(double signedFraction);
    double[] getMountOffset();
}
