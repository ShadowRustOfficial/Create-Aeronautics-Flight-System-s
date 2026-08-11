package com.flightcomputer.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightComputerNetwork;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Create-Goggles-only Flight Controller diagnostic overlay.
 * It is intentionally independent of NavigationConsoleScreen: opening the main UI can
 * never cause this telemetry panel to bleed into MAP/ROUTE/FLIGHT CONTROL pages.
 */
@EventBusSubscriber(modid = "flightcomputer", value = Dist.CLIENT)
public final class FlightControllerGoggleOverlay {
    private FlightControllerGoggleOverlay() { }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!GogglesItem.isWearingGoggles(mc.player)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        var be = mc.level.getBlockEntity(hit.getBlockPos());
        if (!(be instanceof FlightControllerBlockEntity controller)) return;

        FlightComputerNetwork.TelemetryPayload t = FlightComputerTelemetryClient.get(controller.getControllerId());
        if (t == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = 350;
        int height = 156;
        int left = Math.max(8, g.guiWidth() - width - 12);
        int top = Math.max(8, g.guiHeight() - height - 12);

        int thermalColor = switch (t.thermalState()) {
            case 0 -> 0xFF55FF55;
            case 1 -> 0xFFFFFF55;
            case 2 -> 0xFFFFAA33;
            case 3 -> 0xFFFF5555;
            default -> 0xFFFF2222;
        };
        double thermalFraction = fraction(t.temperature(), t.maxTemperature());

        g.fill(left, top, left + width, top + height, 0xE80B1116);
        g.fill(left, top, left + width, top + 2, 0xFF55D9FF);
        g.drawString(mc.font, "FLIGHT CONTROLLER / GOGGLE DATA", left + 8, top + 7, 0xFFE6EEF2);
        g.drawString(mc.font, "STATE " + (controller.isEngaged() ? "ENGAGED" : "STANDBY")
                + "   STABILISER " + (controller.isStabiliser() ? "ON" : "OFF"), left + 8, top + 21,
                controller.isEngaged() ? 0xFF55FF55 : 0xFF9DAEB5);
        g.drawString(mc.font, "MODE " + controller.getControllerState().flightMode().name()
                + "   AUTOPILOT " + (controller.getControllerState().flightMode().name().equals("AUTOPILOT") ? "ON" : "OFF"),
                left + 8, top + 35, 0xFF66D9FF);
        g.drawString(mc.font, String.format("ALT %.1f  SPD %.1f  HDG %.1f°", t.y(), t.speed(), t.heading()),
                left + 8, top + 49, 0xFFBFD0D8);

        g.drawString(mc.font, "THERMAL " + thermalName(t.thermalState()), left + 8, top + 64, thermalColor);
        g.drawString(mc.font, String.format("TEMP %.1f°C / %.1f°C", t.temperature(), t.maxTemperature()), left + 150, top + 64, thermalColor);
        int barL = left + 8, barT = top + 77, barR = left + 215, barB = top + 85;
        g.fill(barL, barT, barR, barB, 0xFF252B30);
        g.fill(barL, barT, barL + (int) ((barR - barL) * thermalFraction), barB, thermalColor);
        g.drawString(mc.font, "COOLING TIER " + t.coolingTier() + "   "
                + (t.cooldownTicks() > 0 ? "LOCKOUT" : "ACTIVE"), left + 225, top + 77,
                t.cooldownTicks() > 0 ? 0xFFFF5555 : 0xFF66D9FF);

        g.drawString(mc.font, "POWER " + t.energy() + " / " + t.maxEnergy() + " FE", left + 8, top + 94,
                t.energy() > 0 ? 0xFF55FF55 : 0xFFFF5555);
        String target = t.targetPresent() ? t.targetName() : "NONE";
        g.drawString(mc.font, "TARGET " + target, left + 8, top + 108, t.targetPresent() ? 0xFF66D9FF : 0xFF9DAEB5);
        if (t.targetPresent()) {
            g.drawString(mc.font, String.format("DIST %.1f m", t.distance()), left + 220, top + 108, 0xFFE6EEF2);
        }
        g.drawString(mc.font, "CONTROL OUTPUT", left + 8, top + 124, 0xFFE6EEF2);
        drawBars(g, t, left + 112, top + 122, false);
        drawBars(g, t, left + 235, top + 122, true);
    }

    private static void drawBars(GuiGraphics g, FlightComputerNetwork.TelemetryPayload t, int x, int y, boolean auto) {
        double[] values = auto
                ? new double[]{t.autopilotNorth(), t.autopilotEast(), t.autopilotSouth(), t.autopilotWest(), t.autopilotUp(), t.autopilotDown()}
                : new double[]{t.stabiliserNorth(), t.stabiliserEast(), t.stabiliserSouth(), t.stabiliserWest(), t.stabiliserUp(), t.stabiliserDown()};
        double max = 1.0D;
        for (double value : values) max = Math.max(max, Math.abs(value));
        for (int i = 0; i < values.length; i++) {
            int bx = x + i * 19;
            int h = (int) Math.min(10, Math.round(Math.abs(values[i]) / max * 10));
            g.fill(bx, y + 10 - h, bx + 14, y + 10, auto ? 0xFF2D88AA : 0xFF2D8A4A);
        }
    }

    private static double fraction(double value, double max) {
        return max <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, value / max));
    }

    private static String thermalName(int state) {
        return switch (state) {
            case 0 -> "NORMAL";
            case 1 -> "WARM";
            case 2 -> "HOT";
            case 3 -> "CRITICAL";
            default -> "COOLING DOWN";
        };
    }
}
