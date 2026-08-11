package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
 * Generated tiles are retained for the lifetime of the active world/dimension. The
 * renderer may move its viewport without evicting already-generated terrain, so
 * revisiting an area is a cache read rather than another CPU generation pass.
 */
public final class LiveWorldMapProvider implements FlightMapDataProvider {
    private static final int MAX_JOBS = 64;

    /**
     * Session-persistent terrain cache. Deliberately unbounded: generated terrain is
     * authoritative for this client-world session and must not be evicted merely
     * because the map viewport moved elsewhere.
     */
    private final Map<Long, int[]> cache = new HashMap<>();
    private final ArrayBlockingQueue<Long> queued = new ArrayBlockingQueue<>(MAX_JOBS);
    private final Map<Long, Future<?>> running = new HashMap<>();
    private final ExecutorService workers;
    private final CpuTerrainTileGenerator generator = new CpuTerrainTileGenerator();

    public LiveWorldMapProvider() {
        int cores = Runtime.getRuntime().availableProcessors();
        int workerCount = Math.max(1, Math.min(4, cores - 2));
        workers = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "FlightComputer-Terrain");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized int[] getCachedChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        return cache.get(key(chunkX, chunkZ));
    }

    @Override
    public synchronized void requestChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        if (level == null) return;
        long key = key(chunkX, chunkZ);
        if (cache.containsKey(key) || running.containsKey(key) || queued.contains(key)) return;
        if (queued.remainingCapacity() == 0 || !level.hasChunk(chunkX, chunkZ)) return;

        TerrainChunkSnapshot snapshot = capture(level, chunkX, chunkZ);
        if (snapshot == null || !queued.offer(key)) return;
        Future<?> future = workers.submit(() -> {
            int[] result = generator.generate(snapshot);
            synchronized (LiveWorldMapProvider.this) {
                cache.put(key, result);
                running.remove(key);
                queued.remove(key);
            }
        });
        running.put(key, future);
    }

    @Override
    public synchronized boolean isTilePending(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        return running.containsKey(key) || queued.contains(key);
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

    @Override
    public synchronized void clear() {
        for (Future<?> future : running.values()) future.cancel(false);
        running.clear();
        queued.clear();
        cache.clear();
    }

    public synchronized int cachedTiles() { return cache.size(); }
    public synchronized int queuedTiles() { return queued.size(); }

    private long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
}
