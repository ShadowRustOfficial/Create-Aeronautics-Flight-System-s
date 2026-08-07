package com.flightcomputer.avionics;

import net.minecraft.nbt.CompoundTag;

/** Immutable, persisted source of truth for a single Flight Controller. */
public record FlightControllerState(
        boolean engaged,
        boolean stabiliser,
        FlightMode flightMode,
        boolean altitudeHold,
        boolean headingHold,
        boolean positionHold,
        boolean velocityHold,
        boolean navigationEnabled,
        boolean routeActive
) {
    public static final FlightControllerState DEFAULT = new FlightControllerState(
            false, false, FlightMode.DISENGAGED, false, false, false, false, false, false);

    public FlightControllerState apply(FlightControllerAction action) {
        return switch (action) {
            case TOGGLE_ENGAGED -> new FlightControllerState(!engaged, stabiliser, !engaged ? FlightMode.MANUAL : FlightMode.DISENGAGED,
                    altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_STABILISER -> new FlightControllerState(engaged, !stabiliser, flightMode,
                    altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case CYCLE_MODE -> new FlightControllerState(engaged, stabiliser, flightMode.next(),
                    altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_ALTITUDE_HOLD -> new FlightControllerState(engaged, stabiliser, flightMode,
                    !altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_HEADING_HOLD -> new FlightControllerState(engaged, stabiliser, flightMode,
                    altitudeHold, !headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_POSITION_HOLD -> new FlightControllerState(engaged, stabiliser, flightMode,
                    altitudeHold, headingHold, !positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_VELOCITY_HOLD -> new FlightControllerState(engaged, stabiliser, flightMode,
                    altitudeHold, headingHold, positionHold, !velocityHold, navigationEnabled, routeActive);
            case TOGGLE_NAVIGATION -> new FlightControllerState(engaged, stabiliser, flightMode,
                    altitudeHold, headingHold, positionHold, velocityHold, !navigationEnabled, routeActive);
            case START_ROUTE -> new FlightControllerState(engaged, stabiliser, FlightMode.AUTOPILOT,
                    altitudeHold, headingHold, positionHold, velocityHold, true, true);
            case ABORT_ROUTE -> new FlightControllerState(engaged, stabiliser, flightMode,
                    altitudeHold, headingHold, positionHold, velocityHold, false, false);
            case PULSE_DISPLAY -> this;
        };
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("Engaged", engaged);
        tag.putBoolean("Stabiliser", stabiliser);
        tag.putString("FlightMode", flightMode.name());
        tag.putBoolean("AltitudeHold", altitudeHold);
        tag.putBoolean("HeadingHold", headingHold);
        tag.putBoolean("PositionHold", positionHold);
        tag.putBoolean("VelocityHold", velocityHold);
        tag.putBoolean("NavigationEnabled", navigationEnabled);
        tag.putBoolean("RouteActive", routeActive);
    }

    public static FlightControllerState load(CompoundTag tag) {
        FlightMode mode;
        try { mode = FlightMode.valueOf(tag.getString("FlightMode")); }
        catch (IllegalArgumentException ignored) { mode = FlightMode.DISENGAGED; }
        return new FlightControllerState(tag.getBoolean("Engaged"), tag.getBoolean("Stabiliser"), mode,
                tag.getBoolean("AltitudeHold"), tag.getBoolean("HeadingHold"), tag.getBoolean("PositionHold"),
                tag.getBoolean("VelocityHold"), tag.getBoolean("NavigationEnabled"), tag.getBoolean("RouteActive"));
    }
}
