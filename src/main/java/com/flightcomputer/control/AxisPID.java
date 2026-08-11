package com.flightcomputer.control;

/** PID in acceleration/angular-acceleration domain; live mass/inertia is applied each tick. */
public final class AxisPID {
    private final PIDController pid;

    public AxisPID(double kp, double ki, double kd, double maxAccelCorrection) {
        pid = new PIDController(kp, ki, kd)
                .withOutputClamp(-maxAccelCorrection, maxAccelCorrection)
                .withIntegralClamp(-maxAccelCorrection * 2, maxAccelCorrection * 2)
                .withDerivativeFilter(0.25);
    }

    public double update(double error, double measurement, double dt, double scale) {
        double accelCommand = pid.update(error, measurement, dt);
        return accelCommand * Math.max(scale, 1.0e-3);
    }

    public void reset() { pid.reset(); }
}
