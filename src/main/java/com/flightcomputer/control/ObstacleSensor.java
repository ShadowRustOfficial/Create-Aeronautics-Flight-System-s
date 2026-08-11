package com.flightcomputer.control;

/** World raycast adapter. The implementation must exclude the craft's own moving Sub-Level. */
public interface ObstacleSensor {
    double raycast(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance);
}
