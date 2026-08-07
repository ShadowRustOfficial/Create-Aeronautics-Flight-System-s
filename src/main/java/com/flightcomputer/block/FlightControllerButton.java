package com.flightcomputer.block;

/**
 * Static definition of a single physical/GUI button on the controller. Register one of
 * these per button in ControllerButtons; the block entity only ever stores which ids are
 * currently toggled on, never touches animation names directly - gameplay code must not
 * reference GeckoLib clip names outside this definition and ControllerButtons.
 *
 * bone/pressAnimation are placeholders ("button_power" / "animation.flight_controller
 * .button_press") - line these up with the real geo.json bone names and animation.json
 * clip names before relying on them; they were not verified against the model assets.
 */
public record FlightControllerButton(String id, String bone, String pressAnimation, boolean isToggle) {
}
