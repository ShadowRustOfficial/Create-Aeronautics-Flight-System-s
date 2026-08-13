package com.flightcomputer.control.autotune;

import org.joml.Vector3d;

/** Immutable physical thruster description exposed to the auto-tuner. */
public record Thruster(String id, Vector3d direction, Vector3d mountOffset, double maxThrust) {
    public Thruster {
        direction = new Vector3d(direction).normalize();
        mountOffset = new Vector3d(mountOffset);
        maxThrust = Math.max(0.0D, maxThrust);
    }
}
