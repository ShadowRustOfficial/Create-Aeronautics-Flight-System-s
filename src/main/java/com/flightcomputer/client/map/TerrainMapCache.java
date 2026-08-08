package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Normalized client-side map cache. Xaero's explored map data is the preferred source.
 * Rendering never performs disk reads or queues work; map preparation happens from tick().
 * The Flight Controller map intentionally does not fall back to loading/sampling live chunks.
 */
public final class TerrainMapCache {
    private static final int FORMAT_VERSION = 3;
    private static final int MAX_PENDING_PROMOTIONS_PER_TICK = 512;
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

        // Always queue the player's current region first so the first known-good Xaero tile
        // can reach the renderer before the rest of the viewport is populated.
        requestChunk(level, ChunkPos.asLong(Math.floorDiv(centerWorldX, 16), Math.floorDiv(centerWorldZ, 16)));

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (!CACHE.containsKey(key)) requestChunk(level, key);
            }
        }
    }

    /**
     * Main-thread pump. Disk/cache reads and Xaero decoding are bounded and never run
     * from render(). The provider's decoder thread produces normalized tiles; this
     * method promotes completed tiles into the render cache and persists them.
     */
    public static void tick(ClientLevel level) {
        ensureLevel(level);
        XAERO_PROVIDER.tick(level);

        // The provider decodes asynchronously from the renderer's perspective: decoding occurs
        // during tick(), then the newly decoded chunk tiles are transferred into our normalized
        // cache here. The old implementation never performed this transfer, so REQUESTED tiles
        // could remain permanently absent even after a successful Xaero decode.
        for (Map.Entry<Long, int[]> entry : XAERO_PROVIDER.drainDecodedTiles().entrySet()) {
            long key = entry.getKey();
            CACHE.put(key, entry.getValue());
            REQUESTED.remove(key);
            writeTile(key, entry.getValue());
        }

        int promoted = 0;
        Iterator<Long> iterator = REQUESTED.iterator();
        while (iterator.hasNext() && promoted < MAX_PENDING_PROMOTIONS_PER_TICK) {
            long key = iterator.next();
            int[] tile = XAERO_PROVIDER.getChunkTile(level, ChunkPos.getX(key), ChunkPos.getZ(key));
            if (tile == null) continue;
            CACHE.put(key, tile);
            writeTile(key, tile);
            iterator.remove();
            promoted++;
        }
    }

    /** Current Xaero integration diagnostics, primarily for troubleshooting/logging. */
    public static String xaeroDiagnostics() {
        return XAERO_PROVIDER.diagnostics();
    }

    private static void requestChunk(ClientLevel level, long key) {
        if (CACHE.containsKey(key) || REQUESTED.contains(key)) return;

        // Fast persistent cache path. This is an exact-file lookup, never a directory scan.
        int[] diskTile = readTile(key);
        if (diskTile != null) {
            CACHE.put(key, diskTile);
            return;
        }

        REQUESTED.add(key);
        // The provider owns the asynchronous Xaero file work. Keeping REQUESTED until
        // the provider actually returns a tile fixes the previous "queued forever"
        // failure where a region decoded successfully but the render cache stayed empty.
        XAERO_PROVIDER.getChunkTile(level, ChunkPos.getX(key), ChunkPos.getZ(key));
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

    private static int[] readTile(long key) {
        if (activeCacheDirectory == null) return null;
        Path path = tilePath(key);
        if (!Files.isRegularFile(path)) return null;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION && version != 2) return null;
            int[] grid = new int[256];
            for (int i = 0; i < grid.length; i++) grid[i] = in.readInt();
            return grid;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static void writeTile(long key, int[] grid) {
        if (activeCacheDirectory == null || grid == null || grid.length != 256) return;
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
