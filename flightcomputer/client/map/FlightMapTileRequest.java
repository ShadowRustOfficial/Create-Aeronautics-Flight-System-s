package com.flightcomputer.client.map;

import net.minecraft.world.level.ChunkPos;

/** Immutable, deduplicatable request for one logical map tile. */
public record FlightMapTileRequest(long key, int chunkX, int chunkZ, int priority, long sequence) {
    public static FlightMapTileRequest of(int chunkX, int chunkZ, int priority, long sequence) {
        return new FlightMapTileRequest(ChunkPos.asLong(chunkX, chunkZ), chunkX, chunkZ, priority, sequence);
    }
}
