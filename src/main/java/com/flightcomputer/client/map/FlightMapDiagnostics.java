package com.flightcomputer.client.map;

/**
 * Cheap provider-neutral diagnostics. Counters are intentionally monotonic so the UI can
 * calculate deltas without requiring knowledge of the selected provider.
 */
public final class FlightMapDiagnostics {
    private FlightMapProviderKind provider = FlightMapProviderKind.NONE;
    private FlightMapProviderState state = FlightMapProviderState.DISCOVERING;
    private long requested;
    private long pending;
    private long snapshots;
    private long decoded;
    private long ready;
    private long uploaded;
    private long failed;
    private long retried;
    private long cacheHits;
    private long cacheMisses;
    private long dropped;
    private long lastProgressTick;
    private String lastError = "<none>";
    private boolean renderStateClean = true;

    public void provider(FlightMapProviderKind value) { provider = value == null ? FlightMapProviderKind.NONE : value; }
    public void state(FlightMapProviderState value) { state = value == null ? FlightMapProviderState.DEGRADED : value; }
    public void requested() { requested++; }
    public void pending(long value) { pending = Math.max(0L, value); }
    public void snapshot() { snapshots++; }
    public void decoded() { decoded++; lastProgressTick = tick(); }
    public void ready() { ready++; lastProgressTick = tick(); }
    public void uploaded() { uploaded++; lastProgressTick = tick(); }
    public void failed(Throwable throwable) { failed++; lastError = safe(throwable); state = FlightMapProviderState.DEGRADED; }
    public void retry() { retried++; }
    public void cacheHit() { cacheHits++; }
    public void cacheMiss() { cacheMisses++; }
    public void dropped() { dropped++; }
    public void renderStateClean(boolean clean) { renderStateClean = clean; }

    public FlightMapProviderKind provider() { return provider; }
    public FlightMapProviderState state() { return state; }
    public long requestedCount() { return requested; }
    public long pendingCount() { return pending; }
    public long snapshots() { return snapshots; }
    public long decodedCount() { return decoded; }
    public long readyCount() { return ready; }
    public long uploadedCount() { return uploaded; }
    public long failedCount() { return failed; }
    public long retryCount() { return retried; }
    public long cacheHits() { return cacheHits; }
    public long cacheMisses() { return cacheMisses; }
    public long droppedCount() { return dropped; }
    public long lastProgressTick() { return lastProgressTick; }
    public String lastError() { return lastError; }
    public boolean renderStateClean() { return renderStateClean; }

    // Compatibility accessors retained for older Navigation Console builds.
    public long requestedTiles() { return requestedCount(); }
    public long pendingTiles() { return pendingCount(); }
    public long decodedTiles() { return decodedCount(); }
    public long failedTiles() { return failedCount(); }

    private static long tick() {
        return System.nanoTime() / 50_000_000L;
    }

    private static String safe(Throwable throwable) {
        if (throwable == null) return "<unknown>";
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
