package com.flightcomputer.client.map.nativeimpl;

/** Pure CPU tile generator. It must never access Minecraft or OpenGL. */
public final class NativeMapTileWorker {
    private NativeMapTileWorker() {}

    public static NativeMapTile generate(NativeChunkSnapshot snapshot) {
        int[] heights = snapshot.surfaceHeights();
        int[] pixels = new int[NativeMapTile.SIDE * NativeMapTile.SIDE];

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int height : heights) {
            min = Math.min(min, height);
            max = Math.max(max, height);
        }
        int range = Math.max(1, max - min);

        for (int i = 0; i < pixels.length; i++) {
            int value = 55 + ((heights[i] - min) * 180 / range);
            value = Math.max(0, Math.min(255, value));
            pixels[i] = 0xFF000000 | (value << 16) | (value << 8) | value;
        }
        return new NativeMapTile(snapshot.key(), pixels, snapshot.sourceRevision());
    }
}
