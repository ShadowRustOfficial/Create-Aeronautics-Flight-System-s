package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/** Client-side source of normalized map tile data. Providers never own Minecraft chunks. */
public interface FlightMapDataProvider {
    /** Returns a cached 16x16 chunk color tile, or null when this provider has no data. */
    int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ);

    /** Performs bounded provider work on the client tick. */
    void tick(ClientLevel level);

    /** Clears world/profile-specific provider state. */
    void clear();
}
