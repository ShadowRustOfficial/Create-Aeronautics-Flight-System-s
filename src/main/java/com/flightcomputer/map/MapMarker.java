package com.flightcomputer.map;

/**
 * A single point of interest on the Flight Map. dimensionId is the string form of a
 * dimension's ResourceLocation (e.g. "minecraft:overworld") so this record stays free
 * of any registry/level dependency.
 */
public record MapMarker(String id, String name, MarkerCategory category, int x, int y, int z, String dimensionId) {
}
