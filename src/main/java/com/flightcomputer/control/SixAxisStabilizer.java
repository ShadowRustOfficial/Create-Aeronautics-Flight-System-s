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
    private double lastVerticalForce;
    private boolean verticalForceInitialized;
    public double gravity = 11.0;

    public SixAxisStabilizer() { this(false); }

    public SixAxisStabilizer(boolean legacyStableProfile) {
        this.legacyStableProfile = legacyStableProfile;
        // Attitude hold is a PD controller in practice. Integral on pitch/roll is intentionally
        // removed because it slowly winds up against asymmetric propulsion and then launches the
        // vessel into the opposite direction. The rate term is the primary damping authority.
        pitchPID = new AxisPID(2.15, 0.0, 4.8, 14.0);
        rollPID = new AxisPID(2.55, 0.0, 5.4, 16.0);
        yawPID = new AxisPID(2.8, 0.06, 3.4, 14.0);
        if (legacyStableProfile) {
            verticalPID = new AxisPID(1.8, 0.0, 0.8, 3.0);
            longitudinalPID = new AxisPID(2.0, 0.2, 0.8, 40.0);
            lateralPID = new AxisPID(2.0, 0.2, 0.8, 40.0);
        } else {
            verticalPID = new AxisPID(3.0, 0.4, 1.2, 36.0);
            longitudinalPID = new AxisPID(2.0, 0.15, 0.8, 36.0);
            lateralPID = new AxisPID(2.0, 0.15, 0.8, 36.0);
        }
    }

    public void applyProfile(TuningResult profile) {
        if (profile == null) return;
        apply(pitchPID, profile.pitch()); apply(rollPID, profile.roll()); apply(yawPID, profile.yaw());
        apply(verticalPID, profile.vertical()); apply(longitudinalPID, profile.longitudinal()); apply(lateralPID, profile.lateral());
    }
    public TuningResult snapshotProfile(long fingerprint, int version) {
        return new TuningResult(g(pitchPID), g(rollPID), g(yawPID), g(verticalPID), g(longitudinalPID), g(lateralPID), fingerprint, version);
    }
    private static void apply(AxisPID axis, TuningResult.Gains gains) { if (gains != null) axis.setGains(gains.p(), gains.i(), gains.d(), gains.maxOutput()); }
    private static TuningResult.Gains g(AxisPID axis) { return new TuningResult.Gains(axis.kp(), axis.ki(), axis.kd(), 0.0D); }

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt) {
        return computeCommands(state, sp, dt, null, FlightMode.STABILIZE);
    }

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt,
                                                    ThrusterRegistry registry, FlightMode mode) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = deadband(wrapAngle(sp.desiredPitch - state.pitch), Math.toRadians(0.35));
        double rollError = deadband(wrapAngle(sp.desiredRoll - state.roll), Math.toRadians(0.35));
        double yawError = sp.yawIsRateNotHeading
                ? sp.desiredYawRate - state.yawRate
                : deadband(wrapAngle(sp.desiredYaw - state.yaw), Math.toRadians(0.25));

        double pitchScale = responseScale(angularAuthority(registry, mode, state, ControlAxis.PITCH), 4.0D);
        double rollScale = responseScale(angularAuthority(registry, mode, state, ControlAxis.ROLL), 4.0D);
        double yawScale = responseScale(angularAuthority(registry, mode, state, ControlAxis.YAW), 3.0D);

        double pitchAcceleration = pitchPID.updateWithMeasurementRate(pitchError, state.pitchRate, dt, pitchScale);
        double rollAcceleration = rollPID.updateWithMeasurementRate(rollError, state.rollRate, dt, rollScale);
        double yawAcceleration = sp.yawIsRateNotHeading
                ? yawPID.updateWithMeasurementRate(yawError, state.yawRate, dt, yawScale)
                : yawPID.update(yawError, state.yaw, dt, yawScale);

        Vector3d requestedTorque = state.bodyTorqueForAngularAcceleration(pitchAcceleration, yawAcceleration, rollAcceleration);
        requestedTorque.sub(state.externalTorqueBody());
        out.put(ControlAxis.PITCH, requestedTorque.x);
        out.put(ControlAxis.YAW, requestedTorque.y);
        out.put(ControlAxis.ROLL, requestedTorque.z);

        double mass = Math.max(state.mass, 1.0e-3D);
        Vector3d externalForce = state.externalForceBody();
        double verticalAcceleration = verticalPID.update(sp.desiredVerticalVelocity - state.vy, state.vy, dt,
                responseScale(linearAuthority(registry, mode, ControlAxis.VERTICAL, mass), 6.0D));
        double[] bodyVel = state.bodyFrameVelocity();
        double longitudinalAcceleration = longitudinalPID.update(sp.desiredLongitudinalVelocity - bodyVel[0], bodyVel[0], dt,
                responseScale(linearAuthority(registry, mode, ControlAxis.LONGITUDINAL, mass), 6.0D));
        double lateralAcceleration = lateralPID.update(sp.desiredLateralVelocity - bodyVel[1], bodyVel[1], dt,
                responseScale(linearAuthority(registry, mode, ControlAxis.LATERAL, mass), 6.0D));

        double requestedVerticalForce = verticalAcceleration * mass - externalForce.y;
        if (legacyStableProfile) {
            double hoverForce = -externalForce.y;
            double correction = verticalAcceleration * mass;
            double correctionLimit = mass * 2.0D;
            correction = clamp(correction, -correctionLimit, correctionLimit);
            requestedVerticalForce = Math.max(0.0D, hoverForce + correction);
            requestedVerticalForce = slewVerticalForce(requestedVerticalForce, mass, dt);
        }
        out.put(ControlAxis.VERTICAL, requestedVerticalForce);
        out.put(ControlAxis.LONGITUDINAL, longitudinalAcceleration * mass - externalForce.z);
        out.put(ControlAxis.LATERAL, lateralAcceleration * mass - externalForce.x);
        return out;
    }

    private double slewVerticalForce(double requested, double mass, double dt) {
        if (!Double.isFinite(requested)) return verticalForceInitialized ? lastVerticalForce : 0.0D;
        if (!verticalForceInitialized) { lastVerticalForce = requested; verticalForceInitialized = true; return requested; }
        double safeDt = clamp(dt, 1.0 / 200.0, 0.5);
        double maxDelta = Math.max(1.0D, mass * 1.25D * safeDt);
        lastVerticalForce += clamp(requested - lastVerticalForce, -maxDelta, maxDelta);
        return lastVerticalForce;
    }

    private double linearAuthority(ThrusterRegistry registry, FlightMode mode, ControlAxis axis, double mass) {
        if (registry == null) return 6.0D;
        return registry.getAxisAuthority(mode, axis) / Math.max(mass, 1.0e-3D);
    }

    private double angularAuthority(ThrusterRegistry registry, FlightMode mode, VehicleState state, ControlAxis axis) {
        if (registry == null) return 4.0D;
        double authority = 0.0D;
        for (ThrusterLink link : registry.getAllLinks(mode)) {
            if (link == null || link.source == null) continue;
            double[] mount = link.source.getMountOffset();
            if (mount == null || mount.length < 3) continue;
            Vector3d force = new Vector3d(link.direction.x(), link.direction.y(), link.direction.z())
                    .mul(Math.max(0.0D, link.source.getAvailableThrust()) * link.polarity);
            Vector3d r = new Vector3d(mount).sub(state.comX, state.comY, state.comZ);
            Vector3d torque = r.cross(force, new Vector3d());
            double axisTorque = switch (axis) {
                case PITCH -> Math.abs(torque.x);
                case YAW -> Math.abs(torque.y);
                case ROLL -> Math.abs(torque.z);
                default -> 0.0D;
            };
            authority += axisTorque;
        }
        double inertia = switch (axis) {
            case PITCH -> Math.max(state.inertiaPitch, 1.0e-3D);
            case YAW -> Math.max(state.inertiaYaw, 1.0e-3D);
            case ROLL -> Math.max(state.inertiaRoll, 1.0e-3D);
            default -> 1.0D;
        };
        return authority / inertia;
    }

    private double responseScale(double authority, double reference) {
        if (!Double.isFinite(authority) || authority <= 0.0D) return legacyStableProfile ? 0.30D : 0.35D;
        double ratio = Math.max(0.0D, authority) / Math.max(reference, 1.0e-3D);
        double maxScale = legacyStableProfile ? 1.0D : 1.25D;
        return clamp(Math.sqrt(ratio), legacyStableProfile ? 0.30D : 0.35D, maxScale);
    }

    public void resetAll() {
        pitchPID.reset(); rollPID.reset(); yawPID.reset(); verticalPID.reset(); longitudinalPID.reset(); lateralPID.reset();
        lastVerticalForce = 0.0D;
        verticalForceInitialized = false;
    }
    private static double wrapAngle(double radians) { double a = radians % (2 * Math.PI); if (a > Math.PI) a -= 2 * Math.PI; if (a < -Math.PI) a += 2 * Math.PI; return a; }
    private static double deadband(double value, double band) {
        if (Math.abs(value) <= band) return 0.0D;
        return value > 0.0D ? value - band : value + band;
    }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
