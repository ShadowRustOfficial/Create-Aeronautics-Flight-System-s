package com.flightcomputer.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * Controller-scoped map coverage. This is deliberately separate from Xaero's global map state.
 * It describes what terrain the Flight Computer is permitted to display for one controller.
 */
public final class FlightMapTracker {
    private final UUID controllerId;
    private final ResourceLocation dimension;
    private final BlockPos controllerPos;
    private final int radiusBlocks;

    public FlightMapTracker(UUID controllerId, ResourceLocation dimension, BlockPos controllerPos, int radiusBlocks) {
        this.controllerId = controllerId;
        this.dimension = dimension;
        this.controllerPos = controllerPos.immutable();
        this.radiusBlocks = Math.max(0, radiusBlocks);
    }

    public UUID controllerId() { return controllerId; }
    public ResourceLocation dimension() { return dimension; }
    public BlockPos controllerPos() { return controllerPos; }
    public int radiusBlocks() { return radiusBlocks; }

    public boolean tracksBlock(int worldX, int worldZ) {
        long dx = (long) worldX - controllerPos.getX();
        long dz = (long) worldZ - controllerPos.getZ();
        return dx * dx + dz * dz <= (long) radiusBlocks * radiusBlocks;
    }

    public boolean tracksChunk(int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        int closestX = Math.max(minX, Math.min(controllerPos.getX(), maxX));
        int closestZ = Math.max(minZ, Math.min(controllerPos.getZ(), maxZ));
        long dx = (long) closestX - controllerPos.getX();
        long dz = (long) closestZ - controllerPos.getZ();
        return dx * dx + dz * dz <= (long) radiusBlocks * radiusBlocks;
    }

    public boolean tracksChunk(long chunkKey) {
        return tracksChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
    }
}
