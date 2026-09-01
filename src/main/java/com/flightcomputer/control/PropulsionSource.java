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

    /** Unit force direction in the vehicle/body frame. */
    default double[] getForceDirection() {
        VectorDirection d = getDirection();
        return d == null ? new double[]{0.0D, 0.0D, 0.0D} : new double[]{d.x(), d.y(), d.z()};
    }

    default double[] getReverseForceDirection() {
        double[] f = getForceDirection();
        return new double[]{-f[0], -f[1], -f[2]};
    }
    default double getMinimumThrust() { return 0.0D; }
    default double getHalfAngleDeg() { return 0.0D; }
    default boolean isReversible() { return false; }
    default double getReverseMaxThrust() { return getMaxThrust(); }
    default double getReverseMinimumThrust() { return getMinimumThrust(); }
    default boolean hasInstantThrottleResponse() { return false; }
    default Object getThrottleGroupKey() { return null; }
    default double[] getAppliedForceFeedback() { return null; }

    /** Optional physics-step hook for actuators that own a native Sable/Aeronautics force path. */
    default void applyPhysicsImpulse(Object subLevel, double timeStep) { }

    void applyThrust(double signedFraction);
}
