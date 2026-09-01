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

    /**
     * Unit force direction in the vehicle/body frame. The default preserves the existing
     * six-cardinal-vector actuator model, while real vectoring thrusters can override it.
     */
    default double[] getForceDirection() {
        VectorDirection d = getDirection();
        return d == null ? new double[]{0.0D, 0.0D, 0.0D} : new double[]{d.x(), d.y(), d.z()};
    }

    /** Optional reverse-force direction for reversible/vectoring actuators. */
    default double[] getReverseForceDirection() {
        double[] f = getForceDirection();
        return new double[]{-f[0], -f[1], -f[2]};
    }

    /** Minimum useful thrust fraction/capability; zero keeps the legacy actuator behaviour. */
    default double getMinimumThrust() { return 0.0D; }

    /** Maximum allowed nozzle half-angle in degrees. Zero means a fixed-direction actuator. */
    default double getHalfAngleDeg() { return 0.0D; }

    default boolean isReversible() { return false; }
    default double getReverseMaxThrust() { return getMaxThrust(); }
    default double getReverseMinimumThrust() { return getMinimumThrust(); }
    default boolean hasInstantThrottleResponse() { return false; }

    /** Optional grouping key for actuators that share one physical resource/controller. */
    default Object getThrottleGroupKey() { return null; }

    /** Actual force currently applied, when the actuator can report it. */
    default double[] getAppliedForceFeedback() { return null; }

    /** Apply one final physical command. Negative values are clamped by the adapter. */
    void applyThrust(double signedFraction);
}
