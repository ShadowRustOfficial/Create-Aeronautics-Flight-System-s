package com.flightcomputer.client.map.nativeimpl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Native Flight Computer map pipeline. Only snapshotting touches Minecraft state;
 * tile generation is worker-safe and the GUI only consumes the cache.
 */
public final class NativeMapPipeline implements AutoCloseable {
    private static final int MAX_PENDING = 512;
    private static final int MAX_SNAPSHOTS_PER_TICK = 4;
    private static final int MAX_COMPLETIONS_PER_TICK = 8;

    private final NativeMapCache cache = new NativeMapCache(2048);
    private final ArrayDeque<NativeMapTileKey> pending = new ArrayDeque<>();
    private final Set<NativeMapTileKey> pendingSet = new HashSet<>();
    private final ArrayDeque<Future<NativeMapTile>> completed = new ArrayDeque<>();
    private final ExecutorService worker = Executors.newFixedThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "FlightComputer-NativeMap");
        thread.setDaemon(true);
        return thread;
    });

    private String worldId;
    private ResourceKey<Level> dimension;
    private long revision;
    private long submitted;
    private long generated;
    private long failed;

    public NativeMapTile getCached(NativeMapTileKey key) {
        return cache.get(key);
    }

    /** Render/UI side: submit only; never performs snapshotting or generation. */
    public void request(NativeMapTileKey key) {
        if (cache.get(key) != null) return;
        if (pendingSet.size() >= MAX_PENDING) return;
        if (pendingSet.add(key)) pending.addLast(key);
    }

    /** Client tick: bounded world snapshots and completion collection. */
    public void tick(ClientLevel level) {
        if (level == null) return;
        resetWorldIfNeeded(level);
        collectCompleted();

        int snapshots = 0;
        while (snapshots++ < MAX_SNAPSHOTS_PER_TICK && !pending.isEmpty()) {
            NativeMapTileKey key = pending.pollFirst();
            pendingSet.remove(key);
            if (cache.get(key) != null) continue;

            NativeChunkSnapshot snapshot = NativeMapSnapshotter.snapshot(level, key, revision++);
            if (snapshot == null) continue;
            submitted++;
            completed.addLast(worker.submit(() -> NativeMapTileWorker.generate(snapshot)));
        }
        collectCompleted();
    }

    private void collectCompleted() {
        int processed = 0;
        while (processed++ < MAX_COMPLETIONS_PER_TICK && !completed.isEmpty()) {
            Future<NativeMapTile> future = completed.peekFirst();
            if (!future.isDone()) break;
            completed.removeFirst();
            try {
                NativeMapTile tile = future.get();
                cache.put(tile);
                generated++;
            } catch (Exception e) {
                failed++;
            }
        }
    }

    private void resetWorldIfNeeded(ClientLevel level) {
        String currentWorld = buildWorldId(level);
        ResourceKey<Level> currentDimension = level.dimension();
        if (currentWorld.equals(worldId) && currentDimension.equals(dimension)) return;
        clearQueues();
        cache.clear();
        worldId = currentWorld;
        dimension = currentDimension;
        revision = 0L;
    }

    private String buildWorldId(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null) return "server:" + minecraft.getCurrentServer().ip;
        if (minecraft.getSingleplayerServer() != null) {
            String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
            return "singleplayer:" + (name == null || name.isBlank() ? "unknown" : name);
        }
        return "unknown";
    }

    private void clearQueues() {
        pending.clear();
        pendingSet.clear();
        for (Future<NativeMapTile> future : completed) future.cancel(false);
        completed.clear();
    }

    public int pending() { return pendingSet.size(); }
    public int cached() { return cache.size(); }
    public long submitted() { return submitted; }
    public long generated() { return generated; }
    public long failed() { return failed; }
    public double hitRatio() { return cache.hitRatio(); }

    @Override
    public void close() {
        clearQueues();
        worker.shutdownNow();
        cache.clear();
    }
}
