package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/** Client-side source of normalized map tile data. Providers never own the Flight Computer UI. */
public interface FlightMapDataProvider {
    /** Render-thread-safe cache lookup. Must not scan chunks, touch disk, or decode provider state. */
    int[] getCachedChunkTile(ClientLevel level, int chunkX, int chunkZ);

    /** Requests one tile for bounded client-thread processing. This method must be idempotent. */
    void requestChunkTile(ClientLevel level, int chunkX, int chunkZ);

    /** Performs bounded provider work on the client tick. */
    void tick(ClientLevel level);

    /** Clears world/profile-specific provider state. */
    void clear();
}
