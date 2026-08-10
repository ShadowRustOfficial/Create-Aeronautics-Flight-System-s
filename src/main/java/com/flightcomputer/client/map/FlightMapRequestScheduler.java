package com.flightcomputer.client.map;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded, deduplicating request scheduler. It owns scheduling only; providers never enqueue
 * themselves into the UI/render loop.
 */
public final class FlightMapRequestScheduler {
    private static final int DEFAULT_CAPACITY = 512;
    private final int capacity;
    private final PriorityQueue<FlightMapTileRequest> queue = new PriorityQueue<>(
            Comparator.comparingInt(FlightMapTileRequest::priority).reversed()
                    .thenComparingLong(FlightMapTileRequest::sequence));
    private final Set<Long> queued = new HashSet<>();
    private long sequence;

    public FlightMapRequestScheduler() {
        this(DEFAULT_CAPACITY);
    }

    public FlightMapRequestScheduler(int capacity) {
        this.capacity = Math.max(32, capacity);
    }

    public boolean offer(int chunkX, int chunkZ, int priority) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (!queued.add(key)) return false;
        if (queue.size() >= capacity) {
            FlightMapTileRequest worst = queue.stream()
                    .min(Comparator.comparingInt(FlightMapTileRequest::priority)
                            .thenComparingLong(FlightMapTileRequest::sequence))
                    .orElse(null);
            if (worst != null && worst.priority() < priority) {
                queue.remove(worst);
                queued.remove(worst.key());
            } else {
                queued.remove(key);
                return false;
            }
        }
        queue.offer(FlightMapTileRequest.of(chunkX, chunkZ, priority, sequence++));
        return true;
    }

    public FlightMapTileRequest poll() {
        FlightMapTileRequest request = queue.poll();
        if (request != null) queued.remove(request.key());
        return request;
    }

    public boolean contains(long key) { return queued.contains(key); }
    public int size() { return queue.size(); }
    public int capacity() { return capacity; }
    public boolean isEmpty() { return queue.isEmpty(); }

    public void clear() {
        queue.clear();
        queued.clear();
    }

    /** Centre/near/far priorities used by the viewport planner. */
    public static int priorityForDistance(int chunkDistance) {
        if (chunkDistance <= 1) return 100;
        if (chunkDistance <= 4) return 75;
        if (chunkDistance <= 8) return 50;
        return 25;
    }
}
