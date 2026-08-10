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
 * This class deliberately has no integration with Xaero, JourneyMap, or VoxelMap.
 * JourneyMap is an architectural reference only; all acquisition, caching and
 * rendering data is owned by Flight Computer.
 */
public final class TerrainMapCache {
    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final Set<Long> REQUESTED = new HashSet<>();
    private static final LiveWorldMapProvider PROVIDER = new LiveWorldMapProvider();
    private static final int MAX_REQUESTED_CHUNKS = 4096;
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

    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ,
                                       int radiusBlocks, int sampleStepBlocks) {
        ensureLevel(level);
        if (level == null || REQUESTED.size() >= MAX_REQUESTED_CHUNKS) return;
        int radius = Math.max(16, radiusBlocks);
        int step = Math.max(1, sampleStepBlocks);
        requestChunk(level, ChunkPos.asLong(Math.floorDiv(centerWorldX, 16), Math.floorDiv(centerWorldZ, 16)));
        for (int distance = step; distance <= radius && REQUESTED.size() < MAX_REQUESTED_CHUNKS; distance += step) {
            requestSampleLine(level, centerWorldX - distance, centerWorldZ - distance,
                    centerWorldX + distance, centerWorldZ - distance, step);
            requestSampleLine(level, centerWorldX - distance, centerWorldZ + distance,
                    centerWorldX + distance, centerWorldZ + distance, step);
            requestSampleLine(level, centerWorldX - distance, centerWorldZ - distance,
                    centerWorldX - distance, centerWorldZ + distance, step);
            requestSampleLine(level, centerWorldX + distance, centerWorldZ - distance,
                    centerWorldX + distance, centerWorldZ + distance, step);
        }
    }

    public static void tick(ClientLevel level) {
        ensureLevel(level);
        if (level == null) return;
        for (Long key : Set.copyOf(REQUESTED)) {
            int[] tile = PROVIDER.getChunkTile(level, ChunkPos.getX(key), ChunkPos.getZ(key));
            if (tile != null) {
                CACHE.put(key, tile);
                REQUESTED.remove(key);
            }
        }
    }

    public static String diagnostics() {
        return "Native Flight Computer terrain provider"
                + "\nloaded=" + CACHE.size()
                + "\npending=" + REQUESTED.size();
    }

    public static void clear() {
        CACHE.clear();
        REQUESTED.clear();
        PROVIDER.clear();
        activeIdentity = null;
    }

    private static void requestSampleLine(ClientLevel level, int x1, int z1, int x2, int z2, int step) {
        int dx = Integer.compare(x2, x1);
        int dz = Integer.compare(z2, z1);
        int length = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int offset = 0; offset <= length && REQUESTED.size() < MAX_REQUESTED_CHUNKS; offset += step) {
            requestChunk(level, ChunkPos.asLong(Math.floorDiv(x1 + dx * offset, 16), Math.floorDiv(z1 + dz * offset, 16)));
        }
    }

    private static void requestChunk(ClientLevel level, long key) {
        if (CACHE.containsKey(key) || !REQUESTED.add(key)) return;
        if (REQUESTED.size() > MAX_REQUESTED_CHUNKS) REQUESTED.remove(key);
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
