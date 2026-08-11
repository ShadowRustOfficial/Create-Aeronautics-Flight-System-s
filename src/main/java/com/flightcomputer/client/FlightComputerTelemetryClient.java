package com.flightcomputer.client;

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

/** Client cache and lightweight telemetry/thermal graph overlay. */
@EventBusSubscriber(modid = "flightcomputer", value = Dist.CLIENT)
public final class FlightComputerTelemetryClient {
    private static final Map<UUID, FlightComputerNetwork.TelemetryPayload> SNAPSHOTS = new ConcurrentHashMap<>();
    private FlightComputerTelemetryClient() { }

    public static void accept(FlightComputerNetwork.TelemetryPayload payload) {
        if (payload != null) SNAPSHOTS.put(payload.controllerId(), payload);
    }

    public static FlightComputerNetwork.TelemetryPayload get(UUID id) { return id == null ? null : SNAPSHOTS.get(id); }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof com.flightcomputer.client.gui.NavigationConsoleScreen screen)) return;
        var controller = screen.controllerPos();
        if (mc.level == null || controller == null) return;
        var be = mc.level.getBlockEntity(controller);
        if (!(be instanceof com.flightcomputer.block.FlightControllerBlockEntity flightController)) return;
        FlightComputerNetwork.TelemetryPayload t = get(flightController.getControllerId());
        if (t == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = 270;
        int height = 92;
        int left = Math.max(8, mc.getWindow().getGuiScaledWidth() - width - 12);
        int top = Math.max(8, mc.getWindow().getGuiScaledHeight() - height - 12);
        int thermalColor = switch (t.thermalState()) {
            case 0 -> 0xFF55FF55; case 1 -> 0xFFFFFF55; case 2 -> 0xFFFFAA33; case 3 -> 0xFFFF5555; default -> 0xFFFF2222;
        };
        g.fill(left, top, left + width, top + height, 0xDD0B1116);
        g.fill(left, top, left + width, top + 2, thermalColor);
        g.drawString(mc.font, "LIVE FLIGHT TELEMETRY", left + 8, top + 7, 0xFFE6EEF2);
        g.drawString(mc.font, String.format("ALT %.1f  SPD %.1f  HDG %.1f°", t.y, t.speed, t.heading), left + 8, top + 21, 0xFFBFD0D8);
        g.drawString(mc.font, "THERMAL: " + thermalName(t.thermalState()) + "  " + String.format("%.0f%%", fraction(t.temperature(), t.maxTemperature()) * 100.0D), left + 8, top + 35, thermalColor);
        g.drawString(mc.font, "POWER: " + t.energy() + "/" + t.maxEnergy() + " FE   COOL: " + t.coolingTier(), left + 8, top + 49, 0xFF66D9FF);
        g.drawString(mc.font, "STAB N/E/S/W/U/D", left + 8, top + 63, 0xFF55FF55);
        drawAuthorityBars(g, t, left + 106, top + 62, false);
        g.drawString(mc.font, "AUTO N/E/S/W/U/D", left + 8, top + 77, 0xFF66D9FF);
        drawAuthorityBars(g, t, left + 106, top + 76, true);
    }

    private static void drawAuthorityBars(GuiGraphics g, FlightComputerNetwork.TelemetryPayload t, int x, int y, boolean auto) {
        double[] a = auto ? new double[]{t.autopilotNorth(),t.autopilotEast(),t.autopilotSouth(),t.autopilotWest(),t.autopilotUp(),t.autopilotDown()}
                : new double[]{t.stabiliserNorth(),t.stabiliserEast(),t.stabiliserSouth(),t.stabiliserWest(),t.stabiliserUp(),t.stabiliserDown()};
        double max = 1.0D;
        for (double value : a) max = Math.max(max, value);
        for (int i = 0; i < a.length; i++) {
            int bx = x + i * 25;
            int h = (int) Math.min(12, Math.round((a[i] / max) * 12));
            g.fill(bx, y + 12 - h, bx + 20, y + 12, auto ? 0xFF2D88AA : 0xFF2D8A4A);
        }
    }

    private static double fraction(double value, double max) { return max <= 0 ? 0 : Math.max(0, Math.min(1, value / max)); }
    private static String thermalName(int state) { return switch (state) { case 0 -> "NORMAL"; case 1 -> "WARM"; case 2 -> "HOT"; case 3 -> "CRITICAL"; default -> "COOLING DOWN"; }; }
}
