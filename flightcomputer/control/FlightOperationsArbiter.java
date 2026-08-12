package com.flightcomputer.control;

import com.flightcomputer.avionics.DockingState;
import com.flightcomputer.avionics.FlightOperationsState;

/**
 * Single arbitration point for future flight objectives. Individual systems should submit intent here
 * rather than directly fighting over the propulsion allocator.
 */
public final class FlightOperationsArbiter {
    private FlightOperationsArbiter() { }

    public static FlightObjective choose(FlightOperationsState operations,
                                         boolean emergencyOverride,
                                         boolean collisionHazard,
                                         boolean thermalProtection,
                                         boolean defensiveReturn,
                                         boolean landingAssist,
                                         boolean offensiveTracking,
                                         boolean routeActive,
                                         boolean stabilisationActive) {
        if (emergencyOverride || operations.dockingState() == DockingState.OVERRIDDEN) return FlightObjective.EMERGENCY_OVERRIDE;
        if (collisionHazard) return FlightObjective.COLLISION_SAFETY;
        if (thermalProtection) return FlightObjective.THERMAL_POWER_PROTECTION;
        if (defensiveReturn || operations.emergencyReturn()) return FlightObjective.DEFENSIVE_RETURN;
        if (operations.autoDocking() && !operations.dockingOverride()) return FlightObjective.AUTO_DOCK;
        if (landingAssist || operations.landingAssist()) return FlightObjective.LANDING_ASSIST;
        if (offensiveTracking || operations.combatAssist() && operations.offensiveCallsign() != null && !operations.offensiveCallsign().isBlank()) return FlightObjective.OFFENSIVE_TRACK;
        if (routeActive) return FlightObjective.ROUTE_NAVIGATION;
        if (stabilisationActive) return FlightObjective.STABILISATION;
        return FlightObjective.MANUAL_ASSIST;
    }
}
