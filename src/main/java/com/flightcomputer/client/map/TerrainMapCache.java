package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coordinates Flight Computer's independent map renderer with Xaero's already-decoded map state.
 * The cache never parses .xwmc files itself.
 */
public final class TerrainMapCache {
    private static final XaeroMapDataProvider XAERO_PROVIDER = new XaeroMapDataProvider();
    private static final int MAX_LEAF_SNAPSHOTS = 192;
    private static final Map<Long, XaeroMapDataProvider.LeafSnapshot> LEAF_SNAPSHOTS =
            new LinkedHashMap<>(MAX_LEAF_SNAPSHOTS, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, XaeroMapDataProvider.LeafSnapshot> eldest) {
                    return size() > MAX_LEAF_SNAPSHOTS;
                }
            };

    private static String snapshotIdentity;

    private TerrainMapCache() { }

    /**
     * Prefetches the real native Xaero LOD-0 map chunks. We deliberately do not invent a second
     * LOD coordinate system here: Xaero's MapProcessor API exposes its decoded 64x64 leaf through
     * MapChunk X/Z plus cave layer. The Flight Computer renderer handles zoom by scaling these
     * native leaves on screen.
     */
    public static void requestViewport(ClientLevel level, double centerWorldX, double centerWorldZ,
                                       double radiusBlocks, double blocksPerPixel) {
        if (level == null) return;
        XAERO_PROVIDER.requestWorldArea(level, centerWorldX, centerWorldZ, radiusBlocks, 0);
    }

    public static void tick(ClientLevel level) {
        XAERO_PROVIDER.tick(level);
    }

    /** Reads the pixel corresponding to a world coordinate from Xaero's native LOD-0 leaf. */
    public static int colorAt(ClientLevel level, int worldX, int worldZ, double blocksPerPixel) {
        if (level == null) return 0;
        ensureIdentity(level);

        // blocksPerPixel is a GUI rendering scale, not a Xaero MapProcessor LOD.
        int mapPixelX = worldX;
        int mapPixelZ = worldZ;
        int leafX = Math.floorDiv(mapPixelX, XaeroMapDataProvider.LEAF_PIXELS);
        int leafZ = Math.floorDiv(mapPixelZ, XaeroMapDataProvider.LEAF_PIXELS);
        long key = pack(leafX, leafZ);

        XaeroMapDataProvider.LeafSnapshot snapshot = LEAF_SNAPSHOTS.get(key);
        if (snapshot == null) {
            snapshot = XAERO_PROVIDER.getLeaf(0, leafX, leafZ);
            if (snapshot != null) LEAF_SNAPSHOTS.put(key, snapshot);
        }
        if (snapshot == null) return 0;

        int localX = Math.floorMod(mapPixelX, XaeroMapDataProvider.LEAF_PIXELS);
        int localZ = Math.floorMod(mapPixelZ, XaeroMapDataProvider.LEAF_PIXELS);
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

    public static XaeroMapDataProvider provider() {
        return XAERO_PROVIDER;
    }

    public static String xaeroDiagnostics() {
        return XAERO_PROVIDER.diagnostics();
    }

    public static void clear() {
        LEAF_SNAPSHOTS.clear();
        snapshotIdentity = null;
        XAERO_PROVIDER.clear();
    }

    private static void ensureIdentity(ClientLevel level) {
        String identity = level.dimension().location().toString();
        if (!identity.equals(snapshotIdentity)) {
            LEAF_SNAPSHOTS.clear();
            snapshotIdentity = identity;
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
