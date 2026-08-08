package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

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
 * Client-side persistent terrain cache. It only samples chunks that Minecraft has
 * already loaded; it never requests a chunk solely for the Flight Computer map.
 * Cached 16x16 surface-color tiles survive closing the map and restarting the game.
 */
public final class TerrainMapCache {
    private static final int CHUNKS_PER_TICK = 6;
    private static final int FORMAT_VERSION = 1;

    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final Set<Long> DISK_CHECKED = new HashSet<>();
    private static final Deque<Long> QUEUE = new ArrayDeque<>();

    private static String activeIdentity;
    private static Path activeCacheDirectory;

    private TerrainMapCache() {}

    /** Returns the cached color, or 0 when the tile is not known yet. */
    public static int colorAt(ClientLevel level, int worldX, int worldZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(worldX >> 4, worldZ >> 4);
        int[] grid = CACHE.get(key);
        if (grid == null && !DISK_CHECKED.contains(key)) {
            DISK_CHECKED.add(key);
            grid = readTile(key);
            if (grid != null) CACHE.put(key, grid);
        }
        if (grid == null) {
            enqueue(level, key);
            return 0;
        }
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        return grid[localZ * 16 + localX];
    }

    /** Call once per client tick while the map is open. */
    public static void tick(ClientLevel level) {
        ensureLevel(level);
        int processed = 0;
        while (processed < CHUNKS_PER_TICK && !QUEUE.isEmpty()) {
            long key = QUEUE.pollFirst();
            QUEUED.remove(key);
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (level.hasChunk(chunkX, chunkZ)) {
                int[] grid = computeChunk(level, chunkX, chunkZ);
                CACHE.put(key, grid);
                DISK_CHECKED.add(key);
                writeTile(key, grid);
                processed++;
            }
        }
    }

    private static void enqueue(ClientLevel level, long key) {
        if (QUEUED.contains(key) || CACHE.containsKey(key)) return;
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        if (!level.hasChunk(chunkX, chunkZ)) return;
        QUEUED.add(key);
        QUEUE.addLast(key);
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

    private static void ensureLevel(ClientLevel level) {
        String identity = buildIdentity(level);
        if (identity.equals(activeIdentity)) return;
        CACHE.clear();
        QUEUED.clear();
        DISK_CHECKED.clear();
        QUEUE.clear();
        activeIdentity = identity;
        activeCacheDirectory = cacheDirectory(identity);
        try { Files.createDirectories(activeCacheDirectory); } catch (IOException ignored) { }
    }

    private static String buildIdentity(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        String server = minecraft.getCurrentServer() != null
                ? minecraft.getCurrentServer().ip
                : "singleplayer:" + level.getLevelData().getLevelName();
        String dimension = level.dimension().location().toString();
        return sanitize(server) + "__" + sanitize(dimension);
    }

    private static Path cacheDirectory(String identity) {
        Path root = Minecraft.getInstance().gameDirectory
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
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static void writeTile(long key, int[] grid) {
        if (activeCacheDirectory == null) return;
        Path path = tilePath(key);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(FORMAT_VERSION);
            for (int color : grid) out.writeInt(color);
        } catch (IOException ignored) {
            return;
        }
        try {
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            try { Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ignoredAgain) { }
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Clears only the in-memory state; persisted tiles remain available next session. */
    public static void clear() {
        CACHE.clear();
        QUEUE.clear();
        QUEUED.clear();
        DISK_CHECKED.clear();
        activeIdentity = null;
        activeCacheDirectory = null;
    }
}
