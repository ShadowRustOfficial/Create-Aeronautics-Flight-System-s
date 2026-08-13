package com.flightcomputer.control.autotune;

public record TuningResult(Gains pitch, Gains roll, Gains yaw, Gains vertical, Gains longitudinal,
                           Gains lateral, long fingerprint, int version) {
    public record Gains(double p, double i, double d, double maxOutput) { }
}
