package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Small render-side cache of data consumed from Xaero's native map state.
 * Xaero remains the source of truth: this class does not read/write Xaero files,
 * scan Minecraft chunks, or maintain a persistent terrain database.
 */
public final class TerrainMapCache {
    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final Set<Long> REQUESTED = new HashSet<>();

    private static final XaeroMapDataProvider XAERO_PROVIDER = new XaeroMapDataProvider();
    private static final XaeroWaypointProvider XAERO_WAYPOINT_PROVIDER = new XaeroWaypointProvider();
    private static final WaystoneMapProvider WAYSTONE_PROVIDER = new WaystoneMapProvider();

    /* Never allow the UI to enqueue an effectively unbounded map area. */
    private static final int MAX_REQUESTED_CHUNKS = 4096;

    private static String activeIdentity;

    private TerrainMapCache() {}

    /** Legacy lookup retained for callers outside the renderer. */
    public static int colorAt(ClientLevel level, int worldX, int worldZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        int[] grid = CACHE.get(key);
        if (grid == null) {
            requestChunk(level, key);
            return 0;
        }
        return grid[Math.floorMod(worldZ, 16) * 16 + Math.floorMod(worldX, 16)];
    }

    /** Fast render-only lookup. Never touches disk, chunks, or the Xaero decoder. */
    public static int cachedColorAt(ClientLevel level, int worldX, int worldZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        int[] grid = CACHE.get(key);
        if (grid == null) return 0;
        return grid[Math.floorMod(worldZ, 16) * 16 + Math.floorMod(worldX, 16)];
    }

    /**
     * Compatibility overload for existing callers. The renderer-aware overload below
     * is preferred because it requests only the world positions the map actually samples.
     */
    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ, int radiusBlocks) {
        requestViewport(level, centerWorldX, centerWorldZ, radiusBlocks, 16);
    }

    /**
     * Requests terrain from the centre outward using the renderer's world-space
     * sampling interval. This prevents a high-zoom radius from consuming the queue
     * with the north-west corner while the actual player/controller area stays empty.
     */
    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ,
                                       int radiusBlocks, int sampleStepBlocks) {
        ensureLevel(level);
        if (level == null || REQUESTED.size() >= MAX_REQUESTED_CHUNKS) return;

        int radius = Math.max(16, radiusBlocks);
        int step = Math.max(1, sampleStepBlocks);

        // Always request the exact centre first so the first useful Xaero result is
        // the terrain around the player/controller rather than a distant corner.
        requestChunk(level, ChunkPos.asLong(
                Math.floorDiv(centerWorldX, 16),
                Math.floorDiv(centerWorldZ, 16)));

        // Expand in square rings at the same world-space interval used by the renderer.
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

    private static void requestSampleLine(ClientLevel level, int x1, int z1, int x2, int z2, int step) {
        int dx = Integer.compare(x2, x1);
        int dz = Integer.compare(z2, z1);
        int length = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));

        for (int offset = 0; offset <= length && REQUESTED.size() < MAX_REQUESTED_CHUNKS; offset += step) {
            int x = x1 + dx * offset;
            int z = z1 + dz * offset;
            requestChunk(level, ChunkPos.asLong(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        }

        if (REQUESTED.size() < MAX_REQUESTED_CHUNKS) {
            requestChunk(level, ChunkPos.asLong(Math.floorDiv(x2, 16), Math.floorDiv(z2, 16)));
        }
    }

    public static void tick(ClientLevel level) {
        ensureLevel(level);
        XAERO_PROVIDER.tick(level);

        for (Map.Entry<Long, int[]> entry : XAERO_PROVIDER.drainDecodedTiles().entrySet()) {
            long key = entry.getKey();
            CACHE.put(key, entry.getValue());
            REQUESTED.remove(key);
        }

        // Marker layers remain independent from terrain and are updated on the same client tick.
        XAERO_WAYPOINT_PROVIDER.tick(level);
        WAYSTONE_PROVIDER.tick(level);
    }

    public static String xaeroDiagnostics() {
        return XAERO_PROVIDER.diagnostics();
    }

    private static void requestChunk(ClientLevel level, long key) {
        if (CACHE.containsKey(key) || REQUESTED.contains(key)) return;
        if (REQUESTED.size() >= MAX_REQUESTED_CHUNKS) return;

        REQUESTED.add(key);
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        int[] xaeroTile = XAERO_PROVIDER.getChunkTile(level, chunkX, chunkZ);
        if (xaeroTile != null) {
            CACHE.put(key, xaeroTile);
            REQUESTED.remove(key);
        }
    }

    private static void ensureLevel(ClientLevel level) {
        if (level == null) return;
        String identity = buildIdentity(level);
        if (identity.equals(activeIdentity)) return;

        CACHE.clear();
        REQUESTED.clear();
        XAERO_PROVIDER.clear();
        XAERO_WAYPOINT_PROVIDER.clear();
        WAYSTONE_PROVIDER.clear();
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

    public static void clear() {
        CACHE.clear();
        REQUESTED.clear();
        XAERO_PROVIDER.clear();
        XAERO_WAYPOINT_PROVIDER.clear();
        WAYSTONE_PROVIDER.clear();
        activeIdentity = null;
    }
}
