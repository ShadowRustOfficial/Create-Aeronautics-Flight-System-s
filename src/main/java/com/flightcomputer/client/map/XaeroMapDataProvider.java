package com.flightcomputer.client.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.region.MapRegion;
import xaero.map.region.MapTileChunk;
import xaero.map.region.texture.LeafRegionTexture;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** Adapter over Xaero World Map's already-decoded map state. */
public final class XaeroMapDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LEAF_PIXELS = 64;
    private static final int MAX_LOAD_REQUESTS_PER_TICK = 12;
    private static final int REGION_RETRY_TICKS = 10;

    /** region key -> last tick at which a load request was issued */
    private final Map<Long, Long> requestedRegions = new HashMap<>();
    private long tickCounter;
    private String identity;
    private MapProcessor processor;
    private String diagnostics = "Xaero adapter not initialized.";

    public void tick(ClientLevel level) {
        tickCounter++;
        ensure(level);
    }

    public boolean available(ClientLevel level) {
        ensure(level);
        return processor != null;
    }

    /**
     * Prefetches native Xaero LOD-0 map data around the Flight Controller.
     *
     * Flight Computer zoom is deliberately NOT passed to Xaero as a map coordinate or level.
     * Xaero's MapProcessor is used only for its decoded map data and existing disk-load queue.
     */
    public void requestWorldArea(ClientLevel level, double centerX, double centerZ, double radiusBlocks,
                                 int ignoredMapLevel) {
        if (!ensure(level)) return;

        int centreLeafX = Math.floorDiv((int) Math.floor(centerX), LEAF_PIXELS);
        int centreLeafZ = Math.floorDiv((int) Math.floor(centerZ), LEAF_PIXELS);
        int minLeafX = Math.floorDiv((int) Math.floor(centerX - radiusBlocks), LEAF_PIXELS);
        int maxLeafX = Math.floorDiv((int) Math.floor(centerX + radiusBlocks), LEAF_PIXELS);
        int minLeafZ = Math.floorDiv((int) Math.floor(centerZ - radiusBlocks), LEAF_PIXELS);
        int maxLeafZ = Math.floorDiv((int) Math.floor(centerZ + radiusBlocks), LEAF_PIXELS);

        int queued = 0;
        int maxRadius = Math.max(
                Math.max(Math.abs(centreLeafX - minLeafX), Math.abs(maxLeafX - centreLeafX)),
                Math.max(Math.abs(centreLeafZ - minLeafZ), Math.abs(maxLeafZ - centreLeafZ)));

        for (int radius = 0; radius <= maxRadius && queued < MAX_LOAD_REQUESTS_PER_TICK; radius++) {
            for (int dz = -radius; dz <= radius && queued < MAX_LOAD_REQUESTS_PER_TICK; dz++) {
                int dxLimit = radius - Math.abs(dz);
                for (int dx = -dxLimit; dx <= dxLimit && queued < MAX_LOAD_REQUESTS_PER_TICK; dx++) {
                    int leafX = centreLeafX + dx;
                    int leafZ = centreLeafZ + dz;
                    if (leafX < minLeafX || leafX > maxLeafX || leafZ < minLeafZ || leafZ > maxLeafZ) continue;
                    if (requestLeaf(leafX, leafZ)) queued++;
                }
            }
        }
    }

    /**
     * Returns a copy of Xaero's decoded RGBA 64x64 leaf buffer.
     *
     * leafX/leafZ are the native LOD-0 map coordinates used by MapProcessor#getMapChunk.
     */
    public LeafSnapshot getLeaf(int ignoredMapLevel, int leafX, int leafZ) {
        if (processor == null) return null;

        try {
            int caveLayer = processor.getCurrentCaveLayer();
            MapTileChunk chunk = processor.getMapChunk(leafX, leafZ, caveLayer);
            if (chunk == null) {
                diagnostics = "Xaero MapChunk unavailable at " + leafX + "," + leafZ
                        + " layer " + caveLayer + "; waiting for region decode.";
                requestLeaf(leafX, leafZ);
                return null;
            }
            if (!chunk.hasHadTerrain()) {
                diagnostics = "Xaero MapChunk " + leafX + "," + leafZ
                        + " exists but has no decoded terrain yet; waiting for MapSaveLoad.";
                requestLeaf(leafX, leafZ);
                return null;
            }

            LeafRegionTexture texture = chunk.getLeafTexture();
            if (texture == null) {
                diagnostics = "Xaero MapChunk " + leafX + "," + leafZ
                        + " has terrain but no LeafRegionTexture yet.";
                return null;
            }
            if (texture.isColorBufferCompressed()) {
                diagnostics = "Xaero leaf " + leafX + "," + leafZ
                        + " is GPU-compressed; direct RGBA buffer unavailable."
                        + " Disable Xaero texture compression for this integration test.";
                return null;
            }

            ByteBuffer source = texture.getDirectColorBuffer();
            if (source == null) {
                diagnostics = "Xaero leaf " + leafX + "," + leafZ
                        + " has no direct color buffer yet; waiting for texture decode/upload.";
                return null;
            }

            int expected = LEAF_PIXELS * LEAF_PIXELS * 4;
            if (source.capacity() < expected) {
                diagnostics = "Xaero leaf " + leafX + "," + leafZ
                        + " has a " + source.capacity() + " byte buffer; expected " + expected;
                return null;
            }

            ByteBuffer copy = source.duplicate();
            copy.position(0);
            copy.limit(expected);
            byte[] rgba = new byte[expected];
            copy.get(rgba);
            diagnostics = "Xaero native terrain buffer ready at " + leafX + "," + leafZ + ".";
            return new LeafSnapshot(0, leafX, leafZ, texture.getTextureVersion(), rgba);
        } catch (Throwable t) {
            diagnostics = "Xaero leaf read failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Failed to read Xaero native leaf {},{}", leafX, leafZ, t);
            return null;
        }
    }

    public String diagnostics() {
        return diagnostics;
    }

    public void clear() {
        requestedRegions.clear();
        processor = null;
        identity = null;
        tickCounter = 0;
        diagnostics = "Xaero adapter cleared.";
    }

    private boolean ensure(ClientLevel level) {
        if (level == null) return false;
        String newIdentity = buildIdentity(level);
        if (newIdentity.equals(identity) && processor != null) return true;

        identity = newIdentity;
        requestedRegions.clear();
        tickCounter = 0;
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
            diagnostics = "Xaero native MapProcessor connected; requesting LOD 0 terrain.";
            return true;
        } catch (Throwable t) {
            diagnostics = "Xaero API connection failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Xaero API connection unavailable", t);
            return false;
        }
    }

    private boolean requestLeaf(int leafX, int leafZ) {
        if (processor == null) return false;

        int caveLayer = processor.getCurrentCaveLayer();
        int regionX = Math.floorDiv(leafX, 8);
        int regionZ = Math.floorDiv(leafZ, 8);
        long key = pack(caveLayer, regionX, regionZ);

        Long lastRequest = requestedRegions.get(key);
        if (lastRequest != null && tickCounter - lastRequest < REGION_RETRY_TICKS) return false;

        try {
            MapRegion region = processor.getLeafMapRegion(regionX, regionZ, caveLayer, true);
            if (region == null) {
                diagnostics = "Xaero region " + regionX + "," + regionZ
                        + " could not be obtained for layer " + caveLayer + ".";
                return false;
            }

            boolean loaded = region.isLoaded() && region.hasHadTerrain();
            if (!loaded) {
                processor.getMapSaveLoad().requestLoad(region, "flightcomputer", false);
                requestedRegions.put(key, tickCounter);
                diagnostics = "Requested Xaero region " + regionX + "," + regionZ
                        + " layer " + caveLayer + "; waiting for decode.";
                return true;
            }

            // It is already decoded. Keep the entry fresh so we do not spam the load queue.
            requestedRegions.put(key, tickCounter);
            return false;
        } catch (Throwable t) {
            diagnostics = "Xaero region request failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Failed to request Xaero region {},{} layer {}", regionX, regionZ, caveLayer, t);
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
        return server + "|" + level.dimension().location();
    }

    private static long pack(int layer, int x, int z) {
        long value = ((long) layer & 0xFFL) << 56;
        value |= ((long) x & 0x0FFFFFFFL) << 28;
        return value | ((long) z & 0x0FFFFFFFL);
    }

    public record LeafSnapshot(int level, int leafX, int leafZ, int textureVersion, byte[] rgba) { }
}
