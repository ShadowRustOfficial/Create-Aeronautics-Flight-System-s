package com.flightcomputer.block;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registered controller buttons. Adding a new physical button means adding one entry
 * here - nothing else needs to change for it to toggle state and animate correctly.
 * See FlightControllerButton for the bone/animation-name caveat.
 */
public final class ControllerButtons {

    private static final Map<String, FlightControllerButton> BUTTONS = new LinkedHashMap<>();

    public static final FlightControllerButton POWER =
            register(new FlightControllerButton("power", "button_power", "animation.flight_controller.button_press", true));

    private static FlightControllerButton register(FlightControllerButton button) {
        BUTTONS.put(button.id(), button);
        return button;
    }

    public static FlightControllerButton get(String id) {
        return BUTTONS.get(id);
    }

    public static Collection<FlightControllerButton> all() {
        return BUTTONS.values();
    }

    private ControllerButtons() {}
}
