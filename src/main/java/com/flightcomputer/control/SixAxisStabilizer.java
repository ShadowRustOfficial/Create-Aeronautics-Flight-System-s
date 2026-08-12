package com.flightcomputer.control;

import java.util.EnumMap;
import java.util.Map;

/** One independent 6-vector stabilizer. A separate instance is used for STABILIZE and CRUISE. */
public final class SixAxisStabilizer {
    private final AxisPID pitchPID;
    private final AxisPID rollPID;
    private final AxisPID yawPID;
    private final AxisPID verticalPID;
    private final AxisPID longitudinalPID;
    private final AxisPID lateralPID;
    private final boolean legacyStableProfile;

    /** Create Aeronautics/Sable gravity is 11 m/s² in the mod's kpg/pN physics units. */
    public double gravity = 11.0;

    /** Default profile is retained for the CRUISE/MPC controller. */
    public SixAxisStabilizer() {
        this(false);
    }

    /**
     * Uses the exact attitude tuning found in the supplied known-good flightcomputer-0.6.8.jar.
     * This profile is intentionally isolated to the manual STABILIZE controller so that restoring
     * the previous roll/pitch behaviour cannot disturb the currently-working Autopilot/MPC loop.
     */
    public SixAxisStabilizer(boolean legacyStableProfile) {
        this.legacyStableProfile = legacyStableProfile;
        if (legacyStableProfile) {
            // Values extracted from flightcomputer-0.6.8.jar:
            // pitch/roll P=8.0 I=0.35 D=4.0 MaxOutput=30.0
            pitchPID = new AxisPID(8.0, 0.35, 4.0, 30.0);
            rollPID = new AxisPID(8.0, 0.35, 4.0, 30.0);
            yawPID = new AxisPID(4.5, 0.2, 2.0, 18.0);
            verticalPID = new AxisPID(3.0, 0.6, 1.2, 40.0);
            longitudinalPID = new AxisPID(2.0, 0.2, 0.8, 40.0);
            lateralPID = new AxisPID(2.0, 0.2, 0.8, 40.0);
        } else {
            pitchPID = new AxisPID(3.5, 0.05, 4.0, 16.0);
            rollPID = new AxisPID(3.5, 0.05, 4.0, 16.0);
            yawPID = new AxisPID(4.0, 0.1, 2.5, 16.0);
            verticalPID = new AxisPID(3.0, 0.4, 1.2, 36.0);
            longitudinalPID = new AxisPID(2.0, 0.15, 0.8, 36.0);
            lateralPID = new AxisPID(2.0, 0.15, 0.8, 36.0);
        }
    }

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = wrapAngle(sp.desiredPitch - state.pitch);
        double rollError = wrapAngle(sp.desiredRoll - state.roll);
        double yawError = sp.yawIsRateNotHeading
                ? sp.desiredYawRate - state.yawRate
                : wrapAngle(sp.desiredYaw - state.yaw);

        if (!legacyStableProfile) {
            // Physical angular-rate feedback is retained for the cruise/MPC controller. The
            // legacy manual stabiliser deliberately omits this extra damping because the supplied
            // known-good jar did not contain it.
            double inertiaPitch = Math.max(state.inertiaPitch, 1.0e-3);
            double inertiaRoll = Math.max(state.inertiaRoll, 1.0e-3);
            double pitchRateDamping = clamp(-3.0 * state.pitchRate * inertiaPitch, -8.0, 8.0);
            double rollRateDamping = clamp(-3.0 * state.rollRate * inertiaRoll, -8.0, 8.0);
            out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, inertiaPitch) + pitchRateDamping);
            out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, inertiaRoll) + rollRateDamping);
        } else {
            out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, state.inertiaPitch));
            out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, state.inertiaRoll));
        }

        out.put(ControlAxis.YAW, yawPID.update(yawError,
                sp.yawIsRateNotHeading ? state.yawRate : state.yaw, dt, state.inertiaYaw));

        double mass = Math.max(state.mass, 1.0e-3);
        double vertError = sp.desiredVerticalVelocity - state.vy;
        double vertForce = verticalPID.update(vertError, state.vy, dt, mass) + mass * gravity;
        out.put(ControlAxis.VERTICAL, vertForce);

        double[] bodyVel = state.bodyFrameVelocity();
        out.put(ControlAxis.LONGITUDINAL,
                longitudinalPID.update(sp.desiredLongitudinalVelocity - bodyVel[0], bodyVel[0], dt, mass));
        out.put(ControlAxis.LATERAL,
                lateralPID.update(sp.desiredLateralVelocity - bodyVel[1], bodyVel[1], dt, mass));
        return out;
    }

    public void resetAll() {
        pitchPID.reset(); rollPID.reset(); yawPID.reset();
        verticalPID.reset(); longitudinalPID.reset(); lateralPID.reset();
    }

    private static double wrapAngle(double radians) {
        double a = radians % (2 * Math.PI);
        if (a > Math.PI) a -= 2 * Math.PI;
        if (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
