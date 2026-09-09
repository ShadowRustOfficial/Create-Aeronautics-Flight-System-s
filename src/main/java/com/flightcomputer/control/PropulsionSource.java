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

    /** Physical force direction in the controller/vehicle frame when the actuator exposes one. */
    default double[] getForceDirection() {
        VectorDirection direction = getDirection();
        return direction == null ? new double[]{0.0D, 0.0D, 0.0D}
                : new double[]{direction.x(), direction.y(), direction.z()};
    }

    /** Apply one final physical command. Negative values are clamped by the actuator. */
    void applyThrust(double signedFraction);
}
