package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First-party terrain cache. The renderer sees this cache/provider boundary and never sees Xaero.
 * Disk decoding and region scheduling belong to the provider; immutable leaf snapshots live here.
 */
public final class TerrainMapCache {
    private static final TerrainProvider PROVIDER = new XaeroMapDataProvider();
    private static final int MAX_LEAF_SNAPSHOTS = 192;
    private static final Map<Long, XaeroMapDataProvider.LeafSnapshot> LEAF_SNAPSHOTS =
            new LinkedHashMap<>(MAX_LEAF_SNAPSHOTS, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, XaeroMapDataProvider.LeafSnapshot> eldest) {
                    return size() > MAX_LEAF_SNAPSHOTS;
                }
            };

    private static String snapshotIdentity;
    private static long cacheMisses;
    private static long cacheHits;

    private TerrainMapCache() { }

    public static void requestViewport(ClientLevel level, double centerWorldX, double centerWorldZ,
                                       double radiusBlocks, double blocksPerPixel) {
        if (level == null) return;
        PROVIDER.request(new TerrainViewport(centerWorldX, centerWorldZ, radiusBlocks, blocksPerPixel), level);
    }

    public static void tick(ClientLevel level) {
        PROVIDER.tick(level);
    }

    /** Renderer-facing sampling API. No Xaero types cross this boundary. */
    public static int colorAt(ClientLevel level, int worldX, int worldZ, double blocksPerPixel) {
        if (level == null) return 0;
        ensureIdentity(level);

        int leafX = Math.floorDiv(worldX, XaeroMapDataProvider.LEAF_PIXELS);
        int leafZ = Math.floorDiv(worldZ, XaeroMapDataProvider.LEAF_PIXELS);
        long key = pack(leafX, leafZ);
        XaeroMapDataProvider.LeafSnapshot snapshot = LEAF_SNAPSHOTS.get(key);
        if (snapshot == null) {
            cacheMisses++;
            if (PROVIDER instanceof XaeroMapDataProvider xaero) {
                snapshot = xaero.getLeaf(0, leafX, leafZ);
            }
            if (snapshot != null) LEAF_SNAPSHOTS.put(key, snapshot);
        } else {
            cacheHits++;
        }
        if (snapshot == null) return 0;

        int localX = Math.floorMod(worldX, XaeroMapDataProvider.LEAF_PIXELS);
        int localZ = Math.floorMod(worldZ, XaeroMapDataProvider.LEAF_PIXELS);
        int index = (localZ * XaeroMapDataProvider.LEAF_PIXELS + localX) * 4;
        byte[] rgba = snapshot.rgba();
        if (index < 0 || index + 3 >= rgba.length) return 0;
        int r = rgba[index] & 0xFF;
        int g = rgba[index + 1] & 0xFF;
        int b = rgba[index + 2] & 0xFF;
        int a = rgba[index + 3] & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int colorAt(ClientLevel level, int worldX, int worldZ) {
        return colorAt(level, worldX, worldZ, 1.0D);
    }

    public static TerrainProvider provider() { return PROVIDER; }

    public static TerrainProviderDiagnostics diagnostics(ClientLevel level) {
        TerrainProviderDiagnostics base = PROVIDER.diagnostics(level);
        return new TerrainProviderDiagnostics(base.state(), base.provider(), base.message(), base.dimension(),
                base.requestedRegions(), base.loadedRegions(), base.decodedLeaves(), LEAF_SNAPSHOTS.size(),
                base.renderedSamples(), base.failedSamples());
    }

    public static String xaeroDiagnostics() { return PROVIDER.diagnostics(null).message(); }

    public static int cachedLeafCount() { return LEAF_SNAPSHOTS.size(); }
    public static long cacheHits() { return cacheHits; }
    public static long cacheMisses() { return cacheMisses; }

    public static void clear() {
        LEAF_SNAPSHOTS.clear();
        snapshotIdentity = null;
        cacheHits = 0;
        cacheMisses = 0;
        PROVIDER.clear();
    }

    private static void ensureIdentity(ClientLevel level) {
        String identity = level.dimension().location().toString();
        if (!identity.equals(snapshotIdentity)) {
            LEAF_SNAPSHOTS.clear();
            cacheHits = 0;
            cacheMisses = 0;
            snapshotIdentity = identity;
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
