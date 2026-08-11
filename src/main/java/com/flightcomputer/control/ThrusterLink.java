package com.flightcomputer.control;

public final class ThrusterLink {
    public final PropulsionSource source;
    public final ControlAxis axis;
    public final FlightMode mode;
    public final double polarity;

    public ThrusterLink(PropulsionSource source, ControlAxis axis, FlightMode mode, double polarity) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (polarity != 1.0 && polarity != -1.0) throw new IllegalArgumentException("polarity must be +1 or -1");
        this.source = source;
        this.axis = axis;
        this.mode = mode;
        this.polarity = polarity;
    }
}
