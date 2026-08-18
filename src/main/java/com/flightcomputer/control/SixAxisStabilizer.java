package com.flightcomputer.control;

import com.flightcomputer.control.autotune.TuningResult;
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
    public double gravity = 11.0;

    public SixAxisStabilizer() { this(false); }

    public SixAxisStabilizer(boolean legacyStableProfile) {
        this.legacyStableProfile = legacyStableProfile;
        if (legacyStableProfile) {
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

    public void applyProfile(TuningResult profile) {
        if (profile == null) return;
        apply(pitchPID, profile.pitch());
        apply(rollPID, profile.roll());
        apply(yawPID, profile.yaw());
        apply(verticalPID, profile.vertical());
        apply(longitudinalPID, profile.longitudinal());
        apply(lateralPID, profile.lateral());
    }
    public TuningResult snapshotProfile(long fingerprint, int version) {
        return new TuningResult(g(pitchPID), g(rollPID), g(yawPID), g(verticalPID), g(longitudinalPID), g(lateralPID), fingerprint, version);
    }
    private static void apply(AxisPID axis, TuningResult.Gains gains) { if (gains != null) axis.setGains(gains.p(), gains.i(), gains.d(), gains.maxOutput()); }
    private static TuningResult.Gains g(AxisPID axis) { return new TuningResult.Gains(axis.kp(), axis.ki(), axis.kd(), 0.0D); }

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = wrapAngle(sp.desiredPitch - state.pitch);
        double rollError = wrapAngle(sp.desiredRoll - state.roll);
        double yawError = sp.yawIsRateNotHeading ? sp.desiredYawRate - state.yawRate : wrapAngle(sp.desiredYaw - state.yaw);
        if (!legacyStableProfile) {
            double inertiaPitch = Math.max(state.inertiaPitch, 1.0e-3), inertiaRoll = Math.max(state.inertiaRoll, 1.0e-3);
            double pitchRateDamping = clamp(-3.0 * state.pitchRate * inertiaPitch, -8.0, 8.0);
            double rollRateDamping = clamp(-3.0 * state.rollRate * inertiaRoll, -8.0, 8.0);
            out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, inertiaPitch) + pitchRateDamping);
            out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, inertiaRoll) + rollRateDamping);
        } else {
            out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, Math.max(state.inertiaPitch, 1.0e-3)));
            out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, Math.max(state.inertiaRoll, 1.0e-3)));
        }
        out.put(ControlAxis.YAW, yawPID.update(yawError, sp.yawIsRateNotHeading ? state.yawRate : state.yaw, dt, Math.max(state.inertiaYaw, 1.0e-3)));
        double mass = Math.max(state.mass, 1.0e-3);
        out.put(ControlAxis.VERTICAL, verticalPID.update(sp.desiredVerticalVelocity - state.vy, state.vy, dt, mass) + mass * gravity);
        double[] bodyVel = state.bodyFrameVelocity();
        out.put(ControlAxis.LONGITUDINAL, longitudinalPID.update(sp.desiredLongitudinalVelocity - bodyVel[0], bodyVel[0], dt, mass));
        out.put(ControlAxis.LATERAL, lateralPID.update(sp.desiredLateralVelocity - bodyVel[1], bodyVel[1], dt, mass));
        return out;
    }

    /** Compatibility overload for the newer registry-aware call site; the known-good controller math is unchanged. */
    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt,
                                                    ThrusterRegistry ignoredRegistry, FlightMode ignoredMode) {
        return computeCommands(state, sp, dt);
    }

    public void resetAll() { pitchPID.reset(); rollPID.reset(); yawPID.reset(); verticalPID.reset(); longitudinalPID.reset(); lateralPID.reset(); }
    private static double wrapAngle(double radians) { double a = radians % (2 * Math.PI); if (a > Math.PI) a -= 2 * Math.PI; if (a < -Math.PI) a += 2 * Math.PI; return a; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
