package com.flightcomputer.client.map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * First-party Flight Computer map renderer.
 *
 * Xaero is a data source only. This renderer never creates, ticks or renders Xaero GUI state.
 */
public final class FlightMapRenderer {
    private static final int UNKNOWN = 0xFF101820;

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
            renderNavigationMarkers(graphics, font, level, viewport, tracker,
                    controllerPos, playerPos, left, top, right, bottom);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void renderTerrain(GuiGraphics graphics, ClientLevel level, FlightMapViewport viewport,
                                      FlightMapTracker tracker, int left, int top, int right, int bottom) {
        int centreX = (left + right) / 2;
        int centreY = (top + bottom) / 2;
        double scale = viewport.blocksPerPixel();

        // The old renderer stepped by four SCREEN pixels regardless of zoom. At the default
        // 4 blocks/pixel scale that sampled one point every 16 world blocks and produced the
        // sparse green squares seen in-game. Keep the renderer cheap, but always cover the
        // viewport continuously with adaptive screen-space blocks.
        int step = scale >= 16.0D ? 4 : scale >= 8.0D ? 3 : scale >= 3.0D ? 2 : 1;

        for (int sy = top; sy < bottom; sy += step) {
            double worldZ = viewport.centerZ() + ((sy + step * 0.5D) - centreY) * scale;
            for (int sx = left; sx < right; sx += step) {
                double worldX = viewport.centerX() + ((sx + step * 0.5D) - centreX) * scale;
                int wx = (int) Math.floor(worldX);
                int wz = (int) Math.floor(worldZ);

                int color = tracker.tracksBlock(wx, wz)
                        ? TerrainMapCache.cachedColorAt(level, wx, wz)
                        : 0;
                int drawColor = color == 0 ? UNKNOWN : color;
                graphics.fill(sx, sy, Math.min(sx + step, right), Math.min(sy + step, bottom), drawColor);
            }
        }
    }

    private static void renderGrid(GuiGraphics graphics, int left, int top, int right, int bottom, double scale) {
        int worldGrid = scale >= 16.0D ? 256 : scale >= 8.0D ? 128 : scale >= 4.0D ? 64 : 32;
        int spacing = Math.max(16, (int) Math.round(worldGrid / scale));
        for (int x = left; x <= right; x += spacing) graphics.vLine(x, top, bottom, 0x332F414A);
        for (int y = top; y <= bottom; y += spacing) graphics.hLine(left, right, y, 0x332F414A);
    }

    private static void renderNavigationMarkers(GuiGraphics graphics, Font font, ClientLevel level,
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
