package com.flightcomputer.client.map.nativeimpl;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * GUI-independent viewport renderer core. The Flight Computer screen supplies a sink that
 * knows how to draw a resident tile. This class never accesses Minecraft chunks while drawing.
 */
public final class NativeMapViewportRenderer {
    private final NativeMapPipeline pipeline;

    public NativeMapViewportRenderer(NativeMapPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Draw a square viewport. Missing tiles are requested and omitted from the draw pass.
     * The caller can draw its own placeholder behind the map.
     */
    public void render(ClientLevel level,
                       String worldId,
                       ResourceKey<Level> dimension,
                       int layer,
                       int centerChunkX,
                       int centerChunkZ,
                       int radiusChunks,
                       TileSink sink) {
        int radius = Math.max(0, Math.min(radiusChunks, 64));
        for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
            for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
                NativeMapTileKey key = new NativeMapTileKey(worldId, dimension, layer, x, z);
                NativeMapTile tile = pipeline.getCached(key);
                if (tile == null) {
                    pipeline.request(key);
                    continue;
                }
                sink.draw(key, tile);
            }
        }
    }

    @FunctionalInterface
    public interface TileSink {
        void draw(NativeMapTileKey key, NativeMapTile tile);
    }
}
