package com.flightcomputer.control.autotune;

import java.util.UUID;

/**
 * Safe model-based first-stage auto-tuner. It characterizes the real thruster geometry and
 * Sable-reported inertia, then makes bounded adjustments around the proven 0.6.8 manual profile.
 * Relay/step testing contracts are kept separate so no automatic live-ship impulse is applied.
 */
public final class PIDAutoTuner {
    public static final int PROFILE_VERSION = 1;
    private final UUID controllerId;
    private final ShipDynamicsProvider dynamics;
    private TuningState state = TuningState.IDLE;
    private TuningResult result;
    private long ticks;
    private long startFingerprint;

    public PIDAutoTuner(UUID controllerId, ShipDynamicsProvider dynamics) { this.controllerId = controllerId; this.dynamics = dynamics; }
    public void begin() {
        if (dynamics == null) { state = TuningState.FAILED; return; }
        startFingerprint = fingerprint(dynamics); result = null; ticks = 0; state = TuningState.INITIALIZING;
    }
    public void tick() {
        if (state == TuningState.IDLE || state == TuningState.COMPLETE || state == TuningState.FAILED) return;
        ticks++;
        if (state == TuningState.INITIALIZING) { state = TuningState.CHARACTERIZING; return; }
        if (state == TuningState.CHARACTERIZING && ticks >= 4) { state = TuningState.ANALYZING; return; }
        if (state == TuningState.ANALYZING) { result = derive(); state = result == null ? TuningState.FAILED : TuningState.APPLYING; return; }
        if (state == TuningState.APPLYING) { if (result != null) PIDAutoTuneStore.put(controllerId, result); state = result == null ? TuningState.FAILED : TuningState.COMPLETE; }
    }
    public boolean isComplete() { return state == TuningState.COMPLETE || state == TuningState.FAILED; }
    public TuningState state() { return state; }
    public TuningResult result() { return result; }
    public long fingerprint() { return fingerprint(dynamics); }

    private TuningResult derive() {
        double[] angular = xyz(dynamics.getMaxAngularAcceleration());
        double[] linear = xyz(dynamics.getMaxLinearAcceleration());
        if (!finite(angular) || !finite(linear)) return null;

        double pitchScale = boundedScale(angular[0], 8.0 * Math.toRadians(10.0));
        double rollScale = boundedScale(angular[1], 8.0 * Math.toRadians(10.0));
        double yawScale = boundedScale(angular[2], 4.5 * Math.toRadians(15.0));
        TuningResult.Gains pitch = attitude(8.0, 0.35, 4.0, 30.0, pitchScale);
        TuningResult.Gains roll = attitude(8.0, 0.35, 4.0, 30.0, rollScale);
        TuningResult.Gains yaw = attitude(4.5, 0.20, 2.0, 18.0, yawScale);
        TuningResult.Gains vertical = translation(3.0, 0.6, 1.2, 40.0, linear[1], 6.0);
        TuningResult.Gains longitudinal = translation(2.0, 0.2, 0.8, 40.0, linear[2], 20.0);
        TuningResult.Gains lateral = translation(2.0, 0.2, 0.8, 40.0, linear[0], 20.0);
        return new TuningResult(pitch, roll, yaw, vertical, longitudinal, lateral, startFingerprint, PROFILE_VERSION);
    }

    private static TuningResult.Gains attitude(double p, double i, double d, double max, double scale) {
        return new TuningResult.Gains(p * scale, i, d * scale, max);
    }
    private static TuningResult.Gains translation(double p, double i, double d, double max, double acceleration, double expectedSpeed) {
        double scale = boundedScale(acceleration, Math.max(0.5, expectedSpeed * p));
        return new TuningResult.Gains(p * scale, i, d * scale, max);
    }
    private static double boundedScale(double capability, double baseline) {
        return clamp(
                Math.sqrt(Math.max(0.05, Math.abs(capability) / Math.max(0.05, Math.abs(baseline)))),
                0.75,
                1.25
        );
    }

    private static long fingerprint(ShipDynamicsProvider p) {
        long h = 1469598103934665603L;
        h = mix(h, p.getMass()); h = mix(h, p.getInertiaTensor().pitch()); h = mix(h, p.getInertiaTensor().roll()); h = mix(h, p.getInertiaTensor().yaw());
        for (Thruster t : p.getThrusters()) {
            h ^= t.id().hashCode(); h *= 1099511628211L;
            h = mix(h, t.direction().x); h = mix(h, t.direction().y); h = mix(h, t.direction().z);
            h = mix(h, t.mountOffset().x); h = mix(h, t.mountOffset().y); h = mix(h, t.mountOffset().z); h = mix(h, t.maxThrust());
        }
        return h;
    }
    private static long mix(long h, double v) { h ^= Double.doubleToLongBits(v); return h * 1099511628211L; }
    private static double[] xyz(net.minecraft.world.phys.Vec3 v) { return new double[]{v.x, v.y, v.z}; }
    private static boolean finite(double[] a) { for (double v : a) if (!Double.isFinite(v)) return false; return true; }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}