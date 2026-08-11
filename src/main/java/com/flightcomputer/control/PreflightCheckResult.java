package com.flightcomputer.control;

import java.util.List;

/** Actionable pre-flight result used by diagnostics and future launch/route gates. */
public record PreflightCheckResult(boolean passed, List<String> passedChecks, List<String> warnings, List<String> failures) {
    public PreflightCheckResult {
        passedChecks = List.copyOf(passedChecks == null ? List.of() : passedChecks);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        failures = List.copyOf(failures == null ? List.of() : failures);
    }
}
