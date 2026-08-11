package com.flightcomputer.control;

import java.util.EnumMap;
import java.util.Map;

/** One independent 6-vector stabilizer. A separate instance is used for STABILIZE and CRUISE. */
public final class SixAxisStabilizer {
    private final AxisPID pitchPID = new AxisPID(6.0, 0.5, 2.5, 20.0);
    private final AxisPID rollPID = new AxisPID(6.0, 0.5, 2.5, 20.0);
    private final AxisPID yawPID = new AxisPID(4.0, 0.2, 1.5, 15.0);
    private final AxisPID verticalPID = new AxisPID(3.0, 0.6, 1.2, 40.0);
    private final AxisPID longitudinalPID = new AxisPID(2.0, 0.2, 0.8, 40.0);
    private final AxisPID lateralPID = new AxisPID(2.0, 0.2, 0.8, 40.0);

    /** Sable/Create Propulsion use SI-like vehicle physics; do not use Minecraft's 32 px/s^2 scale here. */
    public double gravity = 9.81;

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = wrapAngle(sp.desiredPitch - state.pitch);
        double rollError = wrapAngle(sp.desiredRoll - state.roll);
        double yawError;
        double yawMeasurement;
        if (sp.yawIsRateNotHeading) {
            // Rate control must differentiate angular rate, not heading. Using state.yaw here
            // makes the D-term react to heading motion instead of yaw acceleration and can
            // destabilise the controller as soon as the craft starts turning.
            yawError = sp.desiredYawRate - state.yawRate;
            yawMeasurement = state.yawRate;
        } else {
            yawError = wrapAngle(sp.desiredYaw - state.yaw);
            yawMeasurement = state.yaw;
        }

        out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, state.inertiaPitch));
        out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, state.inertiaRoll));
        out.put(ControlAxis.YAW, yawPID.update(yawError, yawMeasurement, dt, state.inertiaYaw));

        double vertError = sp.desiredVerticalVelocity - state.vy;
        double vertForce = verticalPID.update(vertError, state.vy, dt, state.mass) + state.mass * gravity;
        out.put(ControlAxis.VERTICAL, vertForce);

        double[] bodyVel = state.bodyFrameVelocity();
        out.put(ControlAxis.LONGITUDINAL, longitudinalPID.update(sp.desiredLongitudinalVelocity - bodyVel[0], bodyVel[0], dt, state.mass));
        out.put(ControlAxis.LATERAL, lateralPID.update(sp.desiredLateralVelocity - bodyVel[1], bodyVel[1], dt, state.mass));
        return out;
    }

    public void resetAll() {
        pitchPID.reset(); rollPID.reset(); yawPID.reset();
        verticalPID.reset(); longitudinalPID.reset(); lateralPID.reset();
    }

    private static double wrapAngle(double radians) {
        double a = radians % (2 * Math.PI);
        if (a > Math.PI) a -= 2 * Math.PI;
        if (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
