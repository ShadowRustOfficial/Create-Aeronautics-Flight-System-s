package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Normalized client-side map cache. It prefers persisted Flight Computer tiles,
 * then Xaero's explored map data. Rendering never performs disk reads or queues work;
 * map preparation happens from tick(). The Flight Controller map intentionally does not
 * fall back to loading/sampling live Minecraft chunks.
 */
public final class TerrainMapCache {
    private static final int CHUNKS_PER_TICK = 12;
    private static final int FORMAT_VERSION = 2;
    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final Set<Long> DISK_CHECKED = new HashSet<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final Deque<Long> QUEUE = new ArrayDeque<>();

    private static final XaeroMapDataProvider XAERO_PROVIDER = new XaeroMapDataProvider();
    private static final LiveWorldMapProvider LIVE_PROVIDER = new LiveWorldMapProvider();

    private static String activeIdentity;
    private static Path activeCacheDirectory;

    private TerrainMapCache() {}

    /** Legacy lookup retained for callers outside the renderer. */
    public static int colorAt(ClientLevel level, int worldX, int worldZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        int[] grid = CACHE.get(key);
        if (grid == null && !DISK_CHECKED.contains(key)) {
            DISK_CHECKED.add(key);
            grid = readTile(key);
            if (grid != null) CACHE.put(key, grid);
        }

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
                if (CACHE.containsKey(key)) continue;
                if (!DISK_CHECKED.contains(key)) {
                    DISK_CHECKED.add(key);
                    int[] diskTile = readTile(key);
                    if (diskTile != null) {
                        CACHE.put(key, diskTile);
                        continue;
                    }
                }
                requestChunk(level, key);
            }
        }
    }

    public static void tick(ClientLevel level) {
        ensureLevel(level);
        XAERO_PROVIDER.tick(level);

        // Keep the fallback provider available for future use, but do not invoke it here.
        // The controller map must never generate/load live terrain as a side effect of opening it.
        int processed = 0;
        while (processed < CHUNKS_PER_TICK && !QUEUE.isEmpty()) {
            long key = QUEUE.pollFirst();
            QUEUED.remove(key);
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            int[] grid = LIVE_PROVIDER.getChunkTile(level, chunkX, chunkZ);
            if (grid != null) {
                CACHE.put(key, grid);
                DISK_CHECKED.add(key);
                writeTile(key, grid);
                processed++;
            }
        }
    }

    private static void requestChunk(ClientLevel level, long key) {
        if (QUEUED.contains(key) || CACHE.containsKey(key)) return;
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);

        int[] xaeroTile = XAERO_PROVIDER.getChunkTile(level, chunkX, chunkZ);
        if (xaeroTile != null) {
            CACHE.put(key, xaeroTile);
            writeTile(key, xaeroTile);
            DISK_CHECKED.add(key);
        }
        // If Xaero has no data yet, leave this cell unexplored. Do not load/sample a Minecraft chunk.
    }

    private static void ensureLevel(ClientLevel level) {
        String identity = buildIdentity(level);
        if (identity.equals(activeIdentity)) return;

        CACHE.clear();
        QUEUED.clear();
        DISK_CHECKED.clear();
        QUEUE.clear();
        XAERO_PROVIDER.clear();
        LIVE_PROVIDER.clear();
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

    private static int[] readTile(long key) {
        if (activeCacheDirectory == null) return null;
        Path path = tilePath(key);
        if (!Files.isRegularFile(path)) return null;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != FORMAT_VERSION) return null;
            int[] grid = new int[256];
            for (int i = 0; i < grid.length; i++) grid[i] = in.readInt();
            return grid;
        } catch (IOException | RuntimeException ignored) { return null; }
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
        QUEUE.clear();
        QUEUED.clear();
        DISK_CHECKED.clear();
        XAERO_PROVIDER.clear();
        LIVE_PROVIDER.clear();
        activeIdentity = null;
        activeCacheDirectory = null;
    }
}
