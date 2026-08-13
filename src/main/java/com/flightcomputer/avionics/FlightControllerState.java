package com.flightcomputer.avionics;

import net.minecraft.nbt.CompoundTag;

/** Immutable, persisted source of truth for one controller. Stabiliser and autopilot are independent control layers. */
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
                    : new FlightControllerState(true, stabiliser, stabiliser ? FlightMode.STABILIZED : FlightMode.MANUAL,
                            altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            case TOGGLE_STABILISER -> {
                boolean enabled = !stabiliser;
                FlightMode nextMode = flightMode == FlightMode.AUTOPILOT
                        ? FlightMode.AUTOPILOT
                        : enabled ? FlightMode.STABILIZED : FlightMode.MANUAL;
                yield new FlightControllerState(engaged, enabled, nextMode,
                        altitudeHold, headingHold, positionHold, velocityHold, navigationEnabled, routeActive);
            }
            case TOGGLE_AUTOPILOT -> {
                boolean enabled = flightMode != FlightMode.AUTOPILOT;
                FlightMode nextMode = enabled ? FlightMode.AUTOPILOT : (stabiliser ? FlightMode.STABILIZED : FlightMode.MANUAL);
                yield new FlightControllerState(engaged || enabled, stabiliser, nextMode,
                        altitudeHold, headingHold, positionHold, velocityHold, enabled || navigationEnabled, routeActive);
            }
            case CYCLE_MODE -> {
                FlightMode next = flightMode.next();
                boolean nextStabiliser = next == FlightMode.STABILIZED || (next == FlightMode.MANUAL && stabiliser)
                        || (next == FlightMode.AUTOPILOT && stabiliser);
                boolean nextEngaged = next != FlightMode.DISENGAGED && (engaged || nextStabiliser || next == FlightMode.AUTOPILOT);
                yield new FlightControllerState(nextEngaged, nextStabiliser, next,
                        altitudeHold, headingHold, positionHold, velocityHold,
                        next == FlightMode.AUTOPILOT || navigationEnabled, routeActive);
            }
            case TOGGLE_ALTITUDE_HOLD -> toggleHold(0);
            case TOGGLE_HEADING_HOLD -> toggleHold(1);
            case TOGGLE_POSITION_HOLD -> toggleHold(2);
            case TOGGLE_VELOCITY_HOLD -> toggleHold(3);
            case TOGGLE_NAVIGATION -> {
                boolean enabled = !navigationEnabled;
                if (!enabled && flightMode == FlightMode.AUTOPILOT)
                    yield new FlightControllerState(engaged, stabiliser,
                            stabiliser ? FlightMode.STABILIZED : FlightMode.MANUAL,
                            altitudeHold, headingHold, positionHold, velocityHold, false, false);
                yield new FlightControllerState(engaged, stabiliser, flightMode,
                        altitudeHold, headingHold, positionHold, velocityHold, enabled, routeActive && enabled);
            }
            case START_ROUTE -> new FlightControllerState(true, stabiliser, FlightMode.AUTOPILOT,
                    altitudeHold, headingHold, positionHold, velocityHold, true, true);
            case ABORT_ROUTE -> new FlightControllerState(engaged, stabiliser,
                    stabiliser ? FlightMode.STABILIZED : FlightMode.MANUAL,
                    altitudeHold, headingHold, positionHold, velocityHold, false, false);
            case EMERGENCY_SHUTDOWN -> new FlightControllerState(false, false, FlightMode.DISENGAGED,
                    false, false, false, false, false, false);
            case PULSE_DISPLAY, TOGGLE_TERRAIN -> this;
        };
    }

    private FlightControllerState toggleHold(int hold) {
        boolean nextAltitude = hold == 0 ? !altitudeHold : altitudeHold;
        boolean nextHeading = hold == 1 ? !headingHold : headingHold;
        boolean nextPosition = hold == 2 ? !positionHold : positionHold;
        boolean nextVelocity = hold == 3 ? !velocityHold : velocityHold;
        boolean enabling = (hold == 0 && nextAltitude) || (hold == 1 && nextHeading)
                || (hold == 2 && nextPosition) || (hold == 3 && nextVelocity);

        boolean nextEngaged = enabling ? true : engaged;
        boolean nextStabiliser = flightMode == FlightMode.AUTOPILOT ? stabiliser : (enabling || stabiliser);
        FlightMode nextMode = flightMode == FlightMode.AUTOPILOT ? FlightMode.AUTOPILOT
                : (enabling ? FlightMode.STABILIZED : (flightMode == FlightMode.STABILIZED && !nextStabiliser ? FlightMode.MANUAL : flightMode));
        return new FlightControllerState(nextEngaged, nextStabiliser, nextMode,
                nextAltitude, nextHeading, nextPosition, nextVelocity, navigationEnabled, routeActive);
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
