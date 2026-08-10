package com.flightcomputer.client.map.nativeimpl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Immutable identity for a native Flight Computer map tile. */
public record NativeMapTileKey(
        String worldId,
        ResourceKey<Level> dimension,
        int layer,
        int chunkX,
        int chunkZ) {
}
