package com.flightcomputer.client.map;

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

    /** Prepares visible map data without doing work from render(). */
    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ, int radiusBlocks) {
        ensureLevel(level);
        int minChunkX = Math.floorDiv(centerWorldX - radiusBlocks, 16);
        int maxChunkX = Math.floorDiv(centerWorldX + radiusBlocks, 16);
        int minChunkZ = Math.floorDiv(centerWorldZ - radiusBlocks, 16);
        int maxChunkZ = Math.floorDiv(centerWorldZ + radiusBlocks, 16);

        requestChunk(level, ChunkPos.asLong(Math.floorDiv(centerWorldX, 16), Math.floorDiv(centerWorldZ, 16)));

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (!CACHE.containsKey(key)) requestChunk(level, key);
            }
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
        return level.dimension().location().toString();
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
