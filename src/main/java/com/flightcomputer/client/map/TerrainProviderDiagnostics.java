package com.flightcomputer.client.map;

/** Snapshot safe to expose to the Flight Computer diagnostics UI. */
public record TerrainProviderDiagnostics(
        TerrainProviderState state,
        String provider,
        String message,
        String dimension,
        long requestedRegions,
        long loadedRegions,
        long decodedLeaves,
        long cachedLeaves,
        long renderedSamples,
        long failedSamples
) {
    public static TerrainProviderDiagnostics offline(String provider, String message) {
        return new TerrainProviderDiagnostics(
                TerrainProviderState.OFFLINE, provider, message, "unknown",
                0, 0, 0, 0, 0, 0
        );
    }
}
