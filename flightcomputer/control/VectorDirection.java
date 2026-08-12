package com.flightcomputer.control;

/** Six physical faces used by the Link Tool. The same six slots exist for stabilisation and autopilot. */
public enum VectorDirection {
    NORTH("N", 0, 0, -1),
    EAST("E", 1, 0, 0),
    SOUTH("S", 0, 0, 1),
    WEST("W", -1, 0, 0),
    UP("U", 0, 1, 0),
    DOWN("D", 0, -1, 0);

    private final String shortName;
    private final int x;
    private final int y;
    private final int z;

    VectorDirection(String shortName, int x, int y, int z) {
        this.shortName = shortName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String shortName() { return shortName; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public VectorDirection next(int delta) {
        VectorDirection[] values = values();
        return values[Math.floorMod(ordinal() + delta, values.length)];
    }
}
