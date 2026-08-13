package com.flightcomputer.control;

/** Defensive PID with derivative filtering, output/integral clamps and anti-windup. */
public final class PIDController {
    private double kp, ki, kd;
    private double outputMin = Double.NEGATIVE_INFINITY, outputMax = Double.POSITIVE_INFINITY;
    private double integralMin = Double.NEGATIVE_INFINITY, integralMax = Double.POSITIVE_INFINITY;
    private double derivativeFilterAlpha = 0.2;
    private double integral, filteredDerivative, lastMeasurement;
    private boolean initialized;

    public PIDController(double kp, double ki, double kd) { this.kp = kp; this.ki = ki; this.kd = kd; }
    public PIDController withOutputClamp(double min, double max) { outputMin = min; outputMax = max; return this; }
    public PIDController withIntegralClamp(double min, double max) { integralMin = min; integralMax = max; return this; }
    public PIDController withDerivativeFilter(double alpha) { derivativeFilterAlpha = clamp(alpha, 0, 1); return this; }

    public void setGains(double kp, double ki, double kd) {
        if (!Double.isFinite(kp) || !Double.isFinite(ki) || !Double.isFinite(kd)) return;
        this.kp = Math.max(0.0D, kp);
        this.ki = Math.max(0.0D, ki);
        this.kd = Math.max(0.0D, kd);
        reset();
    }

    public double kp() { return kp; }
    public double ki() { return ki; }
    public double kd() { return kd; }

    /** Updates the controller using the measured process variable. Derivative is deliberately
     * taken from the measurement so setpoint changes do not create derivative kick. */
    public double update(double error, double measurement, double dt) {
        double safeDt = clamp(dt, 1.0 / 200.0, 0.5);
        if (!initialized) { lastMeasurement = measurement; initialized = true; }
        integral = clamp(integral + error * safeDt, integralMin, integralMax);
        double rawDerivative = -(measurement - lastMeasurement) / safeDt;
        filteredDerivative += derivativeFilterAlpha * (rawDerivative - filteredDerivative);
        lastMeasurement = measurement;
        double output = kp * error + ki * integral + kd * filteredDerivative;
        double clamped = clamp(output, outputMin, outputMax);
        if (ki != 0 && clamped != output) integral = clamp(integral + (clamped - output) / ki, integralMin, integralMax);
        return clamped;
    }

    public void reset() { integral = 0; filteredDerivative = 0; initialized = false; }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
