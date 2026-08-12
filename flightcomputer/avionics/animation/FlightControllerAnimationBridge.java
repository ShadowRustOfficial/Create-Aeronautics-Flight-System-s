package com.flightcomputer.avionics.animation;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;

/** Single mapping point between gameplay actions and Blockbench animation names. */
public final class FlightControllerAnimationBridge {
    public static final String ENGAGED_ON = "Engaged (Toggle on)";
    public static final String ENGAGED_OFF = "Engaged (Toggle off)";
    public static final String STABILISER_ON = "Stabiliser's (Toggle on)";
    public static final String STABILISER_OFF = "Stabilisers (Toggle off)";
    public static final String MODE_PRESS = "Mode Select (Press)";
    public static final String DISPLAY_PRESS = "Display (Press)";

    private FlightControllerAnimationBridge() {}

    public static String forAction(FlightControllerAction action, FlightControllerState state) {
        return switch (action) {
            case TOGGLE_ENGAGED -> state.engaged() ? ENGAGED_ON : ENGAGED_OFF;
            case TOGGLE_STABILISER -> state.stabiliser() ? STABILISER_ON : STABILISER_OFF;
            case CYCLE_MODE -> MODE_PRESS;
            case PULSE_DISPLAY -> DISPLAY_PRESS;
            default -> "";
        };
    }
}
