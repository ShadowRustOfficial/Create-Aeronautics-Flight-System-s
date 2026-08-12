package com.flightcomputer.control;

/** Runtime health classification for diagnostics and allocator derating. */
public record ThrusterHealth(Status status, double availableFraction, String reason) {
    public enum Status { ONLINE, DEGRADED, FAILED, UNAVAILABLE }

    public ThrusterHealth {
        status = status == null ? Status.UNAVAILABLE : status;
        availableFraction = Math.max(0.0D, Math.min(1.0D, availableFraction));
        reason = reason == null ? "" : reason;
    }

    public boolean usable() { return status == Status.ONLINE || status == Status.DEGRADED; }
}
