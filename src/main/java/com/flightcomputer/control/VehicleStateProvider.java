package com.flightcomputer.control;

/** Single adapter boundary from Sable/Aeronautics to the control core. */
public interface VehicleStateProvider {
    VehicleState getState();
}
