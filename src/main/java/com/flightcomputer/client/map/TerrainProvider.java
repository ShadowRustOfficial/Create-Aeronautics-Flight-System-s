package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Renderer-facing terrain contract. Implementations own loading, decoding and caching.
 * The GUI never knows which map mod supplies the terrain.
 */
public interface TerrainProvider {
    String id();

    void tick(ClientLevel level);

    void request(TerrainViewport viewport, ClientLevel level);

    /** Returns ARGB terrain colour, or 0 when the requested sample is not ready. */
    int sampleColor(ClientLevel level, int worldX, int worldZ);

    TerrainProviderDiagnostics diagnostics(ClientLevel level);

    void clear();
}
