package com.flightcomputer.avionics.buttons;

import com.flightcomputer.avionics.FlightControllerAction;
import java.util.List;
import java.util.Optional;

/**
 * Tier-1 front-panel layout, derived from the Geo model's cubes. U is left to
 * right; V is bottom to top. The small margin makes pressing a 2-pixel-wide
 * model button practical without letting clicks spill into its neighbour.
 *
 * The physical button order is intentionally kept fixed here. The first two
 * physical buttons are the momentary controls; the last two are the stateful
 * toggle controls. This keeps animation semantics aligned with the actual
 * button geometry rather than changing hitbox rectangles.
 */
public final class FlightControllerButtonLayout {
    private static final List<ControllerButtonDefinition> BUTTONS = List.of(
            new ControllerButtonDefinition("mode", 0.105, 0.333, 0.105, 0.333, FlightControllerAction.CYCLE_MODE),
            new ControllerButtonDefinition("display", 0.324, 0.489, 0.105, 0.333, FlightControllerAction.PULSE_DISPLAY),
            new ControllerButtonDefinition("engage", 0.511, 0.676, 0.105, 0.333, FlightControllerAction.TOGGLE_ENGAGED),
            new ControllerButtonDefinition("stabiliser", 0.667, 0.895, 0.105, 0.333, FlightControllerAction.TOGGLE_STABILISER));

    private FlightControllerButtonLayout() {}

    public static Optional<ControllerButtonDefinition> find(double u, double v) {
        return BUTTONS.stream().filter(button -> button.contains(u, v)).findFirst();
    }
}
