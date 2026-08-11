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

/**
 * Bridges the persistent Operations tab into the existing, proven controller/runtime path.
 * This intentionally does not replace the propulsion allocator: it supplies intent to it.
 */
public final class FlightOperationsRuntimeBridge {
    private static final Pattern COORDINATES = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*[, ]\\s*(-?\\d+(?:\\.\\d+)?)\\s*[, ]\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private FlightOperationsRuntimeBridge() { }

    public static void reconcile(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        if (!(controller instanceof FlightOperationsHolder holder)) return;

        FlightOperationsState operations = holder.getFlightOperations();
        FlightControllerState current = controller.getControllerState();

        if (operations.emergencyReturn()) {
            Vec3 home = parseCoordinates(operations.defensiveHome());
            if (home != null) FlightControlRuntimeManager.setTarget(controller, home, "EMERGENCY RETURN");
        } else if (operations.combatAssist() && operations.combatMode() == com.flightcomputer.avionics.CombatMode.DEFENSIVE) {
            Vec3 home = parseCoordinates(operations.defensiveHome());
            if (home != null) FlightControlRuntimeManager.setTarget(controller, home, "DEFENSIVE HOME");
        } else {
            FlightControlRuntimeManager.clearTarget(controller);
        }

        // Emergency return deliberately remains an assisted return, not an uncontrolled shutdown.
        if (operations.emergencyReturn()) {
            if (!current.engaged()) controller.applyAction(FlightControllerAction.TOGGLE_ENGAGED);
            if (!controller.getControllerState().stabiliser()) controller.applyAction(FlightControllerAction.TOGGLE_STABILISER);
        }

        reconcileHold(controller, FlightHold.ALTITUDE, operations.hasHold(FlightHold.ALTITUDE), FlightControllerAction.TOGGLE_ALTITUDE_HOLD);
        reconcileHold(controller, FlightHold.HEADING, operations.hasHold(FlightHold.HEADING), FlightControllerAction.TOGGLE_HEADING_HOLD);
        reconcileHold(controller, FlightHold.POSITION, operations.hasHold(FlightHold.POSITION), FlightControllerAction.TOGGLE_POSITION_HOLD);
        reconcileHold(controller, FlightHold.VELOCITY, operations.hasHold(FlightHold.VELOCITY), FlightControllerAction.TOGGLE_VELOCITY_HOLD);

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

    private static void reconcileHold(FlightControllerBlockEntity controller, FlightHold hold, boolean wanted, FlightControllerAction action) {
        boolean current = switch (hold) {
            case ALTITUDE -> controller.getControllerState().altitudeHold();
            case HEADING -> controller.getControllerState().headingHold();
            case POSITION -> controller.getControllerState().positionHold();
            case VELOCITY -> controller.getControllerState().velocityHold();
        };
        if (current != wanted && controller.isOperationPermitted(action)) controller.applyAction(action);
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
