package com.flightcomputer.control.autotune;

import com.flightcomputer.control.ControlAxis;
import com.flightcomputer.control.SixAxisStabilizer;
import java.util.UUID;

/**
 * Safe model-based first-stage auto-tuner. It characterizes the real thruster geometry and
 * Sable-reported inertia, then derives bounded gains. The active relay contracts remain available
 * through ShipDynamicsProvider/PIDTarget but are not fired automatically, avoiding an uncontrolled
 * test impulse on a live multiplayer vehicle.
 */
public final class PIDAutoTuner {
    public static final int PROFILE_VERSION = 1;
    private final UUID controllerId;
    private final ShipDynamicsProvider dynamics;
    private TuningState state = TuningState.IDLE;
    private TuningResult result;
    private long ticks;
    private long startFingerprint;

    public PIDAutoTuner(UUID controllerId, ShipDynamicsProvider dynamics) {
        this.controllerId = controllerId;
        this.dynamics = dynamics;
    }

    public void begin() {
        if (dynamics == null) { state = TuningState.FAILED; return; }
        startFingerprint = fingerprint(dynamics);
        result = null;
        ticks = 0;
        state = TuningState.INITIALIZING;
    }

    public void tick() {
        if (state == TuningState.IDLE || state == TuningState.COMPLETE || state == TuningState.FAILED) return;
        ticks++;
        if (state == TuningState.INITIALIZING) { state = TuningState.CHARACTERIZING; return; }
        if (state == TuningState.CHARACTERIZING && ticks >= 4) { state = TuningState.ANALYZING; return; }
        if (state == TuningState.ANALYZING) { result = derive(); state = result == null ? TuningState.FAILED : TuningState.APPLYING; return; }
        if (state == TuningState.APPLYING) {
            if (result != null) PIDAutoTuneStore.put(controllerId, result);
            state = result == null ? TuningState.FAILED : TuningState.COMPLETE;
        }
    }

    public boolean isComplete() { return state == TuningState.COMPLETE || state == TuningState.FAILED; }
    public TuningState state() { return state; }
    public TuningResult result() { return result; }
    public long fingerprint() { return fingerprint(dynamics); }

    private TuningResult derive() {
        double[] angular = toAngular(dynamics.getMaxAngularAcceleration());
        double[] linear = toLinear(dynamics.getMaxLinearAcceleration());
        if (!finite(angular) || !finite(linear)) return null;

        // Target a conservative ~10 degree attitude correction. The derived P is tied to the
        // measured angular authority; D gives approximately 0.9 damping ratio and I is kept low.
        double angle = Math.toRadians(10.0);
        TuningResult.Gains pitch = attitude(angular[0], angle, 30.0);
        TuningResult.Gains roll = attitude(angular[1], angle, 30.0);
        TuningResult.Gains yaw = attitude(angular[2], Math.toRadians(15.0), 18.0);
        TuningResult.Gains vertical = translation(linear[1], 6.0, 40.0, 0.035);
        TuningResult.Gains longitudinal = translation(linear[2], 20.0, 40.0, 0.02);
        TuningResult.Gains lateral = translation(linear[0], 20.0, 40.0, 0.02);
        return new TuningResult(pitch, roll, yaw, vertical, longitudinal, lateral, startFingerprint, PROFILE_VERSION);
    }

    private static TuningResult.Gains attitude(double alpha, double targetAngle, double maxOutput) {
        double capability = Math.max(0.75, Math.abs(alpha));
        double kp = clamp(capability / Math.max(targetAngle, 0.05), 2.5, 12.0);
        double wn = Math.sqrt(kp);
        double kd = clamp(2.0 * 0.9 * wn, 1.0, 6.0);
        double ki = clamp(kp * 0.04, 0.03, 0.5);
        return new TuningResult.Gains(kp, ki, kd, maxOutput);
    }

    private static TuningResult.Gains translation(double acceleration, double speedLimit, double maxOutput, double integralRatio) {
        double authority = Math.max(0.5, Math.abs(acceleration));
        double kp = clamp(authority / Math.max(speedLimit, 1.0), 0.5, 4.0);
        double kd = clamp(kp * 0.4, 0.1, 1.5);
        double ki = clamp(kp * integralRatio, 0.01, 0.25);
        return new TuningResult.Gains(kp, ki, kd, maxOutput);
    }

    private static long fingerprint(ShipDynamicsProvider p) {
        long h = 1469598103934665603L;
        h = mix(h, p.getMass());
        h = mix(h, p.getInertiaTensor().pitch()); h = mix(h, p.getInertiaTensor().roll()); h = mix(h, p.getInertiaTensor().yaw());
        for (Thruster t : p.getThrusters()) {
            h ^= t.id().hashCode(); h *= 1099511628211L;
            h = mix(h, t.direction().x); h = mix(h, t.direction().y); h = mix(h, t.direction().z);
            h = mix(h, t.mountOffset().x); h = mix(h, t.mountOffset().y); h = mix(h, t.mountOffset().z); h = mix(h, t.maxThrust());
        }
        return h;
    }
    private static long mix(long h, double v) { long bits = Double.doubleToLongBits(v); h ^= bits; return h * 1099511628211L; }
    private static double[] toAngular(net.minecraft.world.phys.Vec3 v) { return new double[]{v.x, v.y, v.z}; }
    private static double[] toLinear(net.minecraft.world.phys.Vec3 v) { return new double[]{v.x, v.y, v.z}; }
    private static boolean finite(double[] a) { for (double v : a) if (!Double.isFinite(v)) return false; return true; }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
