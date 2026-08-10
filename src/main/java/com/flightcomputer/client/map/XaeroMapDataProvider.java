package com.flightcomputer.client.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.region.MapTileChunk;
import xaero.map.region.texture.LeafRegionTexture;
import xaero.map.region.MapRegion;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter over Xaero World Map's already-decoded map state.
 *
 * Flight Computer deliberately does not parse .xwmc files. Xaero owns cache decoding,
 * compression, palette handling, biome colouring and cache-version compatibility. We only
 * request the regions we need and copy the resulting 64x64 RGBA leaf buffers into our renderer.
 *
 * All calls are client/render-thread calls because Xaero's MapProcessor and texture objects are
 * client-owned. No Minecraft chunks are loaded by this class.
 */
public final class XaeroMapDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LEAF_PIXELS = 64;
    public static final int LEAF_WORLD_PIXELS = LEAF_PIXELS;
    private static final int MAP_TILES_PER_LEAF = 4;
    private static final int LEAF_CHUNKS = 4;
    private static final int MAX_LOAD_REQUESTS_PER_TICK = 4;

    private final Map<Long, Integer> requestedRegions = new HashMap<>();
    private String identity;
    private MapProcessor processor;
    private String diagnostics = "Xaero adapter not initialized.";

    public void tick(ClientLevel level) {
        ensure(level);
    }

    public boolean available(ClientLevel level) {
        ensure(level);
        return processor != null;
    }

    /**
     * Requests all leaf regions intersecting a world-space rectangle at the selected Xaero LOD.
     * The request is handed to Xaero's own MapSaveLoad queue; no filesystem parsing occurs here.
     */
    public void requestWorldArea(ClientLevel level, double centerX, double centerZ, double radiusBlocks,
                                 int mapLevel) {
        if (!ensure(level)) return;
        mapLevel = Math.max(0, Math.min(8, mapLevel));
        double blocksPerMapPixel = 1 << mapLevel;
        double minPixelX = Math.floor((centerX - radiusBlocks) / blocksPerMapPixel);
        double maxPixelX = Math.floor((centerX + radiusBlocks) / blocksPerMapPixel);
        double minPixelZ = Math.floor((centerZ - radiusBlocks) / blocksPerMapPixel);
        double maxPixelZ = Math.floor((centerZ + radiusBlocks) / blocksPerMapPixel);

        int minLeafX = Math.floorDiv((int) minPixelX, LEAF_PIXELS);
        int maxLeafX = Math.floorDiv((int) maxPixelX, LEAF_PIXELS);
        int minLeafZ = Math.floorDiv((int) minPixelZ, LEAF_PIXELS);
        int maxLeafZ = Math.floorDiv((int) maxPixelZ, LEAF_PIXELS);

        int queued = 0;
        // Centre first. This makes the map become useful quickly without flooding Xaero's loader.
        int centreLeafX = Math.floorDiv((int) Math.floor(centerX / blocksPerMapPixel), LEAF_PIXELS);
        int centreLeafZ = Math.floorDiv((int) Math.floor(centerZ / blocksPerMapPixel), LEAF_PIXELS);
        queued += requestLeaf(level, centreLeafX, centreLeafZ, mapLevel) ? 1 : 0;

        for (int z = minLeafZ; z <= maxLeafZ && queued < MAX_LOAD_REQUESTS_PER_TICK; z++) {
            for (int x = minLeafX; x <= maxLeafX && queued < MAX_LOAD_REQUESTS_PER_TICK; x++) {
                if (x == centreLeafX && z == centreLeafZ) continue;
                if (requestLeaf(level, x, z, mapLevel)) queued++;
            }
        }
    }

    /**
     * Returns the exact decoded Xaero leaf texture for a map-level leaf coordinate.
     * The returned object is owned by Xaero and must not be mutated.
     */
    public LeafSnapshot getLeaf(int mapLevel, int leafX, int leafZ) {
        if (processor == null) return null;

        MapTileChunk chunk = processor.getMapChunk(mapLevel, leafX, leafZ);
        if (chunk == null || !chunk.hasHadTerrain()) return null;

        LeafRegionTexture texture = chunk.getLeafTexture();
        if (texture == null || texture.isColorBufferCompressed()) return null;
        ByteBuffer source = texture.getDirectColorBuffer();
        if (source == null) return null;

        int expected = LEAF_PIXELS * LEAF_PIXELS * 4;
        if (source.remaining() < expected) {
            diagnostics = "Xaero leaf " + leafX + "," + leafZ + " level " + mapLevel
                    + " has only " + source.remaining() + " bytes; expected " + expected;
            return null;
        }

        ByteBuffer copy = source.duplicate();
        copy.rewind();
        byte[] rgba = new byte[expected];
        copy.get(rgba);
        return new LeafSnapshot(mapLevel, leafX, leafZ, texture.getTextureVersion(), rgba);
    }

    public String diagnostics() {
        return diagnostics;
    }

    public void clear() {
        requestedRegions.clear();
        processor = null;
        identity = null;
        diagnostics = "Xaero adapter cleared.";
    }

    private boolean ensure(ClientLevel level) {
        if (level == null || Minecraft.getInstance() == null) return false;
        String newIdentity = buildIdentity(level);
        if (newIdentity.equals(identity) && processor != null) return true;

        identity = newIdentity;
        requestedRegions.clear();
        processor = null;

        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) {
                diagnostics = "Xaero World Map session is not usable yet.";
                return false;
            }
            MapProcessor candidate = session.getMapProcessor();
            if (candidate == null || candidate.getWorld() != level) {
                diagnostics = "Xaero session exists but is not attached to the current ClientLevel.";
                return false;
            }
            if (!candidate.isMapWorldUsable()) {
                diagnostics = "Xaero MapProcessor reports the current map world is not usable yet.";
                return false;
            }
            processor = candidate;
            diagnostics = "Xaero native MapProcessor connected.";
            return true;
        } catch (Throwable t) {
            diagnostics = "Xaero API connection failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Xaero API connection unavailable", t);
            return false;
        }
    }

    private boolean requestLeaf(ClientLevel level, int leafX, int leafZ, int mapLevel) {
        if (processor == null) return false;
        long key = pack(mapLevel, leafX, leafZ);
        if (requestedRegions.containsKey(key)) return false;

        int regionX = Math.floorDiv(leafX, 8);
        int regionZ = Math.floorDiv(leafZ, 8);
        try {
            MapRegion region = processor.getLeafMapRegion(mapLevel, regionX, regionZ, true);
            if (region == null) return false;
            if (!region.hasHadTerrain() || !region.isLoaded()) {
                processor.getMapSaveLoad().requestLoad(region, "flightcomputer", false);
            }
            requestedRegions.put(key, region.getReloadVersion());
            return true;
        } catch (Throwable t) {
            diagnostics = "Xaero region request failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Failed to request Xaero region {},{} level {}", regionX, regionZ, mapLevel, t);
            return false;
        }
    }

    private static String buildIdentity(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        String server = mc.getCurrentServer() != null
                ? mc.getCurrentServer().ip
                : "singleplayer:" + (mc.getSingleplayerServer() == null
                ? "unknown"
                : mc.getSingleplayerServer().getWorldData().getLevelName());
        ResourceLocation dimension = level.dimension().location();
        return server + "|" + dimension;
    }

    private static long pack(int level, int x, int z) {
        long value = ((long) level & 0xFFL) << 56;
        value |= ((long) x & 0x0FFFFFFFL) << 28;
        value |= (long) z & 0x0FFFFFFFL;
        return value;
    }

    public record LeafSnapshot(int level, int leafX, int leafZ, int textureVersion, byte[] rgba) { }
}
