package com.flightcomputer.control;

/** PID in acceleration/angular-acceleration domain; live mass/inertia is applied each tick. */
public final class AxisPID {
    private final PIDController pid;
    private final double correctionDeadband;
    private final double maxAccelCorrection;

    public AxisPID(double kp, double ki, double kd, double maxAccelCorrection) {
        this.maxAccelCorrection = Math.max(0.001D, maxAccelCorrection);
        pid = new PIDController(kp, ki, kd)
                .withOutputClamp(-this.maxAccelCorrection, this.maxAccelCorrection)
                .withIntegralClamp(-this.maxAccelCorrection * 2, this.maxAccelCorrection * 2)
                .withDerivativeFilter(0.25);
        correctionDeadband = Math.max(0.001, this.maxAccelCorrection * 0.015);
    }

    public double update(double error, double measurement, double dt, double scale) {
        double accelCommand = pid.update(error, measurement, dt);
        if (Math.abs(accelCommand) < correctionDeadband) accelCommand = 0.0D;
        return accelCommand * Math.max(scale, 1.0e-3);
    }

    /** Uses the authoritative physical process rate for derivative damping. */
    public double updateWithMeasurementRate(double error, double measurementRate, double dt, double scale) {
        double accelCommand = pid.updateWithMeasurementRate(error, measurementRate, dt);
        if (Math.abs(accelCommand) < correctionDeadband) accelCommand = 0.0D;
        return accelCommand * Math.max(scale, 1.0e-3);
    }

    public void setGains(double p, double i, double d, double maxOutput) {
        double limit = Math.max(0.001D, Math.min(maxAccelCorrection, Math.abs(maxOutput)));
        pid.setGains(p, i, d);
        pid.withOutputClamp(-limit, limit)
                .withIntegralClamp(-limit * 2.0D, limit * 2.0D);
    }

    public double kp() { return pid.kp(); }
    public double ki() { return pid.ki(); }
    public double kd() { return pid.kd(); }

    public void reset() { pid.reset(); }
}
