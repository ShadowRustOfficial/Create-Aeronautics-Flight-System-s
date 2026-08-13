package com.flightcomputer.control.autotune;

import org.joml.Vector3d;

/** Lightweight diagonal inertia view used by the PID auto-tuner. */
public record InertiaTensor(double pitch, double roll, double yaw) {
    public InertiaTensor {
        pitch = Math.max(1.0e-3, Math.abs(pitch));
        roll = Math.max(1.0e-3, Math.abs(roll));
        yaw = Math.max(1.0e-3, Math.abs(yaw));
    }

    public Vector3d asVector() { return new Vector3d(pitch, roll, yaw); }
}
