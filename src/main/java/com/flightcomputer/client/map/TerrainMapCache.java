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
     * Prefetch both the requested LOD and LOD 0. LOD 0 is the reliable source of truth because
     * Xaero may not have generated a coarser LOD for every region yet. The GUI can therefore
     * continue rendering while higher-resolution aggregate data becomes available.
     */
    public static void requestViewport(ClientLevel level, double centerWorldX, double centerWorldZ,
                                       double radiusBlocks, double blocksPerPixel) {
        if (level == null) return;
        int mapLevel = chooseMapLevel(blocksPerPixel);
        XAERO_PROVIDER.requestWorldArea(level, centerWorldX, centerWorldZ, radiusBlocks, mapLevel);
        if (mapLevel != 0) {
            XAERO_PROVIDER.requestWorldArea(level, centerWorldX, centerWorldZ, radiusBlocks, 0);
        }
    }

    public static void tick(ClientLevel level) {
        XAERO_PROVIDER.tick(level);
    }

    /**
     * Reads the pixel corresponding to a world coordinate. Higher Xaero LODs are preferred,
     * but if that aggregate leaf is unavailable we immediately fall back to LOD 0. This is
     * important for worlds where Xaero has terrain at LOD 0 but has not produced/loaded the
     * corresponding aggregate LOD yet.
     */
    public static int colorAt(ClientLevel level, int worldX, int worldZ, double blocksPerPixel) {
        if (level == null) return 0;
        ensureIdentity(level);

        int requestedLevel = chooseMapLevel(blocksPerPixel);
        int color = colorAtLevel(requestedLevel, worldX, worldZ);
        if (color != 0 || requestedLevel == 0) return color;

        // Stable fallback: Xaero's LOD 0 coordinates are directly derived from world X/Z.
        return colorAtLevel(0, worldX, worldZ);
    }

    /** Backwards-compatible lookup at one block per map pixel. */
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

    private static int colorAtLevel(int mapLevel, int worldX, int worldZ) {
        int scale = 1 << mapLevel;
        int mapPixelX = Math.floorDiv(worldX, scale);
        int mapPixelZ = Math.floorDiv(worldZ, scale);

        int leafX = Math.floorDiv(mapPixelX, XaeroMapDataProvider.LEAF_PIXELS);
        int leafZ = Math.floorDiv(mapPixelZ, XaeroMapDataProvider.LEAF_PIXELS);
        long key = pack(mapLevel, leafX, leafZ);

        XaeroMapDataProvider.LeafSnapshot snapshot = LEAF_SNAPSHOTS.get(key);
        if (snapshot == null) {
            snapshot = XAERO_PROVIDER.getLeaf(mapLevel, leafX, leafZ);
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

    private static void ensureIdentity(ClientLevel level) {
        String identity = level.dimension().location().toString();
        if (!identity.equals(snapshotIdentity)) {
            LEAF_SNAPSHOTS.clear();
            snapshotIdentity = identity;
        }
    }

    private static int chooseMapLevel(double blocksPerPixel) {
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 1.0D) return 0;
        double log2 = Math.log(blocksPerPixel) / Math.log(2.0D);
        return Math.max(0, Math.min(8, (int) Math.round(log2)));
    }

    private static long pack(int level, int x, int z) {
        long value = ((long) level & 0xFFL) << 56;
        value |= ((long) x & 0x0FFFFFFFL) << 28;
        return value | ((long) z & 0x0FFFFFFFL);
    }
}
