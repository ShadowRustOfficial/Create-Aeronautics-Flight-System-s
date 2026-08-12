package com.flightcomputer.control;

import java.util.EnumMap;
import java.util.Map;

/** One independent 6-vector stabilizer. A separate instance is used for STABILIZE and CRUISE. */
public final class SixAxisStabilizer {
    // Roll/pitch are deliberately damped more conservatively than the prior tuning. Physical
    // angular-rate feedback is already available from the Sable vehicle state, so the attitude
    // loops should not fight that damping with excessive integral/output authority.
    private final AxisPID pitchPID = new AxisPID(5.5, 0.15, 3.0, 22.0);
    private final AxisPID rollPID = new AxisPID(5.5, 0.15, 3.0, 22.0);
    private final AxisPID yawPID = new AxisPID(4.5, 0.2, 2.0, 18.0);
    private final AxisPID verticalPID = new AxisPID(3.0, 0.6, 1.2, 40.0);
    private final AxisPID longitudinalPID = new AxisPID(2.0, 0.2, 0.8, 40.0);
    private final AxisPID lateralPID = new AxisPID(2.0, 0.2, 0.8, 40.0);

    /** Create Aeronautics/Sable gravity is 11 m/s² in the mod's kpg/pN physics units. */
    public double gravity = 11.0;

    public Map<ControlAxis, Double> computeCommands(VehicleState state, StabilizationSetpoint sp, double dt) {
        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        double pitchError = wrapAngle(sp.desiredPitch - state.pitch);
        double rollError = wrapAngle(sp.desiredRoll - state.roll);
        double yawError = sp.yawIsRateNotHeading
                ? sp.desiredYawRate - state.yawRate
                : wrapAngle(sp.desiredYaw - state.yaw);

        // Use the physical angular-rate measurements as explicit damping feedback. This keeps
        // forward-flight disturbances from turning into an increasingly amplified roll/pitch hunt.
        double pitchRateDamping = -3.5 * state.pitchRate * Math.max(state.inertiaPitch, 1.0e-3);
        double rollRateDamping = -3.5 * state.rollRate * Math.max(state.inertiaRoll, 1.0e-3);

        out.put(ControlAxis.PITCH, pitchPID.update(pitchError, state.pitch, dt, state.inertiaPitch) + pitchRateDamping);
        out.put(ControlAxis.ROLL, rollPID.update(rollError, state.roll, dt, state.inertiaRoll) + rollRateDamping);
        // Rate-command yaw must differentiate measured yaw rate, not absolute yaw. Using yaw
        // here produces a false derivative term and can leave yaw thrusters active at rest.
        out.put(ControlAxis.YAW, yawPID.update(yawError,
                sp.yawIsRateNotHeading ? state.yawRate : state.yaw, dt, state.inertiaYaw));

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
