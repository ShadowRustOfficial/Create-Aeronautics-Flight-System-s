package com.flightcomputer.identity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Persistent identity and per-player home data exposed by a Flight Controller. */
public interface FlightIdentityAccess {
    String flightcomputer$getSubLevelName();
    void flightcomputer$setSubLevelName(String name);
    String flightcomputer$getFlightId();
    void flightcomputer$setFlightId(String id);
    Vec3 flightcomputer$getHome(UUID playerId);
    void flightcomputer$setHome(UUID playerId, Vec3 position);
    default boolean flightcomputer$hasHome(UUID playerId) { return flightcomputer$getHome(playerId) != null; }
}