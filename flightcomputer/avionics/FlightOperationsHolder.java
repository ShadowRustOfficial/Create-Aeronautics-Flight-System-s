package com.flightcomputer.avionics;

/** Access contract for persistent Phase 5.2 operations data on a Flight Controller. */
public interface FlightOperationsHolder {
    FlightOperationsState getFlightOperations();
    void setFlightOperations(FlightOperationsState state);
}
