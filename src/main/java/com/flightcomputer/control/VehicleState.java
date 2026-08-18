package com.flightcomputer.control;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Authoritative per-sub-level physics snapshot consumed by the control loops. */
public final class VehicleState {
    public double x, y, z;
    public double vx, vy, vz;
    public double ax, ay, az;
    public double pitch, roll, yaw;
    public double pitchRate, rollRate, yawRate;
    public double bodyBackYawOffset;

    public double mass = 1.0;
    public double inertiaPitch = 1.0, inertiaRoll = 1.0, inertiaYaw = 1.0;
    public double comX, comY, comZ;
    public double i00 = 1.0, i01, i02, i10, i11 = 1.0, i12, i20, i21, i22 = 1.0;

    /* Sable physics-panel inputs. */
    public double gravityAcceleration;
    public double dragForceX, dragForceY, dragForceZ;
    public double levitationForceX, levitationForceY, levitationForceZ;
    public double balloonLiftForceX, balloonLiftForceY, balloonLiftForceZ;
    public double propulsionForceX, propulsionForceY, propulsionForceZ;
    public double liftForceX, liftForceY, liftForceZ;
    public double magneticForceX, magneticForceY, magneticForceZ;
    public double recoilForceX, recoilForceY, recoilForceZ;
    public double impactForceX, impactForceY, impactForceZ;

    public double externalForceX, externalForceY, externalForceZ;
    public double externalTorqueX, externalTorqueY, externalTorqueZ;

    public long timestampNanos;
    public double boundingRadius = 2.0;
    public double boundingHalfHeight = 1.5;

    public VehicleState copy() {
        VehicleState s = new VehicleState();
        s.x=x; s.y=y; s.z=z; s.vx=vx; s.vy=vy; s.vz=vz; s.ax=ax; s.ay=ay; s.az=az;
        s.pitch=pitch; s.roll=roll; s.yaw=yaw; s.pitchRate=pitchRate; s.rollRate=rollRate; s.yawRate=yawRate;
        s.bodyBackYawOffset=bodyBackYawOffset;
        s.mass=mass; s.inertiaPitch=inertiaPitch; s.inertiaRoll=inertiaRoll; s.inertiaYaw=inertiaYaw;
        s.comX=comX; s.comY=comY; s.comZ=comZ;
        s.i00=i00; s.i01=i01; s.i02=i02; s.i10=i10; s.i11=i11; s.i12=i12; s.i20=i20; s.i21=i21; s.i22=i22;
        s.gravityAcceleration=gravityAcceleration;
        s.dragForceX=dragForceX; s.dragForceY=dragForceY; s.dragForceZ=dragForceZ;
        s.levitationForceX=levitationForceX; s.levitationForceY=levitationForceY; s.levitationForceZ=levitationForceZ;
        s.balloonLiftForceX=balloonLiftForceX; s.balloonLiftForceY=balloonLiftForceY; s.balloonLiftForceZ=balloonLiftForceZ;
        s.propulsionForceX=propulsionForceX; s.propulsionForceY=propulsionForceY; s.propulsionForceZ=propulsionForceZ;
        s.liftForceX=liftForceX; s.liftForceY=liftForceY; s.liftForceZ=liftForceZ;
        s.magneticForceX=magneticForceX; s.magneticForceY=magneticForceY; s.magneticForceZ=magneticForceZ;
        s.recoilForceX=recoilForceX; s.recoilForceY=recoilForceY; s.recoilForceZ=recoilForceZ;
        s.impactForceX=impactForceX; s.impactForceY=impactForceY; s.impactForceZ=impactForceZ;
        s.externalForceX=externalForceX; s.externalForceY=externalForceY; s.externalForceZ=externalForceZ;
        s.externalTorqueX=externalTorqueX; s.externalTorqueY=externalTorqueY; s.externalTorqueZ=externalTorqueZ;
        s.timestampNanos=timestampNanos; s.boundingRadius=boundingRadius; s.boundingHalfHeight=boundingHalfHeight;
        return s;
    }

    public double bodyBackYaw() {
        double value = yaw + bodyBackYawOffset;
        value %= Math.PI * 2.0D;
        if (value > Math.PI) value -= Math.PI * 2.0D;
        if (value < -Math.PI) value += Math.PI * 2.0D;
        return value;
    }

    public double[] bodyFrameVelocity() {
        double sinY=Math.sin(yaw), cosY=Math.cos(yaw);
        return new double[]{vx*sinY+vz*cosY, vx*cosY-vz*sinY, vy};
    }

    public Vector3d externalForceBody() {
        Vector3d force=new Vector3d(externalForceX,externalForceY,externalForceZ);
        vehicleRotation().conjugate().transform(force); return force;
    }
    public Vector3d externalTorqueBody() {
        Vector3d torque=new Vector3d(externalTorqueX,externalTorqueY,externalTorqueZ);
        vehicleRotation().conjugate().transform(torque); return torque;
    }
    public Matrix3d inertiaTensor() { return new Matrix3d().set(i00,i10,i20,i01,i11,i21,i02,i12,i22); }
    public Vector3d bodyTorqueForAngularAcceleration(double alphaX,double alphaY,double alphaZ) {
        return inertiaTensor().transform(new Vector3d(alphaX,alphaY,alphaZ));
    }

    public Vector3d namedPhysicsForce() {
        return new Vector3d(
                dragForceX+levitationForceX+balloonLiftForceX+propulsionForceX+liftForceX+magneticForceX+recoilForceX+impactForceX,
                dragForceY+levitationForceY+balloonLiftForceY+propulsionForceY+liftForceY+magneticForceY+recoilForceY+impactForceY,
                dragForceZ+levitationForceZ+balloonLiftForceZ+propulsionForceZ+liftForceZ+magneticForceZ+recoilForceZ+impactForceZ);
    }

    private org.joml.Quaterniond vehicleRotation() {
        return new org.joml.Quaterniond().rotationY(yaw).rotateX(pitch).rotateZ(roll);
    }
}
