package com.flightcomputer.client.map;

import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * First-party Flight Computer map renderer.
 *
 * This renderer knows nothing about Xaero GUI classes. Xaero is allowed to feed
 * normalized terrain into TerrainMapCache, but this class only consumes our data.
 */
public final class FlightMapRenderer {
    private static final int SAMPLE_PIXELS = 4;
    private static final int UNKNOWN = 0xFF101820;

    private FlightMapRenderer() { }

    public static void render(GuiGraphics graphics, Font font, ClientLevel level,
                              FlightMapViewport viewport, FlightMapTracker tracker,
                              int left, int top, int right, int bottom) {
        if (level == null || viewport == null || tracker == null) return;
        if (!level.dimension().location().equals(tracker.dimension())) return;

        graphics.enableScissor(left, top, right, bottom);
        try {
            graphics.fill(left, top, right, bottom, UNKNOWN);
            renderTerrain(graphics, level, viewport, tracker, left, top, right, bottom);
            renderGrid(graphics, left, top, right, bottom, viewport.blocksPerPixel());
            renderMarkers(graphics, font, level, viewport, tracker, left, top, right, bottom);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void renderTerrain(GuiGraphics graphics, ClientLevel level, FlightMapViewport viewport,
                                      FlightMapTracker tracker, int left, int top, int right, int bottom) {
        int centreX = (left + right) / 2;
        int centreY = (top + bottom) / 2;
        double scale = viewport.blocksPerPixel();

        for (int sy = top; sy < bottom; sy += SAMPLE_PIXELS) {
            double worldZ = viewport.centerZ() + (sy - centreY) * scale;
            for (int sx = left; sx < right; sx += SAMPLE_PIXELS) {
                double worldX = viewport.centerX() + (sx - centreX) * scale;
                int wx = (int) Math.floor(worldX);
                int wz = (int) Math.floor(worldZ);
                int color = tracker.tracksBlock(wx, wz)
                        ? TerrainMapCache.cachedColorAt(level, wx, wz)
                        : 0;
                graphics.fill(sx, sy,
                        Math.min(sx + SAMPLE_PIXELS, right),
                        Math.min(sy + SAMPLE_PIXELS, bottom),
                        color == 0 ? UNKNOWN : color);
            }
        }
    }

    private static void renderGrid(GuiGraphics graphics, int left, int top, int right, int bottom, double scale) {
        int worldGrid = scale >= 16.0D ? 256 : scale >= 8.0D ? 128 : scale >= 4.0D ? 64 : 32;
        int spacing = Math.max(16, (int) Math.round(worldGrid / scale));
        for (int x = left; x <= right; x += spacing) graphics.vLine(x, top, bottom, 0x332F414A);
        for (int y = top; y <= bottom; y += spacing) graphics.hLine(left, right, y, 0x332F414A);
    }

    private static void renderMarkers(GuiGraphics graphics, Font font, ClientLevel level,
                                      FlightMapViewport viewport, FlightMapTracker tracker,
                                      int left, int top, int right, int bottom) {
        int centreX = (left + right) / 2;
        int centreY = (top + bottom) / 2;
        double scale = viewport.blocksPerPixel();
        String dimension = level.dimension().location().toString();

        for (MapMarker marker : MarkerRegistry.all()) {
            if (!dimension.equals(marker.dimensionId()) || !MarkerRegistry.isVisible(marker.category())) continue;
            if (!tracker.tracksBlock(marker.x(), marker.z())) continue;

            int sx = centreX + (int) Math.round((marker.x() - viewport.centerX()) / scale);
            int sy = centreY + (int) Math.round((marker.z() - viewport.centerZ()) / scale);
            if (sx < left || sx > right || sy < top || sy > bottom) continue;

            int color = 0xFF000000 | marker.category().getColor();
            graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, color);
            graphics.drawString(font, marker.name(), sx + 7, sy - 4, 0xFFFFFFFF);
        }
    }
}
