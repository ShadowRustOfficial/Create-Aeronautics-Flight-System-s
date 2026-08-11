package com.flightcomputer.avionics;

import net.minecraft.nbt.CompoundTag;

/** Immutable, persisted source of truth for one controller. */
public record FlightControllerState(
        boolean engaged, boolean stabiliser, FlightMode flightMode,
        boolean altitudeHold, boolean headingHold, boolean positionHold, boolean velocityHold,
        boolean navigationEnabled, boolean routeActive) {
    public static final FlightControllerState DEFAULT = new FlightControllerState(false, false, FlightMode.DISENGAGED,
            false, false, false, false, false, false);

    public FlightControllerState apply(FlightControllerAction action) {
        return switch (action) {
            case TOGGLE_ENGAGED -> engaged
                    ? new FlightControllerState(false, false, FlightMode.DISENGAGED, false, false, false, false, false, false)
                    : new FlightControllerState(true, false, FlightMode.MANUAL,
                            altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_STABILISER -> {
                boolean enabled = !stabiliser;
                FlightMode nextMode = enabled ? FlightMode.STABILIZED : (flightMode == FlightMode.STABILIZED ? FlightMode.MANUAL : flightMode);
                yield new FlightControllerState(engaged, enabled, nextMode,
                        altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            }
            case CYCLE_MODE -> {
                FlightMode next = flightMode.next();
                boolean nextStabiliser = next == FlightMode.STABILIZED;
                yield new FlightControllerState(engaged, nextStabiliser, next,
                        altitudeHold, headingHold, positionHold, velocityHold,
                        next == FlightMode.AUTOPILOT || navigationEnabled, routeActive);
            }
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
            case START_ROUTE -> new FlightControllerState(true, true, FlightMode.AUTOPILOT,
                    altitudeHold, headingHold, positionHold, velocityHold, true, true);
            case ABORT_ROUTE -> new FlightControllerState(engaged, true, FlightMode.STABILIZED,
                    altitudeHold, headingHold, positionHold, velocityHold, false, false);
            case EMERGENCY_SHUTDOWN -> new FlightControllerState(false, false, FlightMode.DISENGAGED,
                    false, false, false, false, false, false);
            case PULSE_DISPLAY, TOGGLE_TERRAIN -> this;
        };
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("Engaged", engaged); tag.putBoolean("Stabiliser", stabiliser); tag.putString("FlightMode", flightMode.name());
        tag.putBoolean("AltitudeHold", altitudeHold); tag.putBoolean("HeadingHold", headingHold); tag.putBoolean("PositionHold", positionHold);
        tag.putBoolean("VelocityHold", velocityHold); tag.putBoolean("NavigationEnabled", navigationEnabled); tag.putBoolean("RouteActive", routeActive);
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
