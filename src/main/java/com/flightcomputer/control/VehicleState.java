package com.flightcomputer.control;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Plain, dependency-free snapshot consumed by the control loops. */
public final class VehicleState {
    public double x, y, z;
    public double vx, vy, vz;
    public double ax, ay, az;
    public double pitch, roll, yaw;
    public double pitchRate, rollRate, yawRate;
    /** Yaw offset from the Sable vessel +Z axis to the Flight Computer's BACK direction. */
    public double bodyBackYawOffset;

    public double mass = 1.0;
    public double inertiaPitch = 1.0, inertiaRoll = 1.0, inertiaYaw = 1.0;

    /** Centre of mass relative to the Flight Computer block centre, in the vessel body frame. */
    public double comX, comY, comZ;

    /** Full body-frame inertia tensor. Diagonal values mirror the legacy scalar fields. */
    public double i00 = 1.0, i01, i02, i10, i11 = 1.0, i12, i20, i21, i22 = 1.0;

    /** Estimated non-controller external force acting on the vessel, in world coordinates. */
    public double externalForceX, externalForceY, externalForceZ;

    /** Estimated non-controller external torque acting on the vessel, in world coordinates. */
    public double externalTorqueX, externalTorqueY, externalTorqueZ;

    public long timestampNanos;
    public double boundingRadius = 2.0;
    public double boundingHalfHeight = 1.5;

    public VehicleState copy() {
        VehicleState s = new VehicleState();
        s.x=x; s.y=y; s.z=z;
        s.vx=vx; s.vy=vy; s.vz=vz;
        s.ax=ax; s.ay=ay; s.az=az;
        s.pitch=pitch; s.roll=roll; s.yaw=yaw;
        s.pitchRate=pitchRate; s.rollRate=rollRate; s.yawRate=yawRate;
        s.bodyBackYawOffset=bodyBackYawOffset;
        s.mass=mass; s.inertiaPitch=inertiaPitch; s.inertiaRoll=inertiaRoll; s.inertiaYaw=inertiaYaw;
        s.comX=comX; s.comY=comY; s.comZ=comZ;
        s.i00=i00; s.i01=i01; s.i02=i02; s.i10=i10; s.i11=i11; s.i12=i12; s.i20=i20; s.i21=i21; s.i22=i22;
        s.externalForceX=externalForceX; s.externalForceY=externalForceY; s.externalForceZ=externalForceZ;
        s.externalTorqueX=externalTorqueX; s.externalTorqueY=externalTorqueY; s.externalTorqueZ=externalTorqueZ;
        s.timestampNanos=timestampNanos; s.boundingRadius=boundingRadius; s.boundingHalfHeight=boundingHalfHeight;
        return s;
    }

    /** World yaw of the Flight Computer's physical BACK direction. */
    public double bodyBackYaw() {
        double value = yaw + bodyBackYawOffset;
        value %= Math.PI * 2.0D;
        if (value > Math.PI) value -= Math.PI * 2.0D;
        if (value < -Math.PI) value += Math.PI * 2.0D;
        return value;
    }

    /** Converts world horizontal velocity into the vessel body frame used by the six-axis PID. */
    public double[] bodyFrameVelocity() {
        double sinY = Math.sin(yaw);
        double cosY = Math.cos(yaw);
        double longitudinal = vx * sinY + vz * cosY;
        double lateral = vx * cosY - vz * sinY;
        return new double[]{longitudinal, lateral, vy};
    }

    /** Converts the estimated world external force into the controller's body frame. */
    public Vector3d externalForceBody() {
        Vector3d force = new Vector3d(externalForceX, externalForceY, externalForceZ);
        vehicleRotation().conjugate().transform(force);
        return force;
    }

    /** Converts the estimated world external torque into the controller's body frame. */
    public Vector3d externalTorqueBody() {
        Vector3d torque = new Vector3d(externalTorqueX, externalTorqueY, externalTorqueZ);
        vehicleRotation().conjugate().transform(torque);
        return torque;
    }

    /** Returns the physical body inertia tensor used for angular acceleration -> torque conversion. */
    public Matrix3d inertiaTensor() {
        return new Matrix3d().set(
                i00, i10, i20,
                i01, i11, i21,
                i02, i12, i22);
    }

    /** Converts requested body angular acceleration into the torque needed for the live inertia tensor. */
    public Vector3d bodyTorqueForAngularAcceleration(double alphaX, double alphaY, double alphaZ) {
        Vector3d alpha = new Vector3d(alphaX, alphaY, alphaZ);
        return inertiaTensor().transform(alpha);
    }

    private org.joml.Quaterniond vehicleRotation() {
        return new org.joml.Quaterniond().rotationY(yaw).rotateX(pitch).rotateZ(roll);
    }
}
