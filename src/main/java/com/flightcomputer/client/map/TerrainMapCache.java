package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side, per-chunk terrain color cache for the Flight Map's terrain layer.
 * This mirrors the approach minimap mods (and vanilla's own map item) use: each
 * chunk's 16x16 surface color grid is computed once and cached, rather than
 * re-scanning blocks every frame. Newly-visible chunks are queued and processed
 * a few at a time per tick, so opening the map in a fresh area doesn't cause a
 * hitch from suddenly sampling dozens of chunks at once.
 *
 * Static/singleton by design - there's exactly one client level at a time, and
 * this cache should persist for as long as the game session does (chunks that
 * scroll out of view stay cached in case the player scrolls back).
 */
public final class TerrainMapCache {

    private static final int CHUNKS_PER_TICK = 6;

    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final Deque<Long> QUEUE = new ArrayDeque<>();

    private TerrainMapCache() {}

    /**
     * ARGB color for one world block column. Returns 0 (fully transparent) if
     * that column's chunk isn't cached yet - the caller should treat 0 as
     * "unknown/not loaded" and skip drawing it, or draw a placeholder.
     */
    public static int colorAt(ClientLevel level, int worldX, int worldZ) {
        long key = ChunkPos.asLong(worldX >> 4, worldZ >> 4);
        int[] grid = CACHE.get(key);
        if (grid == null) {
            enqueue(level, key);
            return 0;
        }
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        return grid[localZ * 16 + localX];
    }

    private static void enqueue(ClientLevel level, long key) {
        if (QUEUED.contains(key)) {
            return;
        }
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        if (!level.hasChunk(chunkX, chunkZ)) {
            return; // not loaded yet - try again next time colorAt() is called for it
        }
        QUEUED.add(key);
        QUEUE.addLast(key);
    }

    /** Call once per client tick while the map screen is open. */
    public static void tick(ClientLevel level) {
        int processed = 0;
        while (processed < CHUNKS_PER_TICK && !QUEUE.isEmpty()) {
            long key = QUEUE.pollFirst();
            QUEUED.remove(key);
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (level.hasChunk(chunkX, chunkZ)) {
                CACHE.put(key, computeChunk(level, chunkX, chunkZ));
                processed++;
            }
        }
    }

    private static int[] computeChunk(ClientLevel level, int chunkX, int chunkZ) {
        int[] grid = new int[256];
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                y = Math.max(level.getMinBuildHeight(), y);
                pos.set(wx, y, wz);
                BlockState state = level.getBlockState(pos);
                MapColor mapColor = state.getMapColor(level, pos);
                grid[lz * 16 + lx] = 0xFF000000 | (mapColor.col & 0xFFFFFF);
            }
        }
        return grid;
    }

    /** Drops cached chunks that are no longer loaded, so re-entering an area re-samples it. */
    public static void invalidateUnloaded(ClientLevel level) {
        CACHE.keySet().removeIf(key -> !level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
    }

    public static void clear() {
        CACHE.clear();
        QUEUE.clear();
        QUEUED.clear();
    }
}
