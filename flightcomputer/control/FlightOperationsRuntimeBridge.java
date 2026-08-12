package com.flightcomputer.control;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.FlightHold;
import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.avionics.FlightOperationsState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bridges persistent Operations intent into the live controller/runtime path. */
public final class FlightOperationsRuntimeBridge {
    private static final Pattern COORDINATES = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*[, ]\\s*(-?\\d+(?:\\.\\d+)?)\\s*[, ]\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private FlightOperationsRuntimeBridge() { }

    public static void reconcile(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        if (!(controller instanceof FlightOperationsHolder holder)) return;

        FlightOperationsState operations = holder.getFlightOperations();
        FlightControllerState current = controller.getControllerState();

        // Operations that explicitly own a navigation target update the shared runtime target.
        // Normal route targets are intentionally left alone here so the Route page can keep
        // its destination until the user clears or aborts it.
        if (operations.emergencyReturn()) {
            Vec3 home = parseCoordinates(operations.defensiveHome());
            if (home != null) FlightControlRuntimeManager.setTarget(controller, home, "EMERGENCY RETURN");
        } else if (operations.combatAssist() && operations.combatMode() == com.flightcomputer.avionics.CombatMode.DEFENSIVE) {
            Vec3 home = parseCoordinates(operations.defensiveHome());
            if (home != null) FlightControlRuntimeManager.setTarget(controller, home, "DEFENSIVE HOME");
        }

        if (operations.emergencyReturn()) {
            if (!current.engaged()) controller.applyAction(FlightControllerAction.TOGGLE_ENGAGED);
            if (!controller.getControllerState().stabiliser()) controller.applyAction(FlightControllerAction.TOGGLE_STABILISER);
        }

        // Do not treat an absent Operations hold as an explicit request to disable the
        // Navigation Console's live hold. The previous implementation reconciled false
        // every server tick, so pressing a hold button made it appear to turn ON and then
        // immediately back OFF. Operations can still assert a persistent hold when it is
        // explicitly enabled; the console remains authoritative for disabling it.
        reconcileHoldEnableOnly(controller, operations.hasHold(FlightHold.ALTITUDE), FlightControllerAction.TOGGLE_ALTITUDE_HOLD);
        reconcileHoldEnableOnly(controller, operations.hasHold(FlightHold.HEADING), FlightControllerAction.TOGGLE_HEADING_HOLD);
        reconcileHoldEnableOnly(controller, operations.hasHold(FlightHold.POSITION), FlightControllerAction.TOGGLE_POSITION_HOLD);
        reconcileHoldEnableOnly(controller, operations.hasHold(FlightHold.VELOCITY), FlightControllerAction.TOGGLE_VELOCITY_HOLD);

        if (operations.combatAssist() || operations.landingAssist() || operations.autoDocking()) {
            if (!controller.getControllerState().engaged()) controller.applyAction(FlightControllerAction.TOGGLE_ENGAGED);
            if (!controller.getControllerState().stabiliser()) controller.applyAction(FlightControllerAction.TOGGLE_STABILISER);
        }

        boolean powered = controller.getEnergyStorage().getEnergyStored() > 0 && !controller.isThermalLockout();
        boolean stabiliserLinked = !controller.getVectorLinks(FlightMode.STABILIZE).isEmpty();
        boolean brakingLinked = !controller.getVectorLinks(FlightMode.STABILIZE).isEmpty();
        boolean coolingAvailable = controller.getCoolingTier() != com.flightcomputer.item.CoolingUpgradeItem.Tier.NONE;
        boolean terrainSafety = controller.isTerrainEnabled();
        operations.setPreflightPassed(PreflightChecks.evaluate(operations, powered, stabiliserLinked, brakingLinked, coolingAvailable, terrainSafety).passed());
    }

    private static void reconcileHoldEnableOnly(FlightControllerBlockEntity controller, boolean wanted, FlightControllerAction action) {
        if (!wanted || controller == null || !controller.isOperationPermitted(action)) return;
        boolean current = switch (action) {
            case TOGGLE_ALTITUDE_HOLD -> controller.getControllerState().altitudeHold();
            case TOGGLE_HEADING_HOLD -> controller.getControllerState().headingHold();
            case TOGGLE_POSITION_HOLD -> controller.getControllerState().positionHold();
            case TOGGLE_VELOCITY_HOLD -> controller.getControllerState().velocityHold();
            default -> false;
        };
        if (!current) controller.applyAction(action);
    }

    private static Vec3 parseCoordinates(String value) {
        if (value == null) return null;
        Matcher matcher = COORDINATES.matcher(value);
        if (!matcher.matches()) return null;
        try {
            return new Vec3(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)), Double.parseDouble(matcher.group(3)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
