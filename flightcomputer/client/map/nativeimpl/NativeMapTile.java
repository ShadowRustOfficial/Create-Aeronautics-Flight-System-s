package com.flightcomputer.client.map.nativeimpl;

/** Immutable CPU-side tile produced from a client chunk snapshot. */
public final class NativeMapTile {
    public static final int SIDE = 16;

    private final NativeMapTileKey key;
    private final int[] pixels;
    private final long sourceRevision;

    public NativeMapTile(NativeMapTileKey key, int[] pixels, long sourceRevision) {
        if (pixels == null || pixels.length != SIDE * SIDE) {
            throw new IllegalArgumentException("Native map tile must contain exactly 256 pixels");
        }
        this.key = key;
        this.pixels = pixels.clone();
        this.sourceRevision = sourceRevision;
    }

    public NativeMapTileKey key() { return key; }
    public int[] pixels() { return pixels; }
    public long sourceRevision() { return sourceRevision; }
}
