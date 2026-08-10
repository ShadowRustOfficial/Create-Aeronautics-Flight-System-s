package com.flightcomputer.client.map;

/** Immutable world-space request made by the Flight Computer renderer. */
public record TerrainViewport(
        double centerX,
        double centerZ,
        double radiusBlocks,
        double blocksPerPixel
) {
    public TerrainViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Viewport center must be finite");
        }
        if (!Double.isFinite(radiusBlocks) || radiusBlocks < 0.0D) {
            throw new IllegalArgumentException("Viewport radius must be finite and non-negative");
        }
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
            throw new IllegalArgumentException("blocksPerPixel must be finite and positive");
        }
    }
}
