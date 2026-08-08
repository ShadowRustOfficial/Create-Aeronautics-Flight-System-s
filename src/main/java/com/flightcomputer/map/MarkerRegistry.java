package com.flightcomputer.map;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side store of points of interest shown on the Flight Computer's own map screen.
 * Other parts of the mod (claim tracking, waypoint placement, landing pad detection) push
 * markers in here; the map screen only ever reads from here. This never stores entity or
 * mob positions, and it never writes to another mod's data structures.
 */
public final class MarkerRegistry {

    private static final Map<String, MapMarker> MARKERS = new LinkedHashMap<>();
    private static final Map<MarkerCategory, Boolean> VISIBLE = new EnumMap<>(MarkerCategory.class);

    static {
        for (MarkerCategory category : MarkerCategory.values()) {
            VISIBLE.put(category, true);
        }
    }

    private MarkerRegistry() {}

    public static void put(MapMarker marker) {
        MARKERS.put(marker.id(), marker);
    }

    public static void remove(String id) {
        MARKERS.remove(id);
    }

    public static void clear() {
        MARKERS.clear();
    }

    /** Removes only markers owned by one integration category. */
    public static void clearCategory(MarkerCategory category) {
        Iterator<Map.Entry<String, MapMarker>> iterator = MARKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().category() == category) iterator.remove();
        }
    }

    public static Collection<MapMarker> all() {
        return MARKERS.values();
    }

    public static boolean isVisible(MarkerCategory category) {
        return VISIBLE.getOrDefault(category, true);
    }

    public static void toggle(MarkerCategory category) {
        VISIBLE.put(category, !isVisible(category));
    }
}