package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/** Provider-neutral terrain pipeline. Rendering consumes completed cache entries only. */
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

    /** Render-only fast path. A miss is scheduled; no world scan or generation occurs here. */
    public int[] getCachedTile(ClientLevel level, int chunkX, int chunkZ) {
        if (provider == null || level == null) return null;
        int[] tile = provider.getCachedChunkTile(level, chunkX, chunkZ);
        if (tile != null) {
            diagnostics.cacheHit();
            return tile;
        }
        diagnostics.cacheMiss();
        if (!provider.isTilePending(chunkX, chunkZ)) {
            if (scheduler.offer(chunkX, chunkZ, 50)) diagnostics.pending(scheduler.size());
        }
        return null;
    }

    /**
     * Client-thread request pump. Providers capture Minecraft state here and may then
     * perform pure CPU work asynchronously. The render path never waits for completion.
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
            provider.requestChunkTile(level, request.chunkX(), request.chunkZ());
            if (provider.getCachedChunkTile(level, request.chunkX(), request.chunkZ()) != null) {
                diagnostics.decoded();
            }
        }
        diagnostics.pending(scheduler.size());
        diagnostics.state(scheduler.isEmpty() ? FlightMapProviderState.READY : FlightMapProviderState.WAITING);
    }

    public FlightMapRequestScheduler scheduler() { return scheduler; }
    public FlightMapDiagnostics diagnostics() { return diagnostics; }
    public FlightMapDataProvider provider() { return provider; }
}
