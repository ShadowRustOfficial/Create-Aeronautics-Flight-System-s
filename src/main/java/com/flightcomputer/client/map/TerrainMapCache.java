package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Normalized client-side map cache. Xaero's explored map data is the preferred source.
 * Rendering never performs disk reads or queues work; map preparation happens from tick().
 * The Flight Controller map intentionally does not fall back to loading/sampling live chunks.
 */
public final class TerrainMapCache {
    private static final int FORMAT_VERSION = 2;
    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final Set<Long> REQUESTED = new HashSet<>();

    private static final XaeroMapDataProvider XAERO_PROVIDER = new XaeroMapDataProvider();

    private static String activeIdentity;
    private static Path activeCacheDirectory;

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
    }

    private static void requestChunk(ClientLevel level, long key) {
        if (CACHE.containsKey(key) || REQUESTED.contains(key)) return;
        REQUESTED.add(key);
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        int[] xaeroTile = XAERO_PROVIDER.getChunkTile(level, chunkX, chunkZ);
        if (xaeroTile != null) {
            CACHE.put(key, xaeroTile);
            writeTile(key, xaeroTile);
        }
        // If Xaero has no data yet, leave this cell unexplored. Never load a Minecraft chunk.
    }

    private static void ensureLevel(ClientLevel level) {
        String identity = buildIdentity(level);
        if (identity.equals(activeIdentity)) return;

        CACHE.clear();
        REQUESTED.clear();
        XAERO_PROVIDER.clear();
        activeIdentity = identity;
        activeCacheDirectory = cacheDirectory(identity);
        try { Files.createDirectories(activeCacheDirectory); } catch (IOException ignored) { }
    }

    private static String buildIdentity(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        String server = minecraft.getCurrentServer() != null
                ? minecraft.getCurrentServer().ip
                : "singleplayer:" + singleplayerWorldName(minecraft);
        return sanitize(server) + "__" + sanitize(level.dimension().location().toString());
    }

    private static String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private static Path cacheDirectory(String identity) {
        Path root = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("flightcomputer").resolve("map_cache");
        return root.resolve(identity);
    }

    private static Path tilePath(long key) {
        return activeCacheDirectory.resolve("c_" + ChunkPos.getX(key) + "_" + ChunkPos.getZ(key) + ".fct");
    }

    private static void writeTile(long key, int[] grid) {
        if (activeCacheDirectory == null) return;
        Path path = tilePath(key);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(FORMAT_VERSION);
            for (int color : grid) out.writeInt(color);
        } catch (IOException ignored) { return; }
        try {
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            try { Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ignoredAgain) { }
        }
    }

    private static String sanitize(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }

    public static void clear() {
        CACHE.clear();
        REQUESTED.clear();
        XAERO_PROVIDER.clear();
        activeIdentity = null;
        activeCacheDirectory = null;
    }
}
