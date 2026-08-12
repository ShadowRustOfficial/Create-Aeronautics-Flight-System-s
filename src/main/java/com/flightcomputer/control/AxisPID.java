package com.flightcomputer.control;

/** PID in acceleration/angular-acceleration domain; live mass/inertia is applied each tick. */
public final class AxisPID {
    private final PIDController pid;
    private final double correctionDeadband;

    public AxisPID(double kp, double ki, double kd, double maxAccelCorrection) {
        pid = new PIDController(kp, ki, kd)
                .withOutputClamp(-maxAccelCorrection, maxAccelCorrection)
                .withIntegralClamp(-maxAccelCorrection * 2, maxAccelCorrection * 2)
                .withDerivativeFilter(0.25);
        // Ignore tiny controller corrections so a nearly settled vehicle does not continually
        // pulse individual thrusters. Feed-forward (gravity/hover) is applied separately.
        correctionDeadband = Math.max(0.001, maxAccelCorrection * 0.015);
    }

    public double update(double error, double measurement, double dt, double scale) {
        double accelCommand = pid.update(error, measurement, dt);
        if (Math.abs(accelCommand) < correctionDeadband) accelCommand = 0.0D;
        return accelCommand * Math.max(scale, 1.0e-3);
    }

    public void reset() { pid.reset(); }
}
