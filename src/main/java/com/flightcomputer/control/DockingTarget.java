package com.flightcomputer.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Base contract for a landing-pad docking connector. Implementations may wrap Create/Aeronautics blocks. */
public record DockingTarget(BlockPos position, Direction approachDirection, double alignmentTolerance, double captureDistance) {
    public DockingTarget {
        if (position == null) throw new IllegalArgumentException("position");
        if (approachDirection == null) approachDirection = Direction.DOWN;
        alignmentTolerance = Math.max(0.0D, alignmentTolerance);
        captureDistance = Math.max(0.1D, captureDistance);
    }
}
