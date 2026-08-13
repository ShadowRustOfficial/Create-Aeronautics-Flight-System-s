package com.flightcomputer.control;

/** Plain, dependency-free snapshot consumed by the control loops. */
public final class VehicleState {
    public double x, y, z;
    public double vx, vy, vz;
    public double pitch, roll, yaw;
    public double pitchRate, rollRate, yawRate;
    public double mass = 1.0;
    public double inertiaPitch = 1.0, inertiaRoll = 1.0, inertiaYaw = 1.0;
    public long timestampNanos;
    public double boundingRadius = 2.0;
    public double boundingHalfHeight = 1.5;

    public VehicleState copy() {
        VehicleState s = new VehicleState();
        s.x=x; s.y=y; s.z=z; s.vx=vx; s.vy=vy; s.vz=vz;
        s.pitch=pitch; s.roll=roll; s.yaw=yaw;
        s.pitchRate=pitchRate; s.rollRate=rollRate; s.yawRate=yawRate;
        s.mass=mass; s.inertiaPitch=inertiaPitch; s.inertiaRoll=inertiaRoll; s.inertiaYaw=inertiaYaw;
        s.timestampNanos=timestampNanos; s.boundingRadius=boundingRadius; s.boundingHalfHeight=boundingHalfHeight;
        return s;
    }

    /**
     * Converts world horizontal velocity into the vessel body frame used by the six-axis PID.
     * The controller's yaw convention defines forward as (sin(yaw), 0, cos(yaw)) and right as
     * (cos(yaw), 0, -sin(yaw)).
     */
    public double[] bodyFrameVelocity() {
        double sinY = Math.sin(yaw);
        double cosY = Math.cos(yaw);
        double longitudinal = vx * sinY + vz * cosY;
        double lateral = vx * cosY - vz * sinY;
        return new double[]{longitudinal, lateral, vy};
    }
}
