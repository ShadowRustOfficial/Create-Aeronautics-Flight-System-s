package com.flightcomputer.control;

import com.flightcomputer.avionics.FlightOperationsState;

import java.util.ArrayList;
import java.util.List;

/** Deterministic base checklist; runtime-specific propulsion checks can append their actual authority results. */
public final class PreflightChecks {
    private PreflightChecks() { }

    public static PreflightCheckResult evaluate(FlightOperationsState operations,
                                                 boolean powered,
                                                 boolean stabiliserLinked,
                                                 boolean brakingLinked,
                                                 boolean coolingAvailable,
                                                 boolean terrainSafety) {
        List<String> pass = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        check(pass, failures, powered, "POWER", "NO POWER");
        check(pass, failures, stabiliserLinked, "STABILISER BANK", "NO STABILISER BANK");
        check(pass, warnings, brakingLinked, "BRAKING BANK", "BRAKING AUTHORITY NOT LINKED");
        check(pass, warnings, coolingAvailable, "COOLING", "NO COOLING UPGRADE INSTALLED");
        check(pass, warnings, terrainSafety && operations.terrainSafety(), "TERRAIN SAFETY", "TERRAIN SAFETY DISABLED");
        if (operations.combatAssist() && operations.combatMode() == com.flightcomputer.avionics.CombatMode.OFFENSIVE
                && operations.offensiveCallsign().isBlank()) failures.add("OFFENSIVE TARGET NOT SET");
        if (operations.combatAssist() && operations.combatMode() == com.flightcomputer.avionics.CombatMode.DEFENSIVE
                && operations.defensiveHome().isBlank()) warnings.add("DEFENSIVE HOME NOT SET");
        return new PreflightCheckResult(failures.isEmpty(), pass, warnings, failures);
    }

    private static void check(List<String> pass, List<String> problems, boolean ok, String success, String failure) {
        if (ok) pass.add(success); else problems.add(failure);
    }
}
