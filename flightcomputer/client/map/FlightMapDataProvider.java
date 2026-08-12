package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/** Client-side source of normalized map tile data. Providers never own the Flight Computer UI. */
public interface FlightMapDataProvider {
    /** Render-thread-safe cache lookup. Must not scan chunks, touch disk, or decode provider state. */
    int[] getCachedChunkTile(ClientLevel level, int chunkX, int chunkZ);

    /** Requests one tile. Minecraft/world access must remain on the client thread. */
    void requestChunkTile(ClientLevel level, int chunkX, int chunkZ);

    /** Performs bounded provider bookkeeping on the client tick. */
    void tick(ClientLevel level);

    /** True when work for the tile is already queued or executing. */
    default boolean isTilePending(int chunkX, int chunkZ) { return false; }

    /**
     * Observes the chunks currently resident in the logical client world.
     * Implementations must only consume already-loaded client chunks: this hook
     * must never request/generate a missing Minecraft chunk from the server.
     */
    default void observeLoadedClientChunks(ClientLevel level) { }

    /** Clears world/profile-specific provider state. */
    void clear();
}
