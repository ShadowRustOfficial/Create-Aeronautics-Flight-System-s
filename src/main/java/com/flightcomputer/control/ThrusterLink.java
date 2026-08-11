package com.flightcomputer.control;

public final class ThrusterLink {
    public final PropulsionSource source;
    public final ControlAxis axis;
    public final VectorDirection direction;
    public final FlightMode mode;
    public final double polarity;

    public ThrusterLink(PropulsionSource source, ControlAxis axis, FlightMode mode, double polarity) {
        this(source, axis, defaultDirection(axis), mode, polarity);
    }

    public ThrusterLink(PropulsionSource source, ControlAxis axis, VectorDirection direction,
                        FlightMode mode, double polarity) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (axis == null) throw new IllegalArgumentException("axis must not be null");
        if (direction == null) throw new IllegalArgumentException("direction must not be null");
        if (polarity != 1.0 && polarity != -1.0) {
            throw new IllegalArgumentException("polarity must be +1 or -1");
        }
        this.source = source;
        this.axis = axis;
        this.direction = direction;
        this.mode = mode;
        this.polarity = polarity;
    }

    private static VectorDirection defaultDirection(ControlAxis axis) {
        return switch (axis) {
            case VERTICAL -> VectorDirection.UP;
            case LONGITUDINAL -> VectorDirection.NORTH;
            case LATERAL -> VectorDirection.EAST;
            case PITCH, ROLL, YAW -> VectorDirection.NORTH;
        };
    }
}
