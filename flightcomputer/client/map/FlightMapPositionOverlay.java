package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** Renders Flight Computer-owned position indicators over the native map. */
public final class FlightMapPositionOverlay {
    public void render(GuiGraphics graphics, int mapLeft, int mapTop, int mapWidth, int mapHeight,
                       double centerX, double centerZ, BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (minecraft.player != null) {
            int x = worldToViewportX(minecraft.player.getX(), centerX, mapLeft, mapWidth);
            int y = worldToViewportY(minecraft.player.getZ(), centerZ, mapTop, mapHeight);
            drawTriangle(graphics, x, y, 4, 0xFFFF3333);
        }
        if (controllerPos != null) {
            int x = worldToViewportX(controllerPos.getX() + 0.5D, centerX, mapLeft, mapWidth);
            int y = worldToViewportY(controllerPos.getZ() + 0.5D, centerZ, mapTop, mapHeight);
            drawDiamond(graphics, x, y, 4, 0xFF66D9FF);
        }
    }

    private static int worldToViewportX(double worldX, double centerX, int left, int width) {
        return (int)Math.round(left + width / 2.0D + (worldX - centerX));
    }

    private static int worldToViewportY(double worldZ, double centerZ, int top, int height) {
        return (int)Math.round(top + height / 2.0D + (worldZ - centerZ));
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
