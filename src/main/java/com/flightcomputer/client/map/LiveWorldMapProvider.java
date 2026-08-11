package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Native terrain provider. Minecraft data is sampled only on the client thread;
 * expensive tile shading runs on bounded CPU workers and never touches Minecraft objects.
 *
 * Generated tiles are retained for the lifetime of the active client world/dimension.
 * The cache is shared by all Flight Map screens so closing and reopening the GUI is a
 * cache read, not a regeneration pass. A client-level lifecycle owner calls
 * clearSessionCache() when the world/dimension actually changes.
 */
public final class LiveWorldMapProvider implements FlightMapDataProvider {
    private static final int MAX_JOBS = 64;
    private static final int LOADED_SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_SCAN_RADIUS_CHUNKS = 64;
    private static final int MAX_NEW_CAPTURES_PER_SCAN = 8;

    /** Shared active-session cache: map screens must never evict it on close. */
    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static final ArrayBlockingQueue<Long> QUEUED = new ArrayBlockingQueue<>(MAX_JOBS);
    private static final Map<Long, Future<?>> RUNNING = new HashMap<>();
    private static final ExecutorService WORKERS = createWorkers();

    private final CpuTerrainTileGenerator generator = new CpuTerrainTileGenerator();
    private int loadedScanTicks;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    private static ExecutorService createWorkers() {
        int cores = Runtime.getRuntime().availableProcessors();
        int workerCount = Math.max(1, Math.min(4, cores - 2));
        return Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "FlightComputer-Terrain");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized int[] getCachedChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        synchronized (CACHE) {
            return CACHE.get(key(chunkX, chunkZ));
        }
    }

    @Override
    public void requestChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        if (level == null) return;
        long key = key(chunkX, chunkZ);
        synchronized (CACHE) {
            if (CACHE.containsKey(key) || RUNNING.containsKey(key) || QUEUED.contains(key)) return;
            if (QUEUED.remainingCapacity() == 0 || !level.hasChunk(chunkX, chunkZ)) return;

            TerrainChunkSnapshot snapshot = capture(level, chunkX, chunkZ);
            if (snapshot == null || !QUEUED.offer(key)) return;
            Future<?> future = WORKERS.submit(() -> {
                int[] result = generator.generate(snapshot);
                synchronized (CACHE) {
                    CACHE.put(key, result);
                    RUNNING.remove(key);
                    QUEUED.remove(key);
                }
            });
            RUNNING.put(key, future);
        }
    }

    @Override
    public boolean isTilePending(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        synchronized (CACHE) {
            return RUNNING.containsKey(key) || QUEUED.contains(key);
        }
    }

    /**
     * Converts chunks already resident in the client's chunk cache into Flight Map work.
     * It never requests a missing chunk. This method is safe to call continuously from
     * the client tick even when no map GUI is open.
     */
    @Override
    public void observeLoadedClientChunks(ClientLevel level) {
        if (level == null || !level.isClientSide()) return;
        if (level.getChunkSource() == null) return;

        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        ChunkPos playerChunk = player.chunkPosition();
        loadedScanTicks++;
        boolean moved = playerChunk.x != lastPlayerChunkX || playerChunk.z != lastPlayerChunkZ;
        if (!moved && loadedScanTicks < LOADED_SCAN_INTERVAL_TICKS) return;
        loadedScanTicks = 0;
        lastPlayerChunkX = playerChunk.x;
        lastPlayerChunkZ = playerChunk.z;

        int loadedCount = Math.max(1, level.getChunkSource().getLoadedChunksCount());
        int radius = (int) Math.ceil((Math.sqrt(loadedCount) - 1.0D) * 0.5D) + 2;
        radius = Math.max(2, Math.min(MAX_SCAN_RADIUS_CHUNKS, radius));

        int newCaptures = 0;
        for (int chunkZ = playerChunk.z - radius; chunkZ <= playerChunk.z + radius; chunkZ++) {
            for (int chunkX = playerChunk.x - radius; chunkX <= playerChunk.x + radius; chunkX++) {
                if (newCaptures >= MAX_NEW_CAPTURES_PER_SCAN) return;
                long key = key(chunkX, chunkZ);
                synchronized (CACHE) {
                    if (CACHE.containsKey(key) || RUNNING.containsKey(key) || QUEUED.contains(key)) continue;
                }
                if (level.hasChunk(chunkX, chunkZ)) {
                    requestChunkTile(level, chunkX, chunkZ);
                    newCaptures++;
                }
            }
        }
    }

    private TerrainChunkSnapshot capture(ClientLevel level, int chunkX, int chunkZ) {
        int[] colors = new int[256];
        int[] heights = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int index = lz * 16 + lx;
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int y = Math.max(level.getMinBuildHeight(),
                        level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1);
                pos.set(wx, y, wz);
                BlockState state = level.getBlockState(pos);
                MapColor mapColor = state.getMapColor(level, pos);
                colors[index] = 0xFF000000 | (mapColor.col & 0xFFFFFF);
                heights[index] = y;
            }
        }
        return new TerrainChunkSnapshot(chunkX, chunkZ, colors, heights);
    }

    @Override public synchronized void tick(ClientLevel level) { }

    /**
     * Provider lifecycle reset. Deliberately does not clear the shared terrain cache:
     * GUI close/reopen must preserve generated terrain. Use clearSessionCache() when
     * Minecraft changes the active ClientLevel/dimension.
     */
    @Override
    public void clear() {
        synchronized (CACHE) {
            for (Future<?> future : RUNNING.values()) future.cancel(false);
            RUNNING.clear();
            QUEUED.clear();
        }
        loadedScanTicks = 0;
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
    }

    /** Clears all active-world terrain and in-flight work on a real ClientLevel change. */
    public static void clearSessionCache() {
        synchronized (CACHE) {
            for (Future<?> future : RUNNING.values()) future.cancel(false);
            RUNNING.clear();
            QUEUED.clear();
            CACHE.clear();
        }
    }

    public int cachedTiles() {
        synchronized (CACHE) { return CACHE.size(); }
    }

    public int queuedTiles() {
        synchronized (CACHE) { return QUEUED.size(); }
    }

    private long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
}
