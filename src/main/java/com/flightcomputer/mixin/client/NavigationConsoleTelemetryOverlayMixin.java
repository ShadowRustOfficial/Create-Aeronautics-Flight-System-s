package com.flightcomputer.mixin.client;

import com.flightcomputer.avionics.FlightMode;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.client.FlightRouteTelemetryClient;
import com.flightcomputer.client.FlightSetupTelemetryClient;
import com.flightcomputer.client.map.FlightMapDiagnostics;
import com.flightcomputer.client.map.FlightMapPipeline;
import com.flightcomputer.client.map.WaypointMapProvider;
import com.flightcomputer.client.map.WaystoneMapProvider;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.network.FlightRouteTelemetryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * Keeps the original Navigation Console controls untouched while restoring the full live Route
 * and Diagnostics information from the authoritative runtime telemetry caches.
 *
 * The target screen inherits its Font/Minecraft fields from Screen, so they must not be @Shadowed
 * here. Resolve them through Minecraft.getInstance() instead.
 */
@Mixin(com.flightcomputer.client.gui.NavigationConsoleScreen.class)
public abstract class NavigationConsoleTelemetryOverlayMixin {
    @Shadow @Final private FlightMapPipeline mapPipeline;
    @Shadow @Final private WaystoneMapProvider routeWaystones;
    @Shadow @Final private WaypointMapProvider routeWaypoints;
    @Shadow private FlightControllerBlockEntity controller;

    private static final int CYAN = 0xFF55AAFF, GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555, TEXT = 0xFFE6EEF2, MUTED = 0xFF9DAEB5, YELLOW = 0xFFFFCC55;

    @Inject(method = "renderRoute", at = @At("HEAD"), cancellable = true)
    private void flightComputer$renderLiveRoute(GuiGraphics g, int l, int top, CallbackInfo ci) {
        ci.cancel();
        Font font = Minecraft.getInstance().font;
        g.drawString(font, "ROUTE / FLIGHT PLAN", l, top + 18, TEXT);

        FlightComputerNetwork.TelemetryPayload flight = FlightComputerTelemetryClient.get(controller == null ? null : controller.getControllerId());
        FlightRouteTelemetryNetwork.RouteStatePayload route = FlightRouteTelemetryClient.get(controller == null ? null : controller.getControllerId());

        boolean targetPresent = route != null ? route.targetPresent() : flight != null && flight.targetPresent();
        String targetName = route != null && !route.targetName().isBlank()
                ? route.targetName()
                : flight != null && !flight.targetName().isBlank() ? flight.targetName() : "NAVIGATION TARGET";

        if (flight != null && targetPresent) {
            g.drawString(font, "DESTINATION: " + targetName, l, top + 52, CYAN);
            g.drawString(font, String.format(Locale.ROOT, "CURRENT  X %.1f  Y %.1f  Z %.1f", flight.x(), flight.y(), flight.z()), l, top + 76, TEXT);
            double tx = route != null ? route.targetX() : flight.targetX();
            double ty = route != null ? route.targetY() : flight.targetY();
            double tz = route != null ? route.targetZ() : flight.targetZ();
            g.drawString(font, String.format(Locale.ROOT, "TARGET   X %.1f  Y %.1f  Z %.1f", tx, ty, tz), l, top + 100, TEXT);
            double bearing = Math.toDegrees(Math.atan2(tx - flight.x(), tz - flight.z()));
            if (bearing < 0.0D) bearing += 360.0D;
            g.drawString(font, String.format(Locale.ROOT,
                    "ALT %.1f m   DIST %.1f m   BRG %.1f°   HDG %.1f°   SPEED %.2f m/s",
                    flight.y(), flight.distance(), bearing, flight.heading(), flight.speed()), l, top + 124, TEXT);
        } else {
            g.drawString(font, "NO ACTIVE NAVIGATION TARGET", l, top + 52, MUTED);
        }

        boolean routeActive = route != null ? route.routeActive() : controller != null && controller.getControllerState().routeActive();
        boolean navigation = route != null ? route.navigationEnabled() : controller != null && controller.getControllerState().navigationEnabled();
        boolean autopilot = route != null && route.mode() == FlightMode.AUTOPILOT.ordinal()
                || route == null && controller != null && controller.getControllerState().flightMode() == FlightMode.AUTOPILOT;
        String mode = route == null ? (controller == null ? "UNKNOWN" : controller.getControllerState().flightMode().name())
                : modeName(route.mode());

        g.drawString(font, "MODE: " + mode + "   ROUTE: " + (routeActive ? "ACTIVE" : "IDLE")
                + "   NAVIGATION: " + (navigation ? "ON" : "OFF"), l, top + 148,
                autopilot && targetPresent ? GREEN : MUTED);
        g.drawString(font, "AUTOPILOT LINK: " + (autopilot && targetPresent ? "GUIDANCE ACTIVE" : autopilot ? "WAITING FOR TARGET" : "OFF"), l, top + 172,
                autopilot && targetPresent ? GREEN : autopilot ? YELLOW : MUTED);
        g.drawString(font, "WAYSTONES: " + routeWaystones.markers().size(), l, top + 198, YELLOW);
        g.drawString(font, "WAYPOINTS: " + routeWaypoints.markers().size(), l + 160, top + 198, CYAN);
    }

    @Inject(method = "renderDiagnostics", at = @At("HEAD"), cancellable = true)
    private void flightComputer$renderExpandedDiagnostics(GuiGraphics g, int l, int top, CallbackInfo ci) {
        ci.cancel();
        Font font = Minecraft.getInstance().font;
        g.drawString(font, "DIAGNOSTICS", l, top + 18, TEXT);

        FlightMapDiagnostics map = mapPipeline.diagnostics();
        g.drawString(font, "MAP PROVIDER: " + map.provider(), l, top + 42, CYAN);
        g.drawString(font, "MAP STATE: " + map.state(), l + 300, top + 42, map.state().name().contains("FAIL") ? RED : CYAN);
        g.drawString(font, "CACHE HITS " + map.cacheHits() + " | MISSES " + map.cacheMisses(), l, top + 62, TEXT);
        g.drawString(font, "REQUESTED " + map.requestedCount() + " | PENDING " + map.pendingCount(), l + 300, top + 62, TEXT);
        g.drawString(font, "DECODED " + map.decodedCount() + " | READY " + map.readyCount() + " | UPLOADED " + map.uploadedCount(), l, top + 82, TEXT);
        g.drawString(font, "FAILED " + map.failedCount() + " | RETRIED " + map.retryCount() + " | DROPPED " + map.droppedCount(), l + 300, top + 82, map.failedCount() > 0 ? YELLOW : TEXT);
        g.drawString(font, "LAST ERROR: " + map.lastError(), l, top + 102, map.failedCount() > 0 ? RED : MUTED);
        g.drawString(font, "RENDER STATE: " + (map.renderStateClean() ? "CLEAN" : "DEGRADED"), l, top + 122, map.renderStateClean() ? GREEN : RED);

        FlightComputerNetwork.TelemetryPayload flight = FlightComputerTelemetryClient.get(controller == null ? null : controller.getControllerId());
        FlightRouteTelemetryNetwork.RouteStatePayload route = FlightRouteTelemetryClient.get(controller == null ? null : controller.getControllerId());
        if (flight != null) {
            g.drawString(font, String.format(Locale.ROOT, "POSITION X %.1f  Y %.1f  Z %.1f", flight.x(), flight.y(), flight.z()), l, top + 148, TEXT);
            g.drawString(font, String.format(Locale.ROOT, "SPEED %.2f m/s  HEADING %.1f°  PITCH %.1f°  ROLL %.1f°", flight.speed(), flight.heading(), flight.pitch(), flight.roll()), l, top + 168, TEXT);
            g.drawString(font, "TARGET: " + (flight.targetPresent() ? flight.targetName() : "NONE") + "  DIST: "
                    + String.format(Locale.ROOT, "%.1f", flight.distance()), l, top + 188, flight.targetPresent() ? CYAN : MUTED);
            g.drawString(font, String.format(Locale.ROOT, "THERMAL %.1f / %.1f   ENERGY %d / %d   COOLING TIER %d",
                    flight.temperature(), flight.maxTemperature(), flight.energy(), flight.maxEnergy(), flight.coolingTier()), l, top + 208, TEXT);
            g.drawString(font, "STABILISER VECTORS N/E/S/W/U/D: " + vectorSummary(
                    flight.stabiliserNorth(), flight.stabiliserEast(), flight.stabiliserSouth(), flight.stabiliserWest(), flight.stabiliserUp(), flight.stabiliserDown()), l, top + 228, MUTED);
            g.drawString(font, "AUTOPILOT VECTORS N/E/S/W/U/D: " + vectorSummary(
                    flight.autopilotNorth(), flight.autopilotEast(), flight.autopilotSouth(), flight.autopilotWest(), flight.autopilotUp(), flight.autopilotDown()), l, top + 248, MUTED);
        } else {
            g.drawString(font, "LIVE TELEMETRY: WAITING", l, top + 148, YELLOW);
        }

        if (route != null) {
            g.drawString(font, "CONTROL MODE: " + modeName(route.mode()) + " | ENGAGED " + on(route.engaged()) + " | STABILISER " + on(route.stabiliser()), l, top + 270, CYAN);
            g.drawString(font, "HOLDS A/H/P/V: " + on(route.altitudeHold()) + "/" + on(route.headingHold()) + "/" + on(route.positionHold()) + "/" + on(route.velocityHold()), l, top + 290, TEXT);
            g.drawString(font, "NAVIGATION: " + on(route.navigationEnabled()) + " | ROUTE: " + on(route.routeActive()) + " | TARGET: " + on(route.targetPresent()), l, top + 310, TEXT);
        }

        var setup = FlightSetupTelemetryClient.get(controller == null ? null : controller.getControllerId());
        if (setup != null) {
            g.drawString(font, String.format(Locale.ROOT, "SETUP MASS %.1f  ENVELOPE %.1fx%.1f  HOVER %.1f%%  LIFT MARGIN %.1f%%",
                    setup.mass(), setup.envelopeDiameter(), setup.envelopeHeight(), setup.hoverFraction() * 100.0D, setup.liftMargin() * 100.0D), l, top + 332, TEXT);
            g.drawString(font, "PROPULSION UP THRUSTERS " + setup.upwardThrusterCount() + " | MAX "
                    + String.format(Locale.ROOT, "%.1f", setup.verticalMaxThrust()) + " | REQUIRED/THRUSTER "
                    + String.format(Locale.ROOT, "%.1f", setup.recommendedOutputPerThruster()), l, top + 352, TEXT);
        }
    }

    private static String on(boolean value) { return value ? "ON" : "OFF"; }

    private static String vectorSummary(double north, double east, double south, double west, double up, double down) {
        return String.format(Locale.ROOT, "%.0f%%/%.0f%%/%.0f%%/%.0f%%/%.0f%%/%.0f%%",
                north * 100.0D, east * 100.0D, south * 100.0D, west * 100.0D, up * 100.0D, down * 100.0D);
    }

    private static String modeName(int ordinal) {
        FlightMode[] modes = FlightMode.values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal].name() : "UNKNOWN";
    }
}
