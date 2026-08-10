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

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** Xaero-backed implementation of the renderer-neutral terrain provider contract. */
public final class XaeroMapDataProvider implements TerrainProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LEAF_PIXELS = 64;
    private static final int MAX_LOAD_REQUESTS_PER_TICK = 12;
    private static final int REGION_RETRY_TICKS = 10;

    private final Map<Long, Long> requestedRegions = new HashMap<>();
    private long tickCounter;
    private String identity;
    private MapProcessor processor;
    private String diagnostics = "Xaero adapter not initialized.";
    private TerrainProviderState state = TerrainProviderState.OFFLINE;
    private long requestedRegionCount;
    private long loadedRegionCount;
    private long decodedLeafCount;
    private long renderedSamples;
    private long failedSamples;

    /* Xaero's normal map-processing path is driven by its render processing. */
    private Method renderProcessMethod;
    private boolean renderProcessMethodResolved;

    @Override
    public String id() { return "xaero-native"; }

    @Override
    public void tick(ClientLevel level) {
        tickCounter++;
        if (!ensure(level)) return;
        pumpXaeroDecoder();
    }

    public boolean available(ClientLevel level) {
        ensure(level);
        return processor != null;
    }

    @Override
    public void request(TerrainViewport viewport, ClientLevel level) {
        if (level == null || viewport == null || !ensure(level)) return;
        requestWorldArea(level, viewport.centerX(), viewport.centerZ(), viewport.radiusBlocks(), 0);
    }

    /** Compatibility entry point retained for existing callers. */
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

        state = TerrainProviderState.LOADING;
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

    @Override
    public int sampleColor(ClientLevel level, int worldX, int worldZ) {
        if (level == null || processor == null) {
            failedSamples++;
            return 0;
        }
        int leafX = Math.floorDiv(worldX, LEAF_PIXELS);
        int leafZ = Math.floorDiv(worldZ, LEAF_PIXELS);
        LeafSnapshot snapshot = getLeaf(0, leafX, leafZ);
        if (snapshot == null) {
            failedSamples++;
            return 0;
        }
        int localX = Math.floorMod(worldX, LEAF_PIXELS);
        int localZ = Math.floorMod(worldZ, LEAF_PIXELS);
        int index = (localZ * LEAF_PIXELS + localX) * 4;
        byte[] rgba = snapshot.rgba();
        if (index < 0 || index + 3 >= rgba.length) {
            failedSamples++;
            return 0;
        }
        renderedSamples++;
        int r = rgba[index] & 0xFF;
        int g = rgba[index + 1] & 0xFF;
        int b = rgba[index + 2] & 0xFF;
        int a = rgba[index + 3] & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Returns a copy of Xaero's decoded RGBA 64x64 leaf buffer. */
    public LeafSnapshot getLeaf(int ignoredMapLevel, int leafX, int leafZ) {
        if (processor == null) return null;
        try {
            int caveLayer = processor.getCurrentCaveLayer();
            MapTileChunk chunk = processor.getMapChunk(leafX, leafZ, caveLayer);
            if (chunk == null) {
                diagnostics = "MapChunk unavailable at " + leafX + "," + leafZ + " layer " + caveLayer;
                state = TerrainProviderState.LOADING;
                requestLeaf(leafX, leafZ);
                return null;
            }
            if (!chunk.hasHadTerrain()) {
                diagnostics = "MapChunk exists but terrain is not decoded yet at " + leafX + "," + leafZ;
                state = TerrainProviderState.LOADING;
                requestLeaf(leafX, leafZ);
                return null;
            }
            MapRegion region = processor.getLeafMapRegion(Math.floorDiv(leafX, 8), Math.floorDiv(leafZ, 8), caveLayer, true);
            if (region != null && region.isLoaded()) loadedRegionCount++;

            LeafRegionTexture texture = chunk.getLeafTexture();
            if (texture == null) {
                diagnostics = "MapChunk has terrain but no LeafRegionTexture at " + leafX + "," + leafZ;
                state = TerrainProviderState.DEGRADED;
                return null;
            }
            if (texture.isColorBufferCompressed()) {
                diagnostics = "Leaf colour buffer is compressed at " + leafX + "," + leafZ;
                state = TerrainProviderState.DEGRADED;
                return null;
            }
            ByteBuffer source = texture.getDirectColorBuffer();
            if (source == null) {
                diagnostics = "Leaf has no direct colour buffer at " + leafX + "," + leafZ;
                state = TerrainProviderState.LOADING;
                return null;
            }
            int expected = LEAF_PIXELS * LEAF_PIXELS * 4;
            if (source.capacity() < expected) {
                diagnostics = "Leaf colour buffer is " + source.capacity() + " bytes; expected " + expected;
                state = TerrainProviderState.ERROR;
                return null;
            }
            ByteBuffer copy = source.duplicate();
            copy.position(0);
            copy.limit(expected);
            byte[] rgba = new byte[expected];
            copy.get(rgba);
            decodedLeafCount++;
            state = TerrainProviderState.READY;
            diagnostics = "Decoded native Xaero leaf " + leafX + "," + leafZ;
            return new LeafSnapshot(0, leafX, leafZ, texture.getTextureVersion(), rgba);
        } catch (Throwable t) {
            state = TerrainProviderState.ERROR;
            diagnostics = "Leaf read failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Failed to read Xaero native leaf {},{}", leafX, leafZ, t);
            return null;
        }
    }

    @Override
    public TerrainProviderDiagnostics diagnostics(ClientLevel level) {
        String dimension = level == null ? "unknown" : level.dimension().location().toString();
        return new TerrainProviderDiagnostics(state, id(), diagnostics, dimension,
                requestedRegionCount, loadedRegionCount, decodedLeafCount, 0,
                renderedSamples, failedSamples);
    }

    public String xaeroDiagnostics() { return diagnostics; }

    @Override
    public void clear() {
        requestedRegions.clear();
        processor = null;
        identity = null;
        tickCounter = 0;
        requestedRegionCount = 0;
        loadedRegionCount = 0;
        decodedLeafCount = 0;
        renderedSamples = 0;
        failedSamples = 0;
        renderProcessMethod = null;
        renderProcessMethodResolved = false;
        state = TerrainProviderState.OFFLINE;
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
        renderProcessMethod = null;
        renderProcessMethodResolved = false;
        state = TerrainProviderState.INITIALIZING;

        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) {
                state = TerrainProviderState.OFFLINE;
                diagnostics = "Xaero World Map session is not usable yet.";
                return false;
            }
            MapProcessor candidate = session.getMapProcessor();
            if (candidate == null || candidate.getWorld() != level) {
                state = TerrainProviderState.DEGRADED;
                diagnostics = "Xaero session is not attached to the current ClientLevel.";
                return false;
            }
            if (!candidate.isMapWorldUsable()) {
                state = TerrainProviderState.DEGRADED;
                diagnostics = "Xaero MapProcessor reports map world is not usable yet.";
                return false;
            }
            processor = candidate;
            state = TerrainProviderState.LOADING;
            diagnostics = "Xaero MapProcessor connected; requesting native terrain.";
            return true;
        } catch (Throwable t) {
            state = TerrainProviderState.ERROR;
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
                state = TerrainProviderState.DEGRADED;
                diagnostics = "Xaero region unavailable at " + regionX + "," + regionZ + " layer " + caveLayer;
                return false;
            }
            if (!region.isLoaded() || !region.hasHadTerrain()) {
                processor.getMapSaveLoad().requestLoad(region, "flightcomputer", false);
                requestedRegions.put(key, tickCounter);
                requestedRegionCount++;
                state = TerrainProviderState.LOADING;
                diagnostics = "Requested Xaero region " + regionX + "," + regionZ + " layer " + caveLayer;
                return true;
            }
            loadedRegionCount++;
            requestedRegions.put(key, tickCounter);
            return false;
        } catch (Throwable t) {
            state = TerrainProviderState.ERROR;
            diagnostics = "Xaero region request failed: " + t.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Failed to request Xaero region {},{} layer {}", regionX, regionZ, caveLayer, t);
            return false;
        }
    }

    private void pumpXaeroDecoder() {
        if (processor == null || requestedRegions.isEmpty()) return;
        try {
            if (!renderProcessMethodResolved) {
                renderProcessMethodResolved = true;
                Method method = findZeroArgMethod(processor.getClass(), "onRenderProcess");
                if (method != null) {
                    method.setAccessible(true);
                    renderProcessMethod = method;
                } else {
                    state = TerrainProviderState.DEGRADED;
                    diagnostics = "Xaero decoder pump unavailable: onRenderProcess() not found.";
                    return;
                }
            }
            if (renderProcessMethod == null) return;
            renderProcessMethod.invoke(processor);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            state = TerrainProviderState.ERROR;
            diagnostics = "Xaero decoder pump failed: " + cause.getClass().getSimpleName();
            LOGGER.debug("[FlightComputer] Xaero decoder pump failed", cause);
        }
    }

    private static Method findZeroArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
            }
        }
        return null;
    }

    private static String buildIdentity(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
                : "singleplayer:" + (mc.getSingleplayerServer() == null ? "unknown"
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
