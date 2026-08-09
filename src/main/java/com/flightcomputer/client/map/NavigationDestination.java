package com.flightcomputer.client.map;

/**
 * Flight Computer-owned destination state derived from a real Xaero waypoint.
 * This is navigation state only; Xaero remains responsible for rendering the waypoint.
 */
public record NavigationDestination(String name, String dimension, int x, int y, int z) {
    public static NavigationDestination from(XaeroWaypointProvider.Waypoint waypoint) {
        return new NavigationDestination(waypoint.name(), waypoint.dimension(), waypoint.x(), waypoint.y(), waypoint.z());
    }
}
