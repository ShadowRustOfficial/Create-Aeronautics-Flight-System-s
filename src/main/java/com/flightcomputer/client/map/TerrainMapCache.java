package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Coordinates Flight Computer's view of Xaero data.
 *
 * This class intentionally does not parse files, sample Minecraft chunks, or build a second map
 * database. Xaero owns decoding and its own asynchronous cache loading; FlightMapTextureCache
 * owns the small GPU cache used by our independent GUI renderer.
 */
public final class TerrainMapCache {
    private static final XaeroMapDataProvider XAERO_PROVIDER = new XaeroMapDataProvider();

    private TerrainMapCache() { }

    public static void requestViewport(ClientLevel level, int centerWorldX, int centerWorldZ,
                                       int radiusBlocks, double blocksPerPixel) {
        if (level == null) return;
        int mapLevel = chooseMapLevel(blocksPerPixel);
        XAERO_PROVIDER.requestWorldArea(level, centerWorldX, centerWorldZ, radiusBlocks, mapLevel);
    }

    public static void tick(ClientLevel level) {
        XAERO_PROVIDER.tick(level);
    }

    public static XaeroMapDataProvider provider() {
        return XAERO_PROVIDER;
    }

    public static String xaeroDiagnostics() {
        return XAERO_PROVIDER.diagnostics();
    }

    public static void clear() {
        XAERO_PROVIDER.clear();
    }

    private static int chooseMapLevel(double blocksPerPixel) {
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 1.0D) return 0;
        double log2 = Math.log(blocksPerPixel) / Math.log(2.0D);
        return Math.max(0, Math.min(8, (int) Math.round(log2)));
    }
}
