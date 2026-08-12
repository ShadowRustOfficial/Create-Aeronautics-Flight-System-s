package com.flightcomputer.control;

import net.minecraft.core.BlockPos;

/** A route segment supports static coordinates today and dynamic targets in the next navigation pass. */
public record RouteSegment(String name, BlockPos coordinate, double targetAltitude, double targetSpeed, ArrivalBehaviour arrivalBehaviour) {
    public enum ArrivalBehaviour { HOLD, CONTINUE, LAND, DOCK }

    public RouteSegment {
        name = name == null || name.isBlank() ? "ROUTE SEGMENT" : name.trim();
        targetAltitude = Math.max(-2048.0D, Math.min(4096.0D, targetAltitude));
        targetSpeed = Math.max(0.0D, targetSpeed);
        arrivalBehaviour = arrivalBehaviour == null ? ArrivalBehaviour.CONTINUE : arrivalBehaviour;
    }
}
