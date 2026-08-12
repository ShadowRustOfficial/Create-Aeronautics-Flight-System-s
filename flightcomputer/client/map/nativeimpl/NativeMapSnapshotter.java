package com.flightcomputer.client.map.nativeimpl;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Client-thread-only boundary between Minecraft world state and worker-safe map data. */
public final class NativeMapSnapshotter {
    private NativeMapSnapshotter() {}

    public static NativeChunkSnapshot snapshot(ClientLevel level, NativeMapTileKey key, long revision) {
        if (level == null) return null;

        int[] heights = new int[NativeChunkSnapshot.SIDE * NativeChunkSnapshot.SIDE];
        int baseX = key.chunkX() << 4;
        int baseZ = key.chunkZ() << 4;

        for (int z = 0; z < NativeChunkSnapshot.SIDE; z++) {
            for (int x = 0; x < NativeChunkSnapshot.SIDE; x++) {
                heights[z * NativeChunkSnapshot.SIDE + x] = level.getHeight(
                        Heightmap.Types.WORLD_SURFACE,
                        baseX + x,
                        baseZ + z);
            }
        }
        return new NativeChunkSnapshot(key, heights, revision);
    }
}
