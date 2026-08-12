package com.flightcomputer.client.map.nativeimpl;

/** Read-only diagnostic snapshot for the Flight Computer map UI. */
public record NativeMapDiagnostics(
        int pending,
        int cached,
        long submitted,
        long generated,
        long failed,
        double cacheHitRatio) {
}
