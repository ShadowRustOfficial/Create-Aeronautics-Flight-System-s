package com.flightcomputer.control;

import com.flightcomputer.control.autotune.TuningResult;
import org.joml.Vector3d;
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
    /** Retained for configuration compatibility; gravity is supplied by the live Sable force observer now. */
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

        // AxisPID returns acceleration/angular-acceleration when called with unit scale. The live
        // inertia tensor is then applied once, avoiding the previous double rate-damping path.
        double pitchAcceleration = pitchPID.update(pitchError, state.pitch, dt, 1.0D);
        double rollAcceleration = rollPID.update(rollError, state.roll, dt, 1.0D);
        double yawAcceleration = yawPID.update(yawError, sp.yawIsRateNotHeading ? state.yawRate : state.yaw, dt, 1.0D);
        Vector3d requestedTorque = state.bodyTorqueForAngularAcceleration(pitchAcceleration, yawAcceleration, rollAcceleration);
        Vector3d externalTorque = state.externalTorqueBody();
        requestedTorque.sub(externalTorque);
        out.put(ControlAxis.PITCH, requestedTorque.x);
        out.put(ControlAxis.YAW, requestedTorque.y);
        out.put(ControlAxis.ROLL, requestedTorque.z);

        double mass = Math.max(state.mass, 1.0e-3);
        Vector3d externalForce = state.externalForceBody();
        double verticalAcceleration = verticalPID.update(sp.desiredVerticalVelocity - state.vy, state.vy, dt, 1.0D);
        double[] bodyVel = state.bodyFrameVelocity();
        double longitudinalAcceleration = longitudinalPID.update(sp.desiredLongitudinalVelocity - bodyVel[0], bodyVel[0], dt, 1.0D);
        double lateralAcceleration = lateralPID.update(sp.desiredLateralVelocity - bodyVel[1], bodyVel[1], dt, 1.0D);

        // Sable already accounts for Gravity, Drag, Lift, Levitation, Balloon Lift, Magnetic and
        // other force groups. The controller therefore asks only for the residual force required
        // to reach the target acceleration instead of adding a second mass*gravity term.
        out.put(ControlAxis.VERTICAL, verticalAcceleration * mass - externalForce.y);
        out.put(ControlAxis.LONGITUDINAL, longitudinalAcceleration * mass - externalForce.z);
        out.put(ControlAxis.LATERAL, lateralAcceleration * mass - externalForce.x);
        return out;
    }
    public void resetAll() { pitchPID.reset(); rollPID.reset(); yawPID.reset(); verticalPID.reset(); longitudinalPID.reset(); lateralPID.reset(); }
    private static double wrapAngle(double radians) { double a = radians % (2 * Math.PI); if (a > Math.PI) a -= 2 * Math.PI; if (a < -Math.PI) a += 2 * Math.PI; return a; }
}
