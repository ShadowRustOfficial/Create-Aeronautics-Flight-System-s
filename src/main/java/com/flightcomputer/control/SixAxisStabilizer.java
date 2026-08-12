package com.flightcomputer.control;

import java.util.EnumMap;
import java.util.Map;

/** One independent 6-vector stabilizer. A separate instance is used for STABILIZE and CRUISE. */
public final class SixAxisStabilizer {
    // Conservative attitude tuning: damp roll/pitch without allowing integral buildup to
    // reintroduce the side-to-side hunting seen during forward flight.
    private final AxisPID pitchPID = new AxisPID(3.5, 0.05, 4.0, 16.0);
    private final AxisPID rollPID = new AxisPID(3.5, 0.05, 4.0, 16.0);
    private final AxisPID yawPID = new AxisPID(4.0, 0.1, 2.5, 16.0);
    private final AxisPID verticalPID = new AxisPID(3.0, 0.4, 1.2, 36.0);
    private final AxisPID longitudinalPID = new AxisPID(2.0, 0.15, 0.8, 36.0);
    private final AxisPID lateralPID = new AxisPID(2.0, 0.15, 0.8, 36.0);

    /** Create Aeronautics/Sable gravity is 11 m/s² in the mod's kpg/pN physics units. */
    public double gravity = 11.0;

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = wrapAngle(sp.desiredPitch - state.pitch);
        double rollError = wrapAngle(sp.desiredRoll - state.roll);
        double yawError = sp.yawIsRateNotHeading
                ? sp.desiredYawRate - state.yawRate
                : wrapAngle(sp.desiredYaw - state.yaw);

        // Physical angular-rate feedback provides the main damping term. Keep it bounded so
        // measured Sable rates cannot overpower the attitude controller on high-inertia craft.
        double inertiaPitch = Math.max(state.inertiaPitch, 1.0e-3);
        double inertiaRoll = Math.max(state.inertiaRoll, 1.0e-3);
        double pitchRateDamping = clamp(-3.0 * state.pitchRate * inertiaPitch, -8.0, 8.0);
        double rollRateDamping = clamp(-3.0 * state.rollRate * inertiaRoll, -8.0, 8.0);

        out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, inertiaPitch) + pitchRateDamping);
        out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, inertiaRoll) + rollRateDamping);
        out.put(ControlAxis.YAW, yawPID.update(yawError,
                sp.yawIsRateNotHeading ? state.yawRate : state.yaw, dt, Math.max(state.inertiaYaw, 1.0e-3)));

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
