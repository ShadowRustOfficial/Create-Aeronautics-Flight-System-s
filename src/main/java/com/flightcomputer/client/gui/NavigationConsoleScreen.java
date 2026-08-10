package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.map.FlightMapDiagnostics;
import com.flightcomputer.client.map.FlightMapPipeline;
import com.flightcomputer.client.map.FlightMapProviderKind;
import com.flightcomputer.client.map.LiveWorldMapProvider;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

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

    private final BlockPos controllerPos;
    private final LiveWorldMapProvider mapProvider = new LiveWorldMapProvider();
    private final FlightMapPipeline mapPipeline = new FlightMapPipeline(mapProvider);

    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;
    private boolean showFlightMap = true;
    private double mapCenterX;
    private double mapCenterZ;
    private boolean draggingMap;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
        this.mapCenterX = controllerPos.getX() + 0.5D;
        this.mapCenterZ = controllerPos.getZ() + 0.5D;
    }

    @Override
    protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();
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
        addRenderableWidget(Button.builder(Component.literal("WAYPOINTS: DEFERRED"), b -> {}).bounds(x, y, 142, 20).build());
        x += 146;
        addRenderableWidget(Button.builder(Component.literal("CLAIMS: ON"), b -> {}).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(Component.literal("PADS: ON"), b -> {}).bounds(x, y, w, 20).build());
    }

    private void initRouteControls(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("WAYPOINT PREVIOUS"), b -> {}).bounds(left + 20, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NEXT ▶"), b -> {}).bounds(left + 210, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION"), b -> {}).bounds(left + 400, top + 180, 180, 20).build());
    }

    private void initFlightControls(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 30, top + 210, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 225, top + 210, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("MODE SELECT"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 360, top + 210, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 495, top + 210, 120, 20).build());
    }

    private Component terrainLabel() { return Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF")); }
    private Component flightMapLabel() { return Component.literal("FLIGHT MAP: " + (showFlightMap ? "ON" : "OFF")); }

    private void centrePlayer() {
        if (minecraft != null && minecraft.player != null) {
            mapCenterX = minecraft.player.getX();
            mapCenterZ = minecraft.player.getZ();
        }
    }

    private void centreController() {
        mapCenterX = controllerPos.getX() + 0.5D;
        mapCenterZ = controllerPos.getZ() + 0.5D;
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
        mapPipeline.tick(minecraft.level, 4);
    }

    @Override
    public void onClose() {
        mapProvider.clear();
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
        if (showTerrain && minecraft != null && minecraft.level != null) renderTerrain(g, minecraft.level, mapL, mapT, mapR, mapB);
        if (showFlightMap) renderPositionOverlay(g, mapL, mapT, mapWidth, mapHeight);
        FlightMapDiagnostics d = mapPipeline.diagnostics();
        boolean online = showTerrain && d.provider() == FlightMapProviderKind.NATIVE_JOURNEYMAP_INSPIRED;
        g.drawString(font, "NATIVE TERRAIN: " + (online ? "ONLINE" : "OFFLINE"), mapL + 8, mapT + 8, online ? GREEN : RED);
        g.drawString(font, String.format("CENTRE X %.1f   Z %.1f", mapCenterX, mapCenterZ), mapL + 8, mapB - 30, MUTED);
        g.drawString(font, "DRAG TO PAN | 1 BLOCK/PIXEL", mapL + 8, mapB - 14, MUTED);
    }

    private void renderTerrain(GuiGraphics g, net.minecraft.client.multiplayer.ClientLevel level, int left, int top, int right, int bottom) {
        final double scale = 1.0D;
        int minChunkX = (int)Math.floor((mapCenterX - (right - left) / (2.0D * scale)) / 16.0D) - 1;
        int maxChunkX = (int)Math.floor((mapCenterX + (right - left) / (2.0D * scale)) / 16.0D) + 1;
        int minChunkZ = (int)Math.floor((mapCenterZ - (bottom - top) / (2.0D * scale)) / 16.0D) - 1;
        int maxChunkZ = (int)Math.floor((mapCenterZ + (bottom - top) / (2.0D * scale)) / 16.0D) + 1;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int[] tile = mapPipeline.getCachedTile(level, chunkX, chunkZ);
                int px = (int)Math.floor(left + (chunkX * 16.0D - mapCenterX) * scale + (right - left) / 2.0D);
                int py = (int)Math.floor(top + (chunkZ * 16.0D - mapCenterZ) * scale + (bottom - top) / 2.0D);
                if (tile == null) {
                    g.fill(px, py, px + 16, py + 16, 0xFF171B1E);
                    continue;
                }
                for (int sy = 0; sy < 16; sy += 4) {
                    for (int sx = 0; sx < 16; sx += 4) {
                        g.fill(px + sx, py + sy, px + sx + 4, py + sy + 4, tile[sy * 16 + sx]);
                    }
                }
            }
        }
    }

    private void renderPositionOverlay(GuiGraphics g, int left, int top, int width, int height) {
        if (minecraft == null || minecraft.player == null) return;
        int playerX = worldToScreenX(minecraft.player.getX(), left, width);
        int playerZ = worldToScreenZ(minecraft.player.getZ(), top, height);
        drawTriangle(g, playerX, playerZ, 4, RED);
        int controllerX = worldToScreenX(controllerPos.getX() + 0.5D, left, width);
        int controllerZ = worldToScreenZ(controllerPos.getZ() + 0.5D, top, height);
        drawDiamond(g, controllerX, controllerZ, 4, CYAN_BRIGHT);
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
        g.drawString(font, "WAYPOINT INTEROPERABILITY", left + 20, top + 45, CYAN_BRIGHT);
        g.drawString(font, "Deferred until the native terrain renderer is stable.", left + 20, top + 82, MUTED);
        g.drawString(font, "No external map-mod waypoint code is loaded by this screen.", left + 20, top + 108, MUTED);
    }

    private void renderFlightControl(GuiGraphics g, int left, int top) {
        FlightControllerState state = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        g.drawString(font, "FLIGHT CONTROL", left + 20, top + 10, TEXT);
        g.drawString(font, "SYSTEM: " + (state.engaged() ? "ENGAGED" : "DISENGAGED"), left + 20, top + 42, state.engaged() ? GREEN : MUTED);
        g.drawString(font, "STABILISER: " + (state.stabiliser() ? "ON" : "OFF"), left + 20, top + 65, state.stabiliser() ? GREEN : MUTED);
        g.drawString(font, "FLIGHT MODE: " + state.flightMode(), left + 20, top + 88, TEXT);
        g.drawString(font, "NAVIGATION TARGET: NONE", left + 20, top + 120, CYAN_BRIGHT);
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
        g.drawString(font, "STATE: " + d.state().name(), left + 20, top + 246, d.state().name().equals("READY") ? GREEN : CYAN_BRIGHT);
        g.drawString(font, "POSITION", left + 405, top + 150, TEXT);
        g.drawString(font, String.format("CTRL X  %.2f", (double) controllerPos.getX()), left + 405, top + 174, MUTED);
        g.drawString(font, String.format("CTRL Y  %.2f", (double) controllerPos.getY()), left + 405, top + 196, MUTED);
        g.drawString(font, String.format("CTRL Z  %.2f", (double) controllerPos.getZ()), left + 405, top + 218, MUTED);
        if (minecraft != null && minecraft.player != null) {
            g.drawString(font, String.format("PLAYER X  %.2f", minecraft.player.getX()), left + 405, top + 240, MUTED);
            g.drawString(font, String.format("PLAYER Z  %.2f", minecraft.player.getZ()), left + 405, top + 262, MUTED);
        }
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
