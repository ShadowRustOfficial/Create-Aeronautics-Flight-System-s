package com.flightcomputer.mixin.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.network.FlightComputerNetwork;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/**
 * Object-scoped Flight Controller diagnostics for Create Engineer's Goggles.
 *
 * This intentionally does not draw a normal HUD. Create's goggle overlay invokes this only when
 * the player is actually looking at the Flight Controller while wearing goggles.
 */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerGoggleMixin implements IHaveGoggleInformation {
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        FlightComputerNetwork.TelemetryPayload telemetry = FlightComputerTelemetryClient.get(controller.getControllerId());

        tooltip.add(Component.literal("LIVE FLIGHT TELEMETRY / DIAGNOSTICS").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("SYSTEM: " + (controller.isEngaged() ? "ENGAGED" : "STANDBY"))
                .withStyle(controller.isEngaged() ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        if (telemetry == null) {
            tooltip.add(Component.literal("Telemetry: waiting for server snapshot...").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT,
                    "ALT %.1f  SPD %.2f m/s  HDG %.1f°",
                    telemetry.y(), telemetry.speed(), normalizeDegrees(telemetry.heading())))
                    .withStyle(ChatFormatting.WHITE));

            if (telemetry.targetPresent()) {
                double dx = telemetry.targetX() - telemetry.x();
                double dz = telemetry.targetZ() - telemetry.z();
                double bearing = Math.toDegrees(Math.atan2(dx, dz));
                if (bearing < 0) bearing += 360.0;
                tooltip.add(Component.literal(String.format(java.util.Locale.ROOT,
                        "TARGET %s  DIST %.1f m  BRG %.1f°",
                        telemetry.targetName().isBlank() ? "UNKNOWN" : telemetry.targetName(),
                        telemetry.distance(), bearing)).withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.literal("TARGET: NONE").withStyle(ChatFormatting.GRAY));
            }

            double thermalFraction = telemetry.maxTemperature() <= 0 ? 0
                    : telemetry.temperature() / telemetry.maxTemperature();
            ChatFormatting thermalStyle = thermalFraction >= 1.0 ? ChatFormatting.RED
                    : thermalFraction >= 0.85 ? ChatFormatting.RED
                    : thermalFraction >= 0.65 ? ChatFormatting.GOLD
                    : ChatFormatting.GREEN;
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT,
                    "THERMAL %.1f / %.1f  LOAD %d%%",
                    telemetry.temperature(), telemetry.maxTemperature(),
                    Math.round(Math.max(0, Math.min(1, thermalFraction)) * 100))).withStyle(thermalStyle));
            tooltip.add(Component.literal("POWER " + telemetry.energy() + " / " + telemetry.maxEnergy()
                    + " FE   COOLING TIER " + telemetry.coolingTier()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("STABILISER " + commandSummary(telemetry, false))
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("AUTOPILOT " + commandSummary(telemetry, true))
                    .withStyle(ChatFormatting.BLUE));
        }

        tooltip.add(Component.literal("Goggle diagnostics only — no global HUD overlay")
                .withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private static String commandSummary(FlightComputerNetwork.TelemetryPayload t, boolean autopilot) {
        double max = 0.0;
        double sum = 0.0;
        if (autopilot) {
            double[] values = {t.autopilotNorth(), t.autopilotEast(), t.autopilotSouth(),
                    t.autopilotWest(), t.autopilotUp(), t.autopilotDown()};
            for (double value : values) { max = Math.max(max, Math.abs(value)); sum += Math.abs(value); }
        } else {
            double[] values = {t.stabiliserNorth(), t.stabiliserEast(), t.stabiliserSouth(),
                    t.stabiliserWest(), t.stabiliserUp(), t.stabiliserDown()};
            for (double value : values) { max = Math.max(max, Math.abs(value)); sum += Math.abs(value); }
        }
        return String.format(java.util.Locale.ROOT, "MAX %.2f / SUM %.2f", max, sum);
    }

    private static double normalizeDegrees(double degrees) {
        double value = degrees % 360.0;
        return value < 0 ? value + 360.0 : value;
    }
}
