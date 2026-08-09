package com.flightcomputer.client.xaerobridge.api;

import net.minecraft.client.gui.GuiGraphics;

/** Context supplied to overlays drawn after Xaero's map/UI pass. */
public final class UiOverlayContext {
    private final GuiGraphics graphics;
    private final int width;
    private final int height;

    public UiOverlayContext(GuiGraphics graphics, int width, int height) {
        this.graphics = graphics;
        this.width = width;
        this.height = height;
    }

    public GuiGraphics graphics() { return graphics; }
    public int width() { return width; }
    public int height() { return height; }

    public void fill(int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
    }
}
