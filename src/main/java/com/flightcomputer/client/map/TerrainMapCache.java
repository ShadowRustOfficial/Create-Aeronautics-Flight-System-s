package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Flight Computer-owned compatibility facade over the native terrain provider.
 *
 * No external map-mod integration is performed here. JourneyMap is an architectural
 * reference only; terrain acquisition, caching and rendering remain Flight Computer-owned.
 *
 * Generated tiles remain resident for the lifetime of the active world/dimension.
 * Moving the viewport changes what is requested, not what is retained.
 */
public final class TerrainMapCache {
    /** Never evict a tile during the active world/dimension session. */
    private static final Map<Long, int[]> CACHE = new HashMap<>();
    /** Client chunks that have been observed and are waiting for CPU generation. */
    private static final Set<Long> REQUESTED = new HashSet<>();
    private static final LiveWorldMapProvider PROVIDER = new LiveWorldMapProvider();
    private static String activeIdentity;

    private TerrainMapCache() {}

    public static int colorAt(ClientLevel level, int worldX, int worldZ) {
        ensureLevel(level);
        long key = key(worldX, worldZ);
        int[] grid = CACHE.get(key);
        if (grid == null) {
            requestChunk(level, key);
            return 0;
        }
        return grid[index(worldX, worldZ)];
    }

    public static int cachedColorAt(ClientLevel level, int worldX, int worldZ) {
        ensureLevel(level);
        int[] grid = CACHE.get(key(worldX, worldZ));
        return grid == null ? 0 : grid[index(worldX, worldZ)];
    }

    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ, int radiusBlocks) {
        requestViewport(level, centerWorldX, centerWorldZ, radiusBlocks, 16);
    }

    /**
     * Scans the requested map area but only accepts chunks that are already loaded
     * by the logical client. This never asks Minecraft/server to load a chunk.
     * As the player moves and new client chunks arrive, subsequent scans discover
     * them and add them to the permanent session cache.
     */
    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ,
                                       int radiusBlocks, int sampleStepBlocks) {
        ensureLevel(level);
        if (level == null) return;

        int radius = Math.max(16, radiusBlocks);
        int minChunkX = Math.floorDiv(centerWorldX - radius, 16);
        int maxChunkX = Math.floorDiv(centerWorldX + radius, 16);
        int minChunkZ = Math.floorDiv(centerWorldZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerWorldZ + radius, 16);

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                // Critical: hasChunk is a read-only client-side availability check.
                // Do not replace this with getChunk(...), which could initiate loading.
                if (level.hasChunk(chunkX, chunkZ)) {
                    requestChunk(level, ChunkPos.asLong(chunkX, chunkZ));
                }
            }
        }
    }

    /**
     * Advances requests from the client-thread facade into the native provider.
     * The provider captures Minecraft data here, then performs expensive generation
     * on its bounded CPU worker pool. Completed tiles are read back from its cache.
     */
    public static void tick(ClientLevel level) {
        ensureLevel(level);
        if (level == null) return;
        for (Long key : Set.copyOf(REQUESTED)) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (!level.hasChunk(chunkX, chunkZ)) {
                // A client chunk can unload between scans. Leave it out of the
                // generation queue; a later scan will rediscover it if it returns.
                REQUESTED.remove(key);
                continue;
            }
            PROVIDER.requestChunkTile(level, chunkX, chunkZ);
            int[] tile = PROVIDER.getCachedChunkTile(level, chunkX, chunkZ);
            if (tile != null) {
                CACHE.putIfAbsent(key, tile);
                REQUESTED.remove(key);
            }
        }
    }

    public static String diagnostics() {
        return "Native Flight Computer terrain provider"
                + "\nloaded=" + CACHE.size()
                + "\npending=" + REQUESTED.size()
                + "\nproviderQueued=" + PROVIDER.queuedTiles()
                + "\nproviderCached=" + PROVIDER.cachedTiles();
    }

    public static void clear() {
        CACHE.clear();
        REQUESTED.clear();
        PROVIDER.clear();
        activeIdentity = null;
    }

    private static void requestChunk(ClientLevel level, long key) {
        if (level == null || CACHE.containsKey(key)) return;
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        if (!level.hasChunk(chunkX, chunkZ)) return;
        REQUESTED.add(key);
    }

    private static long key(int worldX, int worldZ) {
        return ChunkPos.asLong(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
    }

    private static int index(int worldX, int worldZ) {
        return Math.floorMod(worldZ, 16) * 16 + Math.floorMod(worldX, 16);
    }

    private static void ensureLevel(ClientLevel level) {
        if (level == null) return;
        String identity = buildIdentity(level);
        if (identity.equals(activeIdentity)) return;
        clear();
        activeIdentity = identity;
    }

    private static String buildIdentity(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        String world = minecraft.getCurrentServer() != null
                ? "server:" + minecraft.getCurrentServer().ip
                : "singleplayer:" + (minecraft.getSingleplayerServer() != null
                ? minecraft.getSingleplayerServer().getWorldData().getLevelName()
                : "unknown");
        return world + "|" + level.dimension().location();
    }
}
