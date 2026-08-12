package com.flightcomputer.client.map;

/** Pure CPU-side terrain shading. It accepts only immutable snapshot data. */
public final class CpuTerrainTileGenerator {
    public int[] generate(TerrainChunkSnapshot snapshot) {
        int[] tile = new int[256];
        for (int i = 0; i < tile.length; i++) {
            int base = snapshot.color(i);
            int height = snapshot.height(i);
            int neighbour = i;
            if ((i & 15) > 0) neighbour = i - 1;
            int delta = height - snapshot.height(neighbour);
            int shade = delta > 2 ? 10 : delta < -2 ? -10 : 0;
            tile[i] = shade(base, shade);
        }
        return tile;
    }

    private int shade(int argb, int amount) {
        int r = clamp(((argb >>> 16) & 0xFF) + amount);
        int g = clamp(((argb >>> 8) & 0xFF) + amount);
        int b = clamp((argb & 0xFF) + amount);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
