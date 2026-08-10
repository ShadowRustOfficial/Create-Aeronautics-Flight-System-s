package com.flightcomputer.client.navigation;

import net.minecraft.resources.ResourceLocation;

/**
 * Client-side navigation state for the Flight Computer.
 * Track, Route and Travel are intentionally independent of Xaero's GUI state.
 */
public final class FlightNavigationState {
    public enum Mode { IDLE, TRACKING, ROUTING, TRAVELLING }

    private final FlightRoute route = new FlightRoute();
    private Mode mode = Mode.IDLE;

    public FlightRoute route() { return route; }
    public Mode mode() { return mode; }

    /** Marks the active controller's known terrain/navigation area as being tracked. */
    public void track() { mode = Mode.TRACKING; }

    /** Starts route planning. The route itself is supplied by the Flight Computer POI layer. */
    public void beginRoute() { mode = route.isEmpty() ? Mode.ROUTING : Mode.ROUTING; }

    /** Begins navigation toward the route's next node. */
    public boolean travel() {
        if (route.isEmpty()) return false;
        mode = Mode.TRAVELLING;
        return true;
    }

    public void abort() { mode = Mode.IDLE; }

    public boolean isTravelling() { return mode == Mode.TRAVELLING; }

    public double distanceToNext(double x, double z, ResourceLocation dimension) {
        if (route.next() == null || !route.next().dimension().equals(dimension)) return 0.0D;
        return route.distance2D(x, z);
    }

    public double bearingToNext(double x, double z, ResourceLocation dimension) {
        if (route.next() == null || !route.next().dimension().equals(dimension)) return 0.0D;
        return route.bearingDegrees(x, z);
    }
}
