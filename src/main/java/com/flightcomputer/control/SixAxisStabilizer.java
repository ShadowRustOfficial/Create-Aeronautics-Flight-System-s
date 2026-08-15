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
    /** Retained for configuration compatibility; live Sable forces are used by the controller. */
    public double gravity = 11.0;

    public SixAxisStabilizer() { this(false); }

    public SixAxisStabilizer(boolean legacyStableProfile) {
        this.legacyStableProfile = legacyStableProfile;
        if (legacyStableProfile) {
            // Stabilisation is deliberately less aggressive than the cruise/autopilot profile.
            // Large Sable vessels have substantially larger inertia and actuator time constants;
            // the old gains caused repeated torque saturation and visible hunting.
            pitchPID = new AxisPID(5.0, 0.12, 5.0, 22.0);
            rollPID = new AxisPID(5.0, 0.12, 5.0, 22.0);
            yawPID = new AxisPID(3.0, 0.05, 3.5, 14.0);
            verticalPID = new AxisPID(2.2, 0.20, 1.5, 28.0);
            longitudinalPID = new AxisPID(1.5, 0.10, 1.0, 28.0);
            lateralPID = new AxisPID(1.5, 0.10, 1.0, 28.0);
        } else {
            // Cruise/autopilot gains are intentionally left on the previously working profile.
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
        return computeCommands(state, sp, dt, null, FlightMode.STABILIZE);
    }

    /**
     * Computes acceleration/force commands from the live Sable vessel state.
     *
     * Sable's authoritative velocity/mass/inertia observer already includes passive physics such
     * as Gravity, Levitation, Lift, Balloon Lift, Drag and Magnetic forces in externalForceBody().
     * They are treated as environmental/passive forces, not as fake thruster authority or extra
     * mass compensation. This is important for Levitite-heavy large vessels: the stabiliser only
     * commands the residual force needed after Sable's actual passive forces have acted.
     */
    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt,
                                                    ThrusterRegistry registry, FlightMode mode) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = wrapAngle(sp.desiredPitch - state.pitch);
        double rollError = wrapAngle(sp.desiredRoll - state.roll);
        double yawError = sp.yawIsRateNotHeading ? sp.desiredYawRate - state.yawRate : wrapAngle(sp.desiredYaw - state.yaw);

        double pitchScale = responseScale(angularAuthority(registry, mode, state, ControlAxis.PITCH), 4.0D);
        double rollScale = responseScale(angularAuthority(registry, mode, state, ControlAxis.ROLL), 4.0D);
        double yawScale = responseScale(angularAuthority(registry, mode, state, ControlAxis.YAW), 3.0D);

        double pitchAcceleration = pitchPID.update(pitchError, state.pitch, dt, pitchScale);
        double rollAcceleration = rollPID.update(rollError, state.roll, dt, rollScale);
        double yawAcceleration = yawPID.update(yawError, sp.yawIsRateNotHeading ? state.yawRate : state.yaw, dt, yawScale);
        Vector3d requestedTorque = state.bodyTorqueForAngularAcceleration(pitchAcceleration, yawAcceleration, rollAcceleration);
        Vector3d externalTorque = state.externalTorqueBody();
        requestedTorque.sub(externalTorque);
        out.put(ControlAxis.PITCH, requestedTorque.x);
        out.put(ControlAxis.YAW, requestedTorque.y);
        out.put(ControlAxis.ROLL, requestedTorque.z);

        double mass = Math.max(state.mass, 1.0e-3);
        Vector3d externalForce = state.externalForceBody();
        double verticalAcceleration = verticalPID.update(sp.desiredVerticalVelocity - state.vy, state.vy, dt,
                responseScale(linearAuthority(registry, mode, ControlAxis.VERTICAL, mass), 6.0D));
        double[] bodyVel = state.bodyFrameVelocity();
        double longitudinalAcceleration = longitudinalPID.update(sp.desiredLongitudinalVelocity - bodyVel[0], bodyVel[0], dt,
                responseScale(linearAuthority(registry, mode, ControlAxis.LONGITUDINAL, mass), 6.0D));
        double lateralAcceleration = lateralPID.update(sp.desiredLateralVelocity - bodyVel[1], bodyVel[1], dt,
                responseScale(linearAuthority(registry, mode, ControlAxis.LATERAL, mass), 6.0D));

        // Passive Sable force is deliberately subtracted once. Do not add a second gravity or
        // levitation term here: doing so makes the controller fight Levitite and causes hunting.
        out.put(ControlAxis.VERTICAL, verticalAcceleration * mass - externalForce.y);
        out.put(ControlAxis.LONGITUDINAL, longitudinalAcceleration * mass - externalForce.z);
        out.put(ControlAxis.LATERAL, lateralAcceleration * mass - externalForce.x);
        return out;
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
        // Stabilisation is capped at unity so a large craft cannot turn a high actuator count into
        // an unnecessarily aggressive response. Cruise/autopilot keeps the existing upper margin.
        double maxScale = legacyStableProfile ? 1.0D : 1.25D;
        return clamp(Math.sqrt(ratio), legacyStableProfile ? 0.30D : 0.35D, maxScale);
    }

    public void resetAll() { pitchPID.reset(); rollPID.reset(); yawPID.reset(); verticalPID.reset(); longitudinalPID.reset(); lateralPID.reset(); }
    private static double wrapAngle(double radians) { double a = radians % (2 * Math.PI); if (a > Math.PI) a -= 2 * Math.PI; if (a < -Math.PI) a += 2 * Math.PI; return a; }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
