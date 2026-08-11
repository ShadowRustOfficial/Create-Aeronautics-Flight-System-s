package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.map.FlightControllerWorldPositionResolver;
import com.flightcomputer.client.map.FlightMapDiagnostics;
import com.flightcomputer.client.map.FlightMapMarker;
import com.flightcomputer.client.map.FlightMapPipeline;
import com.flightcomputer.client.map.FlightMapProviderKind;
import com.flightcomputer.client.map.LiveWorldMapProvider;
import com.flightcomputer.client.map.WaystoneMapProvider;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Flight Computer-owned navigation console. No external map-mod integration is required. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }

    private static final int PANEL = 0xE610141A;
    private static final int MAP_BG = 0xFF000000;
    private static final int CYAN = 0xFF55AAFF;
    private static final int CYAN_BRIGHT = 0xFF66D9FF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int TEXT = 0xFFE6EEF2;
    private static final int MUTED = 0xFF9DAEB5;
    private static final int WAYSTONE = 0xFFFFCC55;

    private final BlockPos controllerPos;
    private final LiveWorldMapProvider mapProvider = new LiveWorldMapProvider();
    private final FlightMapPipeline mapPipeline = new FlightMapPipeline(mapProvider);
    private final FlightControllerWorldPositionResolver worldPositionResolver = new FlightControllerWorldPositionResolver();
    private final WaystoneMapProvider waystoneMapProvider = new WaystoneMapProvider();

    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;
    private boolean showFlightMap = true;
    private boolean showWaypoints = true;
    private double mapCenterX;
    private double mapCenterZ;
    private double controllerWorldX;
    private double controllerWorldY;
    private double controllerWorldZ;
    private boolean draggingMap;
    private EditBox targetInput;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
        this.mapCenterX = controllerPos.getX() + 0.5D;
        this.mapCenterZ = controllerPos.getZ() + 0.5D;
        this.controllerWorldX = mapCenterX;
        this.controllerWorldY = controllerPos.getY() + 0.5D;
        this.controllerWorldZ = mapCenterZ;
    }

    @Override
    protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();
        updateControllerWorldPosition();
        if (controller != null) {
            mapCenterX = controllerWorldX;
            mapCenterZ = controllerWorldZ;
        }

        int left = Math.max(10, (width - 640) / 2);
        int top = 20;
        int tabW = 150;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b -> switchTab(Tab.MAP)).bounds(left, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> switchTab(Tab.ROUTE)).bounds(left + 160, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b -> switchTab(Tab.FLIGHT_CONTROL)).bounds(left + 320, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b -> switchTab(Tab.DIAGNOSTICS)).bounds(left + 480, top, tabW, 22).build());
        if (tab == Tab.MAP) initMapControls(left, top);
        if (tab == Tab.ROUTE) initRouteControls(left, top);
        if (tab == Tab.FLIGHT_CONTROL) initFlightControls(left, top);
    }

    private void initMapControls(int left, int top) {
        int y = top + 310;
        int x = left + 20;
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer()).bounds(x, y, 118, 20).build());
        x += 122;
        addRenderableWidget(Button.builder(Component.literal("CENTRE CTRL"), b -> centreController()).bounds(x, y, 104, 20).build());
        y += 24;
        x = left + 20;
        int w = 92;
        int gap = 4;
        addRenderableWidget(Button.builder(terrainLabel(), b -> {
            showTerrain = !showTerrain;
            send(FlightControllerAction.TOGGLE_TERRAIN);
            b.setMessage(terrainLabel());
        }).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(flightMapLabel(), b -> {
            showFlightMap = !showFlightMap;
            b.setMessage(flightMapLabel());
        }).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(waypointLabel(), b -> {
            showWaypoints = !showWaypoints;
            b.setMessage(waypointLabel());
        }).bounds(x, y, 142, 20).build());
        x += 146;
        addRenderableWidget(Button.builder(Component.literal("CLAIMS: ON"), b -> {}).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(Component.literal("PADS: ON"), b -> {}).bounds(x, y, w, 20).build());
    }

    private void initRouteControls(int left, int top) {
        targetInput = new EditBox(font, left + 20, top + 150, 360, 20, Component.literal("Target X Y Z"));
        targetInput.setHint(Component.literal("X Y Z  (example: 120 80 -240)"));
        addRenderableWidget(targetInput);
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION"), b -> sendTargetFromInput()).bounds(left + 390, top + 150, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLEAR DESTINATION"), b -> { FlightComputerNetwork.clearTarget(controllerPos); }).bounds(left + 20, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("START ROUTE"), b -> send(FlightControllerAction.START_ROUTE)).bounds(left + 210, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT ROUTE"), b -> send(FlightControllerAction.ABORT_ROUTE)).bounds(left + 400, top + 180, 180, 20).build());
    }

    private void sendTargetFromInput() {
        if (targetInput == null) return;
        String[] parts = targetInput.getValue().trim().split("\\s+");
        if (parts.length != 3) return;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            FlightComputerNetwork.sendTarget(controllerPos, x, y, z, "CUSTOM DESTINATION");
        } catch (NumberFormatException ignored) { }
    }

    private void initFlightControls(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 30, top + 210, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 225, top + 210, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("MODE SELECT"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 360, top + 210, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 495, top + 210, 120, 20).build());
    }

    private Component terrainLabel() { return Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF")); }
    private Component flightMapLabel() { return Component.literal("FLIGHT MAP: " + (showFlightMap ? "ON" : "OFF")); }
    private Component waypointLabel() { return Component.literal("WAYPOINTS: " + (showWaypoints ? "ON" : "OFF")); }

    private Vec3 resolvePlayerWorldPosition() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return null;
        return worldPositionResolver.resolve(minecraft.level, minecraft.player.position());
    }

    private void centrePlayer() {
        Vec3 playerWorld = resolvePlayerWorldPosition();
        if (playerWorld != null) {
            mapCenterX = playerWorld.x;
            mapCenterZ = playerWorld.z;
        }
    }

    private void centreController() {
        updateControllerWorldPosition();
        mapCenterX = controllerWorldX;
        mapCenterZ = controllerWorldZ;
    }

    private void updateControllerWorldPosition() {
        if (minecraft == null || minecraft.level == null) return;
        Vec3 resolved = worldPositionResolver.resolve(minecraft.level, controllerPos);
        if (resolved == null) return;
        controllerWorldX = resolved.x;
        controllerWorldY = resolved.y;
        controllerWorldZ = resolved.z;
    }

    private void switchTab(Tab next) {
        tab = next;
        clearWidgets();
        init();
    }

    private void send(FlightControllerAction action) { FlightComputerNetwork.sendControllerAction(controllerPos, action); }

    private FlightControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) return null;
        BlockEntity be = minecraft.level.getBlockEntity(controllerPos);
        return be instanceof FlightControllerBlockEntity fc ? fc : null;
    }

    private boolean controllerPowered() {
        if (controller == null) controller = getController();
        return controller != null && controller.getEnergyStorage().getEnergyStored() > 0 && controller.getPowerState() != PowerState.NO_POWER;
    }

    private String linkStatus() {
        return !controllerPowered() ? "OFFLINE" : (controller != null && controller.getLinkedControllerId() != null ? "CONNECTED" : "NOT LINKED");
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;
        if (!controllerPowered()) { minecraft.setScreen(null); return; }
        updateControllerWorldPosition();
        mapPipeline.tick(minecraft.level, 4);
        waystoneMapProvider.tick(minecraft.level);
    }

    @Override
    public void onClose() {
        // Terrain warming continues independently on the client tick. Do not cancel or clear it here.
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(10, (width - 640) / 2);
        int top = 20;
        int right = left + 640;
        g.fill(left - 8, top - 8, right + 8, Math.min(height - 8, top + 355), PANEL);
        g.drawString(font, "◈ NAVIGATION CONSOLE", left, top - 2, TEXT);
        g.drawString(font, "LINK: " + linkStatus(), right - 140, top - 2, controllerPowered() ? GREEN : RED);
        switch (tab) {
            case MAP -> renderMap(g, left, top + 42);
            case ROUTE -> renderRoute(g, left, top + 42);
            case FLIGHT_CONTROL -> renderFlightControl(g, left, top + 42);
            case DIAGNOSTICS -> renderDiagnostics(g, left, top + 42);
        }
        super.render(g, mouseX, mouseY, partialTick);
        drawAccents(g, left, top);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    private void drawAccents(GuiGraphics g, int left, int top) {
        int activeX = switch (tab) {
            case MAP -> left;
            case ROUTE -> left + 160;
            case FLIGHT_CONTROL -> left + 320;
            case DIAGNOSTICS -> left + 480;
        };
        g.fill(activeX, top + 20, activeX + 150, top + 22, CYAN_BRIGHT);
        if (tab == Tab.MAP) {
            g.hLine(mapLeft(), mapRight(), mapTop(), CYAN);
            g.hLine(mapLeft(), mapRight(), mapBottom(), CYAN);
            g.vLine(mapLeft(), mapTop(), mapBottom(), CYAN);
            g.vLine(mapRight(), mapTop(), mapBottom(), CYAN);
        }
    }

    private void renderMap(GuiGraphics g, int left, int top) {
        int mapL = left + 20, mapT = top + 8, mapR = left + 620, mapB = top + 268;
        int mapWidth = mapR - mapL, mapHeight = mapB - mapT;
        g.fill(mapL, mapT, mapR, mapB, MAP_BG);

        g.enableScissor(mapL, mapT, mapR, mapB);
        if (showTerrain && minecraft != null && minecraft.level != null) renderTerrain(g, minecraft.level, mapL, mapT, mapR, mapB);
        if (showFlightMap) renderPositionOverlay(g, mapL, mapT, mapWidth, mapHeight);
        if (showWaypoints) renderWaystoneMarkers(g, mapL, mapT, mapWidth, mapHeight);
        g.disableScissor();

        FlightMapDiagnostics d = mapPipeline.diagnostics();
        boolean online = showTerrain && d.provider() == FlightMapProviderKind.NATIVE_JOURNEYMAP_INSPIRED;
        g.drawString(font, "NATIVE TERRAIN: " + (online ? "ONLINE" : "OFFLINE"), mapL + 8, mapT + 8, online ? GREEN : RED);
        if (showWaypoints && waystoneMapProvider.isAvailable()) {
            g.drawString(font, "WAYSTONES: " + waystoneMapProvider.markers().size(), mapL + 8, mapT + 20, WAYSTONE);
        }
        g.drawString(font, String.format("CENTRE X %.1f   Z %.1f", mapCenterX, mapCenterZ), mapL + 8, mapB - 30, MUTED);
        g.drawString(font, "DRAG TO PAN | 1 BLOCK/PIXEL", mapL + 8, mapB - 14, MUTED);
    }

    private void renderTerrain(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.multiplayer.ClientLevel level, int left, int top, int right, int bottom) {
        final double scale = 1.0D;
        final int sourceStep = 2;
        final int tilePixels = 16;
        int minChunkX = (int)Math.floor((mapCenterX - (right - left) / (2.0D * scale)) / 16.0D) - 1;
        int maxChunkX = (int)Math.floor((mapCenterX + (right - left) / (2.0D * scale)) / 16.0D) + 1;
        int minChunkZ = (int)Math.floor((mapCenterZ - (bottom - top) / (2.0D * scale)) / 16.0D) - 1;
        int maxChunkZ = (int)Math.floor((mapCenterZ + (bottom - top) / (2.0D * scale)) / 16.0D) + 1;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int[] tile = mapPipeline.getCachedTile(level, chunkX, chunkZ);
                int px = (int)Math.floor(left + (chunkX * tilePixels - mapCenterX) * scale + (right - left) / 2.0D);
                int py = (int)Math.floor(top + (chunkZ * tilePixels - mapCenterZ) * scale + (bottom - top) / 2.0D);
                if (tile == null) {
                    g.fill(px, py, px + tilePixels, py + tilePixels, 0xFF171B1E);
                    continue;
                }
                for (int sy = 0; sy < tilePixels; sy += sourceStep) {
                    int runStart = 0;
                    int runColor = tile[sy * tilePixels];
                    for (int sx = sourceStep; sx <= tilePixels; sx += sourceStep) {
                        int color = sx < tilePixels ? tile[sy * tilePixels + sx] : Integer.MIN_VALUE;
                        if (color != runColor) {
                            g.fill(px + runStart, py + sy, px + sx, py + sy + sourceStep, runColor);
                            runStart = sx;
                            runColor = color;
                        }
                    }
                }
            }
        }
    }

    private void renderPositionOverlay(GuiGraphics g, int left, int top, int width, int height) {
        Vec3 playerWorld = resolvePlayerWorldPosition();
        if (playerWorld != null) {
            int playerX = worldToScreenX(playerWorld.x, left, width);
            int playerZ = worldToScreenZ(playerWorld.z, top, height);
            drawTriangle(g, playerX, playerZ, 4, RED);
        }
        int controllerX = worldToScreenX(controllerWorldX, left, width);
        int controllerZ = worldToScreenZ(controllerWorldZ, top, height);
        drawDiamond(g, controllerX, controllerZ, 4, CYAN_BRIGHT);
    }

    private void renderWaystoneMarkers(GuiGraphics g, int left, int top, int width, int height) {
        for (FlightMapMarker marker : waystoneMapProvider.markers()) {
            int x = worldToScreenX(marker.worldX(), left, width);
            int z = worldToScreenZ(marker.worldZ(), top, height);
            drawDiamond(g, x, z, 3, WAYSTONE);
            if (Math.abs(x - (left + width / 2)) < width / 2 && Math.abs(z - (top + height / 2)) < height / 2) {
                g.drawString(font, marker.label(), x + 5, z - 4, WAYSTONE);
            }
        }
    }

    private int worldToScreenX(double x, int left, int width) { return (int)Math.round(left + width / 2.0D + (x - mapCenterX)); }
    private int worldToScreenZ(double z, int top, int height) { return (int)Math.round(top + height / 2.0D + (z - mapCenterZ)); }

    private static void drawTriangle(GuiGraphics g, int x, int y, int radius, int color) {
        for (int row = 0; row <= radius; row++) g.fill(x - row, y - radius + row, x + row + 1, y - radius + row + 1, color);
    }

    private static void drawDiamond(GuiGraphics g, int x, int y, int radius, int color) {
        g.fill(x, y - radius, x + 1, y + radius + 1, color);
        for (int row = 1; row <= radius; row++) {
            g.fill(x - row, y - row, x + row + 1, y - row + 1, color);
            g.fill(x - row, y + row - 1, x + row + 1, y + row, color);
        }
    }

    private void renderRoute(GuiGraphics g, int left, int top) {
        g.drawString(font, "ROUTE / FLIGHT PLAN", left + 20, top + 10, TEXT);
        var snapshot = controller == null ? null : com.flightcomputer.client.FlightComputerTelemetryClient.get(controller.getControllerId());
        if (snapshot == null || !snapshot.targetPresent()) {
            g.drawString(font, "DESTINATION: NONE", left + 20, top + 48, MUTED);
            g.drawString(font, "Enter world coordinates above to create a smooth MPC destination.", left + 20, top + 76, MUTED);
            return;
        }
        g.drawString(font, "DESTINATION: " + snapshot.targetName(), left + 20, top + 48, CYAN_BRIGHT);
        g.drawString(font, String.format("POS  X %.1f  Y %.1f  Z %.1f", snapshot.targetX(), snapshot.targetY(), snapshot.targetZ()), left + 20, top + 72, TEXT);
        g.drawString(font, String.format("DISTANCE %.1f m   BEARING %.1f°   SPEED %.1f m/s", snapshot.distance(), snapshot.heading(), snapshot.speed()), left + 20, top + 96, TEXT);
        double eta = snapshot.speed() > 0.1 ? snapshot.distance() / snapshot.speed() : -1;
        g.drawString(font, eta < 0 ? "ETA: CALCULATING" : String.format("ETA %.1f s   ROUTE: SMOOTH ACCEL / DECEL", eta), left + 20, top + 120, eta < 0 ? MUTED : GREEN);
    }

    private void renderFlightControl(GuiGraphics g, int left, int top) {
        FlightControllerState state = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        var snapshot = controller == null ? null : com.flightcomputer.client.FlightComputerTelemetryClient.get(controller.getControllerId());
        g.drawString(font, "FLIGHT CONTROL", left + 20, top + 10, TEXT);
        g.drawString(font, "SYSTEM: " + (state.engaged() ? "ENGAGED" : "DISENGAGED"), left + 20, top + 42, state.engaged() ? GREEN : MUTED);
        g.drawString(font, "STABILISER + AUTOPILOT: " + (state.engaged() ? "CONCURRENT" : "STANDBY"), left + 20, top + 65, CYAN_BRIGHT);
        if (snapshot != null) {
            g.drawString(font, String.format("ALT %.1f   SPEED %.1f   HEADING %.1f°", snapshot.y(), snapshot.speed(), snapshot.heading()), left + 20, top + 88, TEXT);
            g.drawString(font, "THERMAL: " + (snapshot.thermalState() >= 4 ? "COOLING DOWN" : snapshot.thermalState() == 3 ? "CRITICAL" : snapshot.thermalState() == 2 ? "HOT" : snapshot.thermalState() == 1 ? "WARM" : "NORMAL"), left + 20, top + 112, snapshot.thermalState() >= 2 ? RED : GREEN);
            g.drawString(font, "TARGET: " + (snapshot.targetPresent() ? snapshot.targetName() : "NONE"), left + 20, top + 136, snapshot.targetPresent() ? CYAN_BRIGHT : MUTED);
        }
    }

    private void renderDiagnostics(GuiGraphics g, int left, int top) {
        boolean powered = controllerPowered();
        long energy = controller == null ? 0L : controller.getEnergyStorage().getEnergyStored();
        long capacity = controller == null ? 0L : controller.getEnergyStorage().getMaxEnergyStored();
        PowerState powerState = controller == null ? PowerState.NO_POWER : controller.getPowerState();
        FlightMapDiagnostics d = mapPipeline.diagnostics();
        String powerLabel = powerState == PowerState.NO_POWER ? "CRITICAL" : energy <= 0 ? "CRITICAL" : energy < Math.max(1L, capacity / 10L) ? "LOW" : energy < Math.max(1L, capacity / 2L) ? "MEDIUM" : "GOOD";
        int powerColor = powerLabel.equals("CRITICAL") || powerLabel.equals("LOW") ? RED : GREEN;
        g.drawString(font, "DIAGNOSTICS", left + 20, top + 10, TEXT);
        g.drawString(font, "FLIGHT COMPUTER", left + 20, top + 42, TEXT);
        g.drawString(font, powered ? "• OPERATIONAL" : "• OFFLINE", left + 265, top + 42, powered ? GREEN : RED);
        g.drawString(font, "LINK", left + 20, top + 65, TEXT);
        g.drawString(font, "• " + linkStatus(), left + 265, top + 65, powered ? GREEN : RED);
        g.drawString(font, "ENERGY", left + 20, top + 88, TEXT);
        g.drawString(font, formatEnergy(energy) + " / " + formatEnergy(capacity) + " FE", left + 265, top + 88, energy > 0 ? GREEN : RED);
        g.drawString(font, "POWER LEVEL", left + 20, top + 111, TEXT);
        g.drawString(font, powerLabel, left + 265, top + 111, powerColor);
        g.drawString(font, "MAP ENGINE", left + 20, top + 150, CYAN_BRIGHT);
        g.drawString(font, "NATIVE CPU TERRAIN", left + 90, top + 150, GREEN);
        g.drawString(font, "REQUESTED: " + d.requestedCount() + "  PENDING: " + d.pendingCount(), left + 20, top + 174, MUTED);
        g.drawString(font, "CACHE HITS: " + d.cacheHits() + "  MISSES: " + d.cacheMisses(), left + 20, top + 198, MUTED);
        g.drawString(font, "DECODED: " + d.decodedCount() + "  FAILED: " + d.failedCount(), left + 20, top + 222, MUTED);
        g.drawString(font, String.format("WORLD X  %.2f", controllerWorldX), left + 405, top + 218, CYAN_BRIGHT);
        g.drawString(font, String.format("WORLD Y  %.2f", controllerWorldY), left + 405, top + 240, CYAN_BRIGHT);
        g.drawString(font, String.format("WORLD Z  %.2f", controllerWorldZ), left + 405, top + 262, CYAN_BRIGHT);
    }

    private String formatEnergy(long value) { return String.format("%,d", Math.max(0L, value)); }
    private int mapLeft() { return Math.max(10, (width - 640) / 2) + 20; }
    private int mapTop() { return 70; }
    private int mapWidth() { return 600; }
    private int mapHeight() { return 260; }
    private int mapRight() { return mapLeft() + mapWidth(); }
    private int mapBottom() { return mapTop() + mapHeight(); }
    private boolean isInsideMap(double x, double y) { return x >= mapLeft() && x < mapRight() && y >= mapTop() && y < mapBottom(); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.MAP && button == 0 && isInsideMap(mouseX, mouseY)) { draggingMap = true; return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.MAP && draggingMap && button == 0) { mapCenterX -= dragX; mapCenterZ -= dragY; return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean isPauseScreen() { return false; }
}
