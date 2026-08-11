package com.flightcomputer.client;

import com.flightcomputer.map.FlightContact;
import com.flightcomputer.map.FlightContactRegistry;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Live diagnostics overlay and contact feed for Flight Computer telemetry. */
@EventBusSubscriber(modid="flightcomputer", value=Dist.CLIENT)
public final class FlightComputerTelemetryClient {
    private static final Map<UUID, FlightComputerNetwork.TelemetryPayload> SNAPSHOTS = new ConcurrentHashMap<>();

    private FlightComputerTelemetryClient() { }

    public static void accept(FlightComputerNetwork.TelemetryPayload payload) {
        if (payload == null) return;
        SNAPSHOTS.put(payload.controllerId(), payload);
        FlightContactRegistry.upsert(new FlightContact(
                payload.controllerId(),
                payload.targetPresent() && !payload.targetName().isBlank() ? payload.targetName() : "",
                payload.targetPresent() ? payload.targetName() : "",
                "",
                payload.x(), payload.y(), payload.z(), payload.speed(), payload.heading(),
                payload.pitch(), payload.roll(), "ACTIVE", System.currentTimeMillis() / 50L));
    }

    public static FlightComputerNetwork.TelemetryPayload get(UUID id) { return id == null ? null : SNAPSHOTS.get(id); }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof com.flightcomputer.client.gui.NavigationConsoleScreen screen)) return;
        var pos = screen.controllerPos();
        if (mc.level == null || pos == null) return;
        var be = mc.level.getBlockEntity(pos);
        if (!(be instanceof com.flightcomputer.block.FlightControllerBlockEntity fc)) return;
        var t = get(fc.getControllerId());
        if (t == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = 360, height = 168;
        int left = Math.max(8, mc.getWindow().getGuiScaledWidth() - width - 12);
        int top = Math.max(8, mc.getWindow().getGuiScaledHeight() - height - 12);
        int thermalColor = switch (t.thermalState()) {
            case 0 -> 0xFF55FF55; case 1 -> 0xFFFFFF55; case 2 -> 0xFFFFAA33; case 3 -> 0xFFFF5555; default -> 0xFFFF2222;
        };
        double thermalFraction = fraction(t.temperature(), t.maxTemperature());
        g.fill(left, top, left + width, top + height, 0xED0B1116);
        g.fill(left, top, left + width, top + 2, thermalColor);
        g.drawString(mc.font, "LIVE FLIGHT TELEMETRY / DIAGNOSTICS", left + 8, top + 7, 0xFFE6EEF2);
        g.drawString(mc.font, String.format("ALT %.1f  SPD %.1f  HDG %.1f°", t.y(), t.speed(), t.heading()), left + 8, top + 21, 0xFFBFD0D8);
        g.drawString(mc.font, "THERMAL MANAGEMENT", left + 8, top + 37, thermalColor);
        g.drawString(mc.font, String.format("TEMP %.1f°C / %.1f°C", t.temperature(), t.maxTemperature()), left + 8, top + 50, thermalColor);
        int barL = left + 8, barT = top + 63, barR = left + 250, barB = top + 72;
        g.fill(barL, barT, barR, barB, 0xFF252B30);
        g.fill(barL, barT, barL + (int) ((barR - barL) * thermalFraction), barB, thermalColor);
        g.drawString(mc.font, String.format("LOAD %d%%   STATE %s", (int) Math.round(thermalFraction * 100), thermalName(t.thermalState())), left + 8, top + 77, thermalColor);
        g.drawString(mc.font, "COOLING TIER " + t.coolingTier() + "   " + (t.cooldownTicks() > 0 ? "LOCKOUT ACTIVE" : "COOLING ACTIVE"), left + 8, top + 91, 0xFF66D9FF);
        g.drawString(mc.font, t.cooldownTicks() > 0 ? String.format("LOCKOUT REMAINING %.1fs", t.cooldownTicks() / 20.0D) : "LOCKOUT NOT ACTIVE", left + 8, top + 105, t.cooldownTicks() > 0 ? 0xFFFF5555 : 0xFF55FF55);
        g.drawString(mc.font, "POWER " + t.energy() + " / " + t.maxEnergy() + " FE", left + 8, top + 119, 0xFFBFD0D8);
        g.drawString(mc.font, "STABILISER", left + 8, top + 135, 0xFF55FFAA); drawBars(g, t, left + 82, top + 134, false);
        g.drawString(mc.font, "AUTOPILOT", left + 8, top + 151, 0xFF55AAFF); drawBars(g, t, left + 82, top + 150, true);
    }

    private static void drawBars(GuiGraphics g, FlightComputerNetwork.TelemetryPayload t, int x, int y, boolean auto) {
        double[] a = auto ? new double[]{t.autopilotNorth(),t.autopilotEast(),t.autopilotSouth(),t.autopilotWest(),t.autopilotUp(),t.autopilotDown()}
                : new double[]{t.stabiliserNorth(),t.stabiliserEast(),t.stabiliserSouth(),t.stabiliserWest(),t.stabiliserUp(),t.stabiliserDown()};
        double max = 1; for (double v : a) max = Math.max(max, v);
        for (int i = 0; i < a.length; i++) { int bx = x + i * 25, h = (int) Math.min(12, Math.round(a[i] / max * 12)); g.fill(bx, y + 12 - h, bx + 20, y + 12, auto ? 0xFF2D88AA : 0xFF2D8A4A); }
    }
    private static double fraction(double v, double m) { return m <= 0 ? 0 : Math.max(0, Math.min(1, v / m)); }
    private static String thermalName(int s) { return switch (s) { case 0 -> "NORMAL"; case 1 -> "WARM"; case 2 -> "HOT"; case 3 -> "CRITICAL"; default -> "COOLING DOWN"; }; }
}
