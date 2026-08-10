package com.flightcomputer.client.map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * First-party Flight Computer map renderer.
 *
 * Xaero is used strictly as a decoded terrain provider. Its GUI, camera, mouse state, zoom state
 * and render methods are never invoked. Terrain is rendered from cached 64x64 Xaero leaf textures.
 */
public final class FlightMapRenderer {
    private static final int UNKNOWN = 0xFF101820;
    private static final FlightMapTextureCache TEXTURES = new FlightMapTextureCache();

    private FlightMapRenderer() { }

    public static void render(GuiGraphics graphics, Font font, ClientLevel level,
                              FlightMapViewport viewport, FlightMapTracker tracker,
                              BlockPos controllerPos, Vec3 playerPos,
                              int left, int top, int right, int bottom) {
        if (level == null || viewport == null || tracker == null) return;
        if (!level.dimension().location().equals(tracker.dimension())) return;

        graphics.enableScissor(left, top, right, bottom);
        try {
            graphics.fill(left, top, right, bottom, UNKNOWN);
            renderTerrain(graphics, level, viewport, tracker, left, top, right, bottom);
            renderGrid(graphics, left, top, right, bottom, viewport.blocksPerPixel());
            renderNavigationMarkers(graphics, font, viewport, tracker,
                    controllerPos, playerPos, left, top, right, bottom);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void renderTerrain(GuiGraphics graphics, ClientLevel level, FlightMapViewport viewport,
                                      FlightMapTracker tracker, int left, int top, int right, int bottom) {
        // Xaero's native API gives us a real 64x64 LOD-0 leaf, representing 64x64 world blocks.
        // Zoom is a screen-space scale only. Do not reinterpret zoom as MapProcessor coordinates.
        final int mapLevel = 0;
        final double blocksPerPixel = viewport.blocksPerPixel();
        final double leafWorldSize = XaeroMapDataProvider.LEAF_PIXELS;

        String identity = level.dimension().location() + "|native-lod0";
        TEXTURES.beginFrame(identity);

        double mapCentreX = (left + right) / 2.0D;
        double mapCentreY = (top + bottom) / 2.0D;
        double minWorldX = viewport.centerX() + (left - mapCentreX) * blocksPerPixel;
        double maxWorldX = viewport.centerX() + (right - mapCentreX) * blocksPerPixel;
        double minWorldZ = viewport.centerZ() + (top - mapCentreY) * blocksPerPixel;
        double maxWorldZ = viewport.centerZ() + (bottom - mapCentreY) * blocksPerPixel;

        int minLeafX = Math.floorDiv((int) Math.floor(minWorldX), XaeroMapDataProvider.LEAF_PIXELS);
        int maxLeafX = Math.floorDiv((int) Math.floor(maxWorldX), XaeroMapDataProvider.LEAF_PIXELS);
        int minLeafZ = Math.floorDiv((int) Math.floor(minWorldZ), XaeroMapDataProvider.LEAF_PIXELS);
        int maxLeafZ = Math.floorDiv((int) Math.floor(maxWorldZ), XaeroMapDataProvider.LEAF_PIXELS);

        XaeroMapDataProvider provider = TerrainMapCache.provider();
        int centreX = (left + right) / 2;
        int centreY = (top + bottom) / 2;
        int maxTracked = tracker.radiusBlocks();

        for (int leafZ = minLeafZ; leafZ <= maxLeafZ; leafZ++) {
            for (int leafX = minLeafX; leafX <= maxLeafX; leafX++) {
                double leafWorldX = leafX * leafWorldSize;
                double leafWorldZ = leafZ * leafWorldSize;
                double leafCentreX = leafWorldX + leafWorldSize * 0.5D;
                double leafCentreZ = leafWorldZ + leafWorldSize * 0.5D;
                if (outsideTrack(leafCentreX, leafCentreZ, tracker.controllerPos(), maxTracked)) continue;

                int sx = centreX + (int) Math.floor((leafWorldX - viewport.centerX()) / blocksPerPixel);
                int sy = centreY + (int) Math.floor((leafWorldZ - viewport.centerZ()) / blocksPerPixel);
                int sw = Math.max(1, (int) Math.ceil(leafWorldSize / blocksPerPixel));
                int sh = sw;
                TEXTURES.drawLeaf(graphics, provider, mapLevel, leafX, leafZ, sx, sy, sw, sh);
            }
        }
    }

    private static boolean outsideTrack(double x, double z, BlockPos anchor, int radius) {
        if (anchor == null) return true;
        double dx = x - (anchor.getX() + 0.5D);
        double dz = z - (anchor.getZ() + 0.5D);
        return dx * dx + dz * dz > (double) radius * radius;
    }

    private static void renderGrid(GuiGraphics graphics, int left, int top, int right, int bottom, double scale) {
        int worldGrid = scale >= 16.0D ? 256 : scale >= 8.0D ? 128 : scale >= 4.0D ? 64 : 32;
        int spacing = Math.max(16, (int) Math.round(worldGrid / scale));
        for (int x = left; x <= right; x += spacing) graphics.vLine(x, top, bottom, 0x332F414A);
        for (int y = top; y <= bottom; y += spacing) graphics.hLine(left, right, y, 0x332F414A);
    }

    private static void renderNavigationMarkers(GuiGraphics graphics, Font font,
                                                FlightMapViewport viewport, FlightMapTracker tracker,
                                                BlockPos controllerPos, Vec3 playerPos,
                                                int left, int top, int right, int bottom) {
        if (controllerPos != null) {
            renderMarker(graphics, font, viewport, tracker,
                    controllerPos.getX() + 0.5D, controllerPos.getZ() + 0.5D,
                    "▲ CONTROLLER", 0xFFFFFFFF, left, top, right, bottom);
        }
        if (playerPos != null) {
            renderMarker(graphics, font, viewport, tracker,
                    playerPos.x, playerPos.z,
                    "● PLAYER", 0xFF55FFFF, left, top, right, bottom);
        }
    }

    private static void renderMarker(GuiGraphics graphics, Font font, FlightMapViewport viewport,
                                     FlightMapTracker tracker, double worldX, double worldZ,
                                     String label, int color, int left, int top, int right, int bottom) {
        if (!tracker.tracksBlock((int) Math.floor(worldX), (int) Math.floor(worldZ))) return;
        int centreX = (left + right) / 2;
        int centreY = (top + bottom) / 2;
        double scale = viewport.blocksPerPixel();
        int sx = centreX + (int) Math.round((worldX - viewport.centerX()) / scale);
        int sy = centreY + (int) Math.round((worldZ - viewport.centerZ()) / scale);
        if (sx < left - 80 || sx > right + 80 || sy < top - 20 || sy > bottom + 20) return;
        graphics.fill(sx - 4, sy - 4, sx + 5, sy + 5, color);
        graphics.drawString(font, label, sx + 10, sy - 5, color);
    }
}
