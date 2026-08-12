package com.flightcomputer.client.map.nativeimpl;

/** Immutable 16x16 terrain snapshot. Minecraft objects do not cross into worker code. */
public final class NativeChunkSnapshot {
    public static final int SIDE = 16;

    private final NativeMapTileKey key;
    private final int[] surfaceHeights;
    private final long sourceRevision;

    public NativeChunkSnapshot(NativeMapTileKey key, int[] surfaceHeights, long sourceRevision) {
        if (surfaceHeights == null || surfaceHeights.length != SIDE * SIDE) {
            throw new IllegalArgumentException("Native chunk snapshot must contain exactly 256 heights");
        }
        this.key = key;
        this.surfaceHeights = surfaceHeights.clone();
        this.sourceRevision = sourceRevision;
    }

    public NativeMapTileKey key() { return key; }
    public int[] surfaceHeights() { return surfaceHeights; }
    public long sourceRevision() { return sourceRevision; }
}
