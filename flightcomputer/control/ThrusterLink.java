package com.flightcomputer.control;

/** One logical bank/vector assignment to one physical propulsion source. */
public final class ThrusterLink {
    public final PropulsionSource source;
    public final ControlAxis axis;
    public final VectorDirection direction;
    public final FlightMode mode;
    public final double polarity;

    public ThrusterLink(PropulsionSource source, ControlAxis axis, FlightMode mode, double polarity) {
        this(source, axis, defaultDirection(axis), mode, polarity);
    }

    public ThrusterLink(PropulsionSource source, VectorDirection direction, FlightMode mode) {
        this(source, axisFor(direction), direction, mode, 1.0D);
    }

    public ThrusterLink(PropulsionSource source, ControlAxis axis, VectorDirection direction,
                        FlightMode mode, double polarity) {
        if (source == null || axis == null || direction == null || mode == null) throw new IllegalArgumentException("link fields must not be null");
        if (polarity != 1.0 && polarity != -1.0) throw new IllegalArgumentException("polarity must be +1 or -1");
        this.source = source;
        this.axis = axis;
        this.direction = direction;
        this.mode = mode;
        this.polarity = polarity;
    }

    private static ControlAxis axisFor(VectorDirection direction) {
        return switch (direction) {
            case UP, DOWN -> ControlAxis.VERTICAL;
            case NORTH, SOUTH -> ControlAxis.LONGITUDINAL;
            case EAST, WEST -> ControlAxis.LATERAL;
        };
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
