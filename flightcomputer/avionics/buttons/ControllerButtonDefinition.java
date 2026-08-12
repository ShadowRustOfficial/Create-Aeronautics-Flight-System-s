package com.flightcomputer.avionics.buttons;

import com.flightcomputer.avionics.FlightControllerAction;

/** A data definition for one physical front-panel control; coordinates are local U/V values. */
public record ControllerButtonDefinition(String id, double minU, double maxU, double minV, double maxV,
                                         FlightControllerAction action) {
    public boolean contains(double u, double v) {
        return u >= minU && u <= maxU && v >= minV && v <= maxV;
    }
}
