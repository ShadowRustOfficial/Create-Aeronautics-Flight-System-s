package com.flightcomputer.control;

public final class StabilizationSetpoint {
    public double desiredPitch = 0;
    public double desiredRoll = 0;
    public double desiredYaw;
    public boolean yawIsRateNotHeading = false;
    public double desiredYawRate = 0;
    public double desiredVerticalVelocity = 0;
    public double desiredLongitudinalVelocity = 0;
    public double desiredLateralVelocity = 0;

    public static StabilizationSetpoint hover() { return new StabilizationSetpoint(); }

    public static StabilizationSetpoint manualNudge(double pitchStick, double rollStick,
            double yawRateStick, double verticalStick, double longitudinalStick, double lateralStick,
            double maxTiltAngle, double maxYawRate, double maxManualSpeed) {
        StabilizationSetpoint sp = new StabilizationSetpoint();
        sp.desiredPitch = clamp(pitchStick, -1, 1) * maxTiltAngle;
        sp.desiredRoll = clamp(rollStick, -1, 1) * maxTiltAngle;
        sp.yawIsRateNotHeading = true;
        sp.desiredYawRate = clamp(yawRateStick, -1, 1) * maxYawRate;
        sp.desiredVerticalVelocity = clamp(verticalStick, -1, 1) * maxManualSpeed;
        sp.desiredLongitudinalVelocity = clamp(longitudinalStick, -1, 1) * maxManualSpeed;
        sp.desiredLateralVelocity = clamp(lateralStick, -1, 1) * maxManualSpeed;
        return sp;
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
