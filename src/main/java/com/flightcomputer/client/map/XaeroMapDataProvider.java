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
     * Prefetches the native 64x64 Xaero leaf map chunks around the Flight Controller.
     *
     * Important: the value passed in as mapLevel is a Flight Computer zoom/LOD concept, not a
     * Xaero MapProcessor coordinate. Xaero's MapProcessor#getMapChunk uses X, Z, cave-layer.
     * The Flight Computer therefore currently requests the real LOD-0 decoded map data only.
     */
    public void requestWorldArea(ClientLevel level, double centerX, double centerZ, double radiusBlocks,
                                 int mapLevel) {
        if (!ensure(level)) return;

        // Native Xaero leaf chunks are 64 world blocks wide at LOD 0 (4 Minecraft chunks).
        int nativeLevel = 0;
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
                    if (requestLeaf(leafX, leafZ, nativeLevel)) queued++;
                }
            }
        }
    }

    /**
     * Returns a copy of Xaero's exact decoded RGBA 64x64 leaf buffer.
     *
     * leafX/leafZ are MapChunk coordinates: one leaf is four Minecraft chunks (64 blocks).
     * They are NOT a map LOD and must never be passed as the cave-layer parameter.
     */
    public LeafSnapshot getLeaf(int mapLevel, int leafX, int leafZ) {
        if (processor == null) return null;

        try {
            int caveLayer = processor.getCurrentCaveLayer();
            MapTileChunk chunk = processor.getMapChunk(leafX, leafZ, caveLayer);
            if (chunk == null || !chunk.hasHadTerrain()) return null;

            LeafRegionTexture texture = chunk.getLeafTexture();
            if (texture == null || texture.isColorBufferCompressed()) return null;
            ByteBuffer source = texture.getDirectColorBuffer();
            if (source == null) return null;

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
        diagnostics = "Xaero adapter cleared.";
    }

    private boolean ensure(ClientLevel level) {
        if (level == null) return false;
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
            diagnostics = "Xaero native MapProcessor connected; using LOD 0 MapChunk coordinates.";
            return true;
        } catch (Throwable t) {
            diagnostics = "Xaero API connection failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Xaero API connection unavailable", t);
            return false;
        }
    }

    private boolean requestLeaf(int leafX, int leafZ, int ignoredLevel) {
        if (processor == null) return false;

        int caveLayer = processor.getCurrentCaveLayer();
        int regionX = Math.floorDiv(leafX, 8);
        int regionZ = Math.floorDiv(leafZ, 8);
        long key = pack(caveLayer, regionX, regionZ);
        if (requestedRegions.containsKey(key)) return false;

        try {
            // Xaero's native coordinate order is X, Z, cave layer, create/load.
            MapRegion region = processor.getLeafMapRegion(regionX, regionZ, caveLayer, true);
            if (region == null) return false;
            if (!region.hasHadTerrain() || !region.isLoaded()) {
                processor.getMapSaveLoad().requestLoad(region, "flightcomputer", false);
            }
            requestedRegions.put(key, region.getReloadVersion());
            return true;
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
