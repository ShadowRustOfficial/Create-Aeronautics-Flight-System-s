package com.flightcomputer.client.map;

/**
 * Immutable data captured on Minecraft's client thread and safe to process off-thread.
 * No Minecraft world, chunk, block-state, or level objects escape the snapshot boundary.
 */
public final class TerrainChunkSnapshot {
    public static final int SIDE = 16;
    private final int chunkX;
    private final int chunkZ;
    private final int[] colors;
    private final int[] heights;

    public TerrainChunkSnapshot(int chunkX, int chunkZ, int[] colors, int[] heights) {
        if (colors.length != 256 || heights.length != 256) {
            throw new IllegalArgumentException("Terrain snapshot must contain exactly 256 samples");
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.colors = colors.clone();
        this.heights = heights.clone();
    }

    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public int color(int index) { return colors[index]; }
    public int height(int index) { return heights[index]; }
}
