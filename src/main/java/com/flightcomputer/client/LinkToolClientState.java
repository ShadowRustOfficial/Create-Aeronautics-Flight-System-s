package com.flightcomputer.client;

import com.flightcomputer.control.VectorDirection;
import net.minecraft.core.BlockPos;

/** Client-only focus state for the cable-style Link Tool. */
public final class LinkToolClientState {
    public enum LinkMode { STABILISER, AUTOPILOT }

    private static BlockPos controllerPos;
    private static VectorDirection direction = VectorDirection.NORTH;
    private static LinkMode mode = LinkMode.STABILISER;

    private LinkToolClientState() {}

    public static void selectController(BlockPos pos) {
        controllerPos = pos == null ? null : pos.immutable();
    }

    public static BlockPos controllerPos() { return controllerPos; }
    public static VectorDirection direction() { return direction; }
    public static LinkMode mode() { return mode; }

    public static void scroll(int delta) {
        direction = direction.next(delta);
    }

    public static void toggleMode() {
        mode = mode == LinkMode.STABILISER ? LinkMode.AUTOPILOT : LinkMode.STABILISER;
    }

    public static void clear() {
        controllerPos = null;
        direction = VectorDirection.NORTH;
        mode = LinkMode.STABILISER;
    }
}
