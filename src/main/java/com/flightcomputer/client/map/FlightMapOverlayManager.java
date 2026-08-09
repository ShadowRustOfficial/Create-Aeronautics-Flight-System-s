package com.flightcomputer.client.map;

import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** Owns Flight Computer overlays drawn above Xaero's terrain. Xaero owns Xaero waypoint rendering. */
public final class FlightMapOverlayManager {
    public void render(GuiGraphics graphics, XaeroMapViewport.Snapshot view,
                       int mapLeft, int mapTop, int mapWidth, int mapHeight,
                       BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !view.finite()) return;
        String dimension = minecraft.level.dimension().location().toString();

        for (MapMarker marker : MarkerRegistry.all()) {
            // Xaero's native GuiMap already renders its own waypoint elements. Never draw a
            // second copy from the parsed waypoint file; the file remains available as a
            // data source for route selection on the ROUTE tab.
            if (marker.category() == MarkerCategory.XAERO_WAYPOINT) continue;
            if (!dimension.equals(marker.dimensionId())) continue;
            if (!MarkerRegistry.isVisible(marker.category())) continue;

            int x = view.worldToViewportX(marker.x() + 0.5D, mapLeft, mapWidth);
            int y = view.worldToViewportY(marker.z() + 0.5D, mapTop, mapHeight);
            int radius = scaledMarkerRadius(view,
                    marker.category() == MarkerCategory.FLIGHT_WAYPOINT ? 2.0D : 2.5D);

            if (x < mapLeft - 12 || x > mapLeft + mapWidth + 12
                    || y < mapTop - 12 || y > mapTop + mapHeight + 12) continue;

            int color = 0xFF000000 | marker.category().getColor();
            switch (marker.category()) {
                case FLIGHT_WAYPOINT -> drawCircle(graphics, x, y, radius, color);
                case CLAIMED_SUBLEVEL -> drawDiamond(graphics, x, y, radius, color);
                case XAERO_WAYPOINT -> { /* Native Xaero renderer owns this layer. */ }
                case LANDING_PAD -> drawCross(graphics, x, y, radius, color);
            }

            if (!marker.name().isBlank() && view.pixelsPerBlock() >= 0.08D) {
                graphics.drawString(minecraft.font, marker.name(), x + radius + 3, y - 4, 0xFFFFFFFF);
            }
        }

        // Flight Computer-owned player marker. It is not stored as a waypoint category.
        if (minecraft.player != null) {
            int x = view.worldToViewportX(minecraft.player.getX(), mapLeft, mapWidth);
            int y = view.worldToViewportY(minecraft.player.getZ(), mapTop, mapHeight);
            drawTriangle(graphics, x, y, scaledMarkerRadius(view, 3.0D), 0xFFFF3333);
        }

        // Flight Computer/controller marker. It is intentionally separate from the claim source layer.
        if (controllerPos != null) {
            int x = view.worldToViewportX(controllerPos.getX() + 0.5D, mapLeft, mapWidth);
            int y = view.worldToViewportY(controllerPos.getZ() + 0.5D, mapTop, mapHeight);
            drawDiamond(graphics, x, y, scaledMarkerRadius(view, 3.0D), 0xFF66D9FF);
        }
    }

    private static int scaledMarkerRadius(XaeroMapViewport.Snapshot view, double worldRadius) {
        return Math.max(2, Math.min(7, (int) Math.round(worldRadius * view.pixelsPerBlock())));
    }

    private static void drawTriangle(GuiGraphics g, int x, int y, int r, int color) {
        for (int i = 0; i <= r; i++) {
            g.fill(x - i, y - r + i, x + i + 1, y - r + i + 1, color);
        }
    }

    private static void drawDiamond(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x, y - r, x + 1, y + r + 1, color);
        for (int i = 1; i <= r; i++) {
            g.fill(x - i, y - i, x + i + 1, y - i + 1, color);
            g.fill(x - i, y + i - 1, x + i + 1, y + i, color);
        }
    }

    private static void drawCircle(GuiGraphics g, int x, int y, int r, int color) {
        for (int yy = -r; yy <= r; yy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - yy * yy));
            g.fill(x - half, y + yy, x + half + 1, y + yy + 1, color);
        }
    }

    private static void drawCross(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y - 1, x + r + 1, y + 2, color);
        g.fill(x - 1, y - r, x + 2, y + r + 1, color);
    }
}
