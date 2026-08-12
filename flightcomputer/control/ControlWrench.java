package com.flightcomputer.control;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;

/** A dependency-free six degree-of-freedom force/torque demand. */
public final class ControlWrench {
    public double forceX;
    public double forceY;
    public double forceZ;
    public double torqueX;
    public double torqueY;
    public double torqueZ;

    public static ControlWrench fromAxes(Map<ControlAxis, Double> axes) {
        ControlWrench wrench = new ControlWrench();
        wrench.torqueX = axes.getOrDefault(ControlAxis.PITCH, 0.0);
        wrench.torqueZ = axes.getOrDefault(ControlAxis.ROLL, 0.0);
        wrench.torqueY = axes.getOrDefault(ControlAxis.YAW, 0.0);
        wrench.forceY = axes.getOrDefault(ControlAxis.VERTICAL, 0.0);
        wrench.forceZ = axes.getOrDefault(ControlAxis.LONGITUDINAL, 0.0);
        wrench.forceX = axes.getOrDefault(ControlAxis.LATERAL, 0.0);
        return wrench;
    }

    /** Converts the controller's body-frame demand into the world frame used by the allocator. */
    public ControlWrench toWorld(Quaterniond vehicleRotation) {
        Vector3d force = vehicleRotation.transform(new Vector3d(forceX, forceY, forceZ));
        Vector3d torque = vehicleRotation.transform(new Vector3d(torqueX, torqueY, torqueZ));
        ControlWrench world = new ControlWrench();
        world.forceX = force.x;
        world.forceY = force.y;
        world.forceZ = force.z;
        world.torqueX = torque.x;
        world.torqueY = torque.y;
        world.torqueZ = torque.z;
        return world;
    }

    public ControlWrench add(ControlWrench other) {
        forceX += other.forceX; forceY += other.forceY; forceZ += other.forceZ;
        torqueX += other.torqueX; torqueY += other.torqueY; torqueZ += other.torqueZ;
        return this;
    }

    public double component(int index) {
        return switch (index) {
            case 0 -> forceX; case 1 -> forceY; case 2 -> forceZ;
            case 3 -> torqueX; case 4 -> torqueY; case 5 -> torqueZ;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public double normSquared() {
        return forceX * forceX + forceY * forceY + forceZ * forceZ
                + torqueX * torqueX + torqueY * torqueY + torqueZ * torqueZ;
    }
}
