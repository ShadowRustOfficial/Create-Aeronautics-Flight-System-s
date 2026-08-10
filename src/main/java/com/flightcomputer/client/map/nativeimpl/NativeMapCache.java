package com.flightcomputer.client.map.nativeimpl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache for decoded native tiles. The renderer only reads this cache.
 */
public final class NativeMapCache {
    private final int capacity;
    private final Map<NativeMapTileKey, NativeMapTile> tiles;
    private long hits;
    private long misses;

    public NativeMapCache(int capacity) {
        this.capacity = Math.max(64, capacity);
        this.tiles = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<NativeMapTileKey, NativeMapTile> eldest) {
                return size() > NativeMapCache.this.capacity;
            }
        };
    }

    public synchronized NativeMapTile get(NativeMapTileKey key) {
        NativeMapTile tile = tiles.get(key);
        if (tile == null) misses++;
        else hits++;
        return tile;
    }

    public synchronized void put(NativeMapTile tile) {
        tiles.put(tile.key(), tile);
    }

    public synchronized void clear() {
        tiles.clear();
    }

    public synchronized int size() { return tiles.size(); }
    public synchronized long hits() { return hits; }
    public synchronized long misses() { return misses; }

    public synchronized double hitRatio() {
        long total = hits + misses;
        return total == 0 ? 1.0 : (double) hits / total;
    }
}
