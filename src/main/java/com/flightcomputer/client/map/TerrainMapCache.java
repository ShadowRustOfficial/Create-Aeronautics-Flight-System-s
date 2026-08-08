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
 * then Xaero's explored map data, and finally samples only already-loaded chunks.
 */
public final class TerrainMapCache {
    private static final int CHUNKS_PER_TICK = 6;
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
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);

            // Xaero data is client-side and does not force a Minecraft chunk load.
            grid = XAERO_PROVIDER.getChunkTile(level, chunkX, chunkZ);
            if (grid != null) {
                CACHE.put(key, grid);
                writeTile(key, grid);
                DISK_CHECKED.add(key);
            } else {
                enqueueLive(level, key);
            }
        }

        if (grid == null) return 0;
        return grid[(Math.floorMod(worldZ, 16)) * 16 + Math.floorMod(worldX, 16)];
    }

    public static void tick(ClientLevel level) {
        ensureLevel(level);
        XAERO_PROVIDER.tick(level);

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

    private static void enqueueLive(ClientLevel level, long key) {
        if (QUEUED.contains(key) || CACHE.containsKey(key)) return;
        if (!level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) return;
        QUEUED.add(key);
        QUEUE.addLast(key);
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
