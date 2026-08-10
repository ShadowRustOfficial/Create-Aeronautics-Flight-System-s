package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coordinates Flight Computer's view of Xaero data.
 *
 * This class intentionally does not parse files, sample Minecraft chunks, or build a second map
 * database. Xaero owns decoding and its own asynchronous cache loading; FlightMapTextureCache
 * owns the GPU cache used by the independent GUI renderer.
 */
public final class TerrainMapCache {
    private static final XaeroMapDataProvider XAERO_PROVIDER = new XaeroMapDataProvider();

    /** Small CPU-side compatibility cache for the lightweight FlightMapScreen pixel lookup. */
    private static final int MAX_LEAF_SNAPSHOTS = 96;
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
     * Prefetch the exact map LOD needed by the current viewport. This is deliberately done once
     * per tick instead of issuing a request from every terrain pixel drawn by the GUI.
     */
    public static void requestViewport(ClientLevel level, double centerWorldX, double centerWorldZ,
                                       double radiusBlocks, double blocksPerPixel) {
        if (level == null) return;
        int mapLevel = chooseMapLevel(blocksPerPixel);
        XAERO_PROVIDER.requestWorldArea(level, centerWorldX, centerWorldZ, radiusBlocks, mapLevel);
    }

    public static void tick(ClientLevel level) {
        XAERO_PROVIDER.tick(level);
    }

    /**
     * Returns the colour of the Xaero map pixel corresponding to a world coordinate at the
     * requested screen scale.
     *
     * Important: Xaero's leaf coordinates are MAP-PIXEL coordinates, not world-block coordinates.
     * The old implementation treated world X/Z as leaf coordinates at LOD 0. That happened to
     * expose pieces of terrain at some zoom levels but caused the map to disappear or become
     * fragmented as the zoom changed. We now convert world coordinates into the selected Xaero
     * LOD before resolving the leaf and local pixel.
     */
    public static int colorAt(ClientLevel level, int worldX, int worldZ, double blocksPerPixel) {
        if (level == null) return 0;
        ensureIdentity(level);

        int mapLevel = chooseMapLevel(blocksPerPixel);
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

    /** Backwards-compatible convenience lookup at one block per map pixel. */
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
