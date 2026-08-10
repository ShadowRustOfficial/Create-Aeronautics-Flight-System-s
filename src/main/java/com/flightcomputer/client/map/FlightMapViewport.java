package com.flightcomputer.client.map;

import net.minecraft.resources.ResourceLocation;

/**
 * First-party Flight Map camera state. This class intentionally has no Xaero dependency.
 * It is the sole source of truth for Flight Computer pan/zoom state.
 */
public final class FlightMapViewport {
    private double centerX;
    private double centerZ;
    private double blocksPerPixel;
    private ResourceLocation dimension;

    public FlightMapViewport(double centerX, double centerZ, double blocksPerPixel, ResourceLocation dimension) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.blocksPerPixel = clampScale(blocksPerPixel);
        this.dimension = dimension;
    }

    public double centerX() { return centerX; }
    public double centerZ() { return centerZ; }
    public double blocksPerPixel() { return blocksPerPixel; }
    public ResourceLocation dimension() { return dimension; }

    public void setDimension(ResourceLocation dimension) { this.dimension = dimension; }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
    }

    public void panPixels(double dx, double dy) {
        centerX -= dx * blocksPerPixel;
        centerZ -= dy * blocksPerPixel;
    }

    /** Positive wheel delta zooms in. Zoom is multiplicative and bounded. */
    public void zoom(double wheelDelta) {
        if (wheelDelta == 0.0D) return;
        double factor = Math.pow(0.82D, wheelDelta);
        blocksPerPixel = clampScale(blocksPerPixel * factor);
    }

    public void setBlocksPerPixel(double blocksPerPixel) {
        this.blocksPerPixel = clampScale(blocksPerPixel);
    }

    public void centreOn(double x, double z) { setCenter(x, z); }

    private static double clampScale(double value) {
        if (!Double.isFinite(value)) return 4.0D;
        return Math.max(0.25D, Math.min(64.0D, value));
    }
}
