package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import java.util.List;

/** Stable waypoint facade. Uses the persistent Xaero reader so API changes cannot break navigation. */
public final class WaypointMapProvider {
    private final XaeroWaypointFileProvider fileProvider = new XaeroWaypointFileProvider();
    public void tick(ClientLevel level) { fileProvider.tick(level); }
    public List<FlightMapMarker> markers() { return fileProvider.markers(); }
    public boolean isAvailable() { return !markers().isEmpty(); }
    public void clear() { }
}
