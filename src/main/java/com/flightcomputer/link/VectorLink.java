package com.flightcomputer.link;

import net.minecraft.core.BlockPos;

public record VectorLink(String vector, BlockPos target, String mode) {
    public static final String WIRING = "wiring";
    public static final String RECEIVING = "receiving";
}
