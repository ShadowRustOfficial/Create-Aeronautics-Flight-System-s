package com.flightcomputer.client.map;

import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** Owns Flight Computer markers drawn above Xaero's terrain. */
public final class FlightMapOverlayManager {
    private static final String PLAYER_ID = "flightcomputer:player";
    private static final String CONTROLLER_ID = "flightcomputer:controller";

    public void refreshDynamicMarkers(BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        String dimension = minecraft.level.dimension().location().toString();

        if (minecraft.player != null) {
            MarkerRegistry.put(new MapMarker(
                    PLAYER_ID, "", MarkerCategory.FLIGHT_WAYPOINT,
                    (int) Math.floor(minecraft.player.getX()), (int) Math.floor(minecraft.player.getY()),
                    (int) Math.floor(minecraft.player.getZ()), dimension));
        }

        if (controllerPos != null) {
            MarkerRegistry.put(new MapMarker(
                    CONTROLLER_ID, "", MarkerCategory.CLAIMED_SUBLEVEL,
                    controllerPos.getX(), controllerPos.getY(), controllerPos.getZ(), dimension));
        }
    }

    public void render(GuiGraphics graphics, XaeroMapViewport.Snapshot view,
                       int mapLeft, int mapTop, int mapWidth, int mapHeight,
                       BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !view.finite()) return;
        String dimension = minecraft.level.dimension().location().toString();

        for (MapMarker marker : MarkerRegistry.all()) {
            if (!dimension.equals(marker.dimensionId())) continue;
            if (!MarkerRegistry.isVisible(marker.category())) continue;

            int x = view.worldToViewportX(marker.x() + 0.5D, mapLeft, mapWidth);
            int y = view.worldToViewportY(marker.z() + 0.5D, mapTop, mapHeight);
            double radiusWorld = marker.category() == MarkerCategory.FLIGHT_WAYPOINT ? 2.0D : 2.5D;
            int radius = (int) Math.round(radiusWorld * view.pixelsPerBlock());
            radius = Math.max(2, Math.min(7, radius));

            if (x < mapLeft - 12 || x > mapLeft + mapWidth + 12
                    || y < mapTop - 12 || y > mapTop + mapHeight + 12) continue;

            int color = 0xFF000000 | marker.category().getColor();
            switch (marker.category()) {
                case FLIGHT_WAYPOINT -> drawTriangle(graphics, x, y, radius, color);
                case CLAIMED_SUBLEVEL -> drawDiamond(graphics, x, y, radius, color);
                case XAERO_WAYPOINT -> drawCircle(graphics, x, y, radius, color);
                case WAYSTONE -> drawSquare(graphics, x, y, radius, color);
                case LANDING_PAD -> drawCross(graphics, x, y, radius, color);
            }

            if (!marker.name().isBlank() && view.pixelsPerBlock() >= 0.08D) {
                graphics.drawString(Minecraft.getInstance().font, marker.name(),
                        x + radius + 3, y - 4, 0xFFFFFFFF);
            }
        }

        // Dynamic player/controller markers are kept separate from Xaero's native player element.
        // Their positions are derived from the same live Xaero camera transform as every other marker.
        if (minecraft.player != null && MarkerRegistry.isVisible(MarkerCategory.FLIGHT_WAYPOINT)) {
            int x = view.worldToViewportX(minecraft.player.getX(), mapLeft, mapWidth);
            int y = view.worldToViewportY(minecraft.player.getZ(), mapTop, mapHeight);
            int radius = scaledMarkerRadius(view, 3.0D);
            drawTriangle(graphics, x, y, radius, 0xFFFF3333);
        }

        if (controllerPos != null && MarkerRegistry.isVisible(MarkerCategory.CLAIMED_SUBLEVEL)) {
            int x = view.worldToViewportX(controllerPos.getX() + 0.5D, mapLeft, mapWidth);
            int y = view.worldToViewportY(controllerPos.getZ() + 0.5D, mapTop, mapHeight);
            int radius = scaledMarkerRadius(view, 3.0D);
            drawDiamond(graphics, x, y, radius, 0xFF66D9FF);
        }
    }

    private static int scaledMarkerRadius(XaeroMapViewport.Snapshot view, double worldRadius) {
        return Math.max(2, Math.min(7, (int) Math.round(worldRadius * view.pixelsPerBlock())));
    }

    private static void drawTriangle(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x, y - r, x + 1, y + r + 1, color);
        g.fill(x - r, y + r - 1, x + r + 1, y + r + 1, color);
        for (int i = 1; i < r; i++) g.fill(x - i, y + r - i, x + i + 1, y + r - i + 1, color);
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

    private static void drawSquare(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y - r, x + r + 1, y + r + 1, color);
    }

    private static void drawCross(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y - 1, x + r + 1, y + 2, color);
        g.fill(x - 1, y - r, x + 2, y + r + 1, color);
    }
}
