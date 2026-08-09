package com.flightcomputer.client.xaerobridge.api;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Context supplied to Flight Computer map overlays while Xaero is rendering its map.
 * Xaero remains the source of terrain; overlays only draw above it.
 */
public final class MapOverlayContext {
    private final GuiGraphics graphics;
    private final int width;
    private final int height;
    private final double cameraX;
    private final double cameraZ;
    private final double blocksPerPixel;
    private final String dimensionId;

    public MapOverlayContext(GuiGraphics graphics, int width, int height,
                             double cameraX, double cameraZ, double blocksPerPixel,
                             String dimensionId) {
        this.graphics = graphics;
        this.width = width;
        this.height = height;
        this.cameraX = cameraX;
        this.cameraZ = cameraZ;
        this.blocksPerPixel = blocksPerPixel;
        this.dimensionId = dimensionId;
    }

    public GuiGraphics graphics() { return graphics; }
    public int width() { return width; }
    public int height() { return height; }
    public double cameraX() { return cameraX; }
    public double cameraZ() { return cameraZ; }
    public double blocksPerPixel() { return blocksPerPixel; }
    public String dimensionId() { return dimensionId; }

    public int worldToScreenX(double worldX) {
        return (int) Math.round((worldX - cameraX) / blocksPerPixel + width / 2.0D);
    }

    public int worldToScreenZ(double worldZ) {
        return (int) Math.round((worldZ - cameraZ) / blocksPerPixel + height / 2.0D);
    }

    public void fill(int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
    }
}
