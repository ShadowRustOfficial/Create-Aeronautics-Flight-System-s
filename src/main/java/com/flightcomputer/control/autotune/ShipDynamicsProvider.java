package com.flightcomputer.control.autotune;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Stable integration surface between the Flight Computer runtime and PID auto-tuning. */
public interface ShipDynamicsProvider {
    double getMass();
    Vec3 getCenterOfMass();
    Vec3 getMaxLinearAcceleration();
    Vec3 getMaxAngularAcceleration();
    InertiaTensor getInertiaTensor();
    List<Thruster> getThrusters();
    Vec3 getVelocity();
    Vec3 getAngularVelocity();
    Vec3 getOrientationError();
}
