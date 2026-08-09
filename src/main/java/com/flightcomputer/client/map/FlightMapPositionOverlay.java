package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** Renders only Flight Computer-owned position indicators; Xaero owns waypoint rendering. */
public final class FlightMapPositionOverlay {
    public void render(GuiGraphics graphics, XaeroMapViewport.Snapshot view,
                       int mapLeft, int mapTop, int mapWidth, int mapHeight, BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !view.finite()) return;

        if (minecraft.player != null) {
            int x = view.worldToViewportX(minecraft.player.getX(), mapLeft, mapWidth);
            int y = view.worldToViewportY(minecraft.player.getZ(), mapTop, mapHeight);
            drawTriangle(graphics, x, y, 4, 0xFFFF3333);
        }

        if (controllerPos != null) {
            int x = view.worldToViewportX(controllerPos.getX() + 0.5D, mapLeft, mapWidth);
            int y = view.worldToViewportY(controllerPos.getZ() + 0.5D, mapTop, mapHeight);
            drawDiamond(graphics, x, y, 4, 0xFF66D9FF);
        }
    }

    private static void drawTriangle(GuiGraphics graphics, int x, int y, int radius, int color) {
        for (int row = 0; row <= radius; row++) {
            graphics.fill(x - row, y - radius + row, x + row + 1, y - radius + row + 1, color);
        }
    }

    private static void drawDiamond(GuiGraphics graphics, int x, int y, int radius, int color) {
        graphics.fill(x, y - radius, x + 1, y + radius + 1, color);
        for (int row = 1; row <= radius; row++) {
            graphics.fill(x - row, y - row, x + row + 1, y - row + 1, color);
            graphics.fill(x - row, y + row - 1, x + row + 1, y + row, color);
        }
    }
}
