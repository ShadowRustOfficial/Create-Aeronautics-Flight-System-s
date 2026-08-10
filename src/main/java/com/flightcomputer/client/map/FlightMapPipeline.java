package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;

/**
 * Provider-neutral terrain pipeline. The UI asks for tiles; the pipeline owns request state,
 * provider work and diagnostics. Rendering should only consume completed cache entries.
 */
public final class FlightMapPipeline {
    private final FlightMapRequestScheduler scheduler;
    private final FlightMapDiagnostics diagnostics;
    private FlightMapDataProvider provider;

    public FlightMapPipeline(FlightMapDataProvider provider) {
        this(provider, new FlightMapRequestScheduler(), new FlightMapDiagnostics());
    }

    public FlightMapPipeline(FlightMapDataProvider provider,
                             FlightMapRequestScheduler scheduler,
                             FlightMapDiagnostics diagnostics) {
        this.provider = provider;
        this.scheduler = scheduler;
        this.diagnostics = diagnostics;
        diagnostics.provider(FlightMapProviderKind.NONE);
        diagnostics.state(FlightMapProviderState.DISCOVERING);
    }

    public void setProvider(FlightMapDataProvider next, FlightMapProviderKind kind) {
        if (provider != next && provider != null) provider.clear();
        provider = next;
        scheduler.clear();
        diagnostics.provider(kind);
        diagnostics.state(next == null ? FlightMapProviderState.FAILED : FlightMapProviderState.DISCOVERING);
    }

    /** Read-only fast path for the render layer. A miss schedules work; it never performs it. */
    public int[] getCachedTile(ClientLevel level, int chunkX, int chunkZ) {
        if (provider == null || level == null) return null;
        int[] tile = provider.getChunkTile(level, chunkX, chunkZ);
        if (tile != null) {
            diagnostics.cacheHit();
            return tile;
        }
        diagnostics.cacheMiss();
        int distance = Math.max(Math.abs(chunkX - ChunkPos.getX(ChunkPos.asLong(chunkX, chunkZ))),
                Math.abs(chunkZ - ChunkPos.getZ(ChunkPos.asLong(chunkX, chunkZ))));
        scheduler.offer(chunkX, chunkZ, FlightMapRequestScheduler.priorityForDistance(distance));
        diagnostics.pending(scheduler.size());
        return null;
    }

    /**
     * Client-thread bounded work pump. Providers may internally stage work, but this layer
     * never blocks waiting for a decoder or texture upload.
     */
    public void tick(ClientLevel level, int maxRequests) {
        if (provider == null || level == null) return;
        diagnostics.state(FlightMapProviderState.WAITING);
        provider.tick(level);
        int budget = Math.max(0, maxRequests);
        while (budget-- > 0) {
            FlightMapTileRequest request = scheduler.poll();
            if (request == null) break;
            diagnostics.requested();
            int[] tile = provider.getChunkTile(level, request.chunkX(), request.chunkZ());
            if (tile != null) diagnostics.decoded();
            else diagnostics.retry();
        }
        diagnostics.pending(scheduler.size());
        diagnostics.state(scheduler.isEmpty() ? FlightMapProviderState.READY : FlightMapProviderState.WAITING);
    }

    public FlightMapRequestScheduler scheduler() { return scheduler; }
    public FlightMapDiagnostics diagnostics() { return diagnostics; }
    public FlightMapDataProvider provider() { return provider; }
}
