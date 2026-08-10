package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.map.FlightMapPositionOverlay;
import com.flightcomputer.client.map.NavigationDestination;
import com.flightcomputer.client.map.XaeroMapHost;
import com.flightcomputer.client.map.XaeroMapViewport;
import com.flightcomputer.client.map.XaeroWaypointProvider;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Navigation Console hosted around Xaero's native map and waypoint rendering. */
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
    private final XaeroMapHost xaeroMap = new XaeroMapHost();
    private final FlightMapPositionOverlay positions = new FlightMapPositionOverlay();
    private final XaeroWaypointProvider xaeroWaypoints = new XaeroWaypointProvider();

    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;
    private boolean showFlightMap = true;
    private int waypointIndex;
    private NavigationDestination destination;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
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
        int gap = 4;
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer()).bounds(x, y, 118, 20).build());
        x += 122;
        addRenderableWidget(Button.builder(Component.literal("CENTRE CTRL"), b -> centreController()).bounds(x, y, 104, 20).build());

        y += 24;
        x = left + 20;
        int w = 92;
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
        addRenderableWidget(Button.builder(Component.literal("XAERO WP: NATIVE"), b -> {}).bounds(x, y, 118, 20).build());
        x += 122;
        addRenderableWidget(Button.builder(Component.literal("CLAIMS: ON"), b -> {}).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(Component.literal("PADS: ON"), b -> {}).bounds(x, y, w, 20).build());
    }

    private void initRouteControls(int left, int top) {
        int buttonY = top + 180;
        addRenderableWidget(Button.builder(Component.literal("WAYPOINT PREVIOUS"), b -> previousWaypoint())
                .bounds(left + 20, buttonY, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NEXT ▶"), b -> nextWaypoint())
                .bounds(left + 210, buttonY, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION"), b -> setDestination())
                .bounds(left + 400, buttonY, 180, 20).build());
    }

    private void initFlightControls(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 30, top + 210, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 225, top + 210, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("MODE SELECT"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 360, top + 210, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 495, top + 210, 120, 20).build());
    }

    private Component terrainLabel() { return Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF")); }
    private Component flightMapLabel() { return Component.literal("FLIGHT MAP: " + (showFlightMap ? "ON" : "OFF"); }

    private void centrePlayer() {
        if (minecraft != null && minecraft.player != null) xaeroMap.centerOn(minecraft.player.getX(), minecraft.player.getZ());
    }

    private void centreController() { xaeroMap.centerOn(controllerPos.getX() + 0.5D, controllerPos.getZ() + 0.5D); }

    private void previousWaypoint() {
        List<XaeroWaypointProvider.Waypoint> waypoints = xaeroWaypoints.getWaypoints();
        if (waypoints.isEmpty()) return;
        waypointIndex = (waypointIndex - 1 + waypoints.size()) % waypoints.size();
    }

    private void nextWaypoint() {
        List<XaeroWaypointProvider.Waypoint> waypoints = xaeroWaypoints.getWaypoints();
        if (waypoints.isEmpty()) return;
        waypointIndex = (waypointIndex + 1) % waypoints.size();
    }

    private void setDestination() {
        List<XaeroWaypointProvider.Waypoint> waypoints = xaeroWaypoints.getWaypoints();
        if (waypoints.isEmpty()) {
            destination = null;
            return;
        }
        waypointIndex = Math.max(0, Math.min(waypointIndex, waypoints.size() - 1));
        destination = NavigationDestination.from(waypoints.get(waypointIndex));
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
        xaeroMap.tick(mapWidth(), mapHeight());
        xaeroWaypoints.tick(minecraft.level);
        List<XaeroWaypointProvider.Waypoint> waypoints = xaeroWaypoints.getWaypoints();
        if (waypoints.isEmpty()) waypointIndex = 0;
        else if (waypointIndex >= waypoints.size()) waypointIndex = waypoints.size() - 1;
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
            case MAP -> renderMap(g, left, top + 42, mouseX, mouseY, partialTick);
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

    private void renderMap(GuiGraphics g, int left, int top, int mouseX, int mouseY, float partialTick) {
        int mapL = left + 20, mapT = top + 8, mapR = left + 620, mapB = top + 268;
        int mapWidth = mapR - mapL, mapHeight = mapB - mapT;
        g.fill(mapL, mapT, mapR, mapB, MAP_BG);
        if (showTerrain) xaeroMap.render(g, mapL, mapT, mapWidth, mapHeight, mouseX, mouseY, partialTick);

        XaeroMapViewport.Snapshot view = XaeroMapViewport.read();
        boolean online = showTerrain && xaeroMap.isActive() && view != null && view.finite();
        if (view != null && view.finite() && showFlightMap) positions.render(g, view, mapL, mapT, mapWidth, mapHeight, controllerPos);

        g.drawString(font, "XAERO TERRAIN: " + (online ? "ONLINE" : "OFFLINE"), mapL + 8, mapT + 8, online ? GREEN : RED);
        g.drawString(font, "XAERO NATIVE MAP", mapR - 122, mapT + 8, CYAN_BRIGHT);
        if (view != null && view.finite()) g.drawString(font, String.format("CENTRE X %.2f   Z %.2f", view.cameraX(), view.cameraZ()), mapL + 8, mapB - 30, MUTED);
        else g.drawString(font, "CENTRE X —   Z —", mapL + 8, mapB - 30, MUTED);
        g.drawString(font, "DRAG TO PAN | MAP 1×", mapL + 8, mapB - 14, MUTED);
    }

    private void renderRoute(GuiGraphics g, int left, int top) {
        List<XaeroWaypointProvider.Waypoint> waypoints = xaeroWaypoints.getWaypoints();
        g.drawString(font, "ROUTE / FLIGHT PLAN", left + 20, top + 10, TEXT);
        g.drawString(font, "XAERO WAYPOINT DESTINATION", left + 20, top + 45, CYAN_BRIGHT);

        if (waypoints.isEmpty()) {
            g.drawString(font, "TARGET: —", left + 20, top + 82, MUTED);
            g.drawString(font, "NO XAERO WAYPOINTS AVAILABLE", left + 20, top + 108, MUTED);
            g.drawString(font, "Create a waypoint with Xaero's normal waypoint UI; it will appear here automatically.", left + 20, top + 135, MUTED);
            return;
        }

        waypointIndex = Math.max(0, Math.min(waypointIndex, waypoints.size() - 1));
        XaeroWaypointProvider.Waypoint selected = waypoints.get(waypointIndex);
        g.drawString(font, "TARGET: " + selected.name(), left + 20, top + 82, TEXT);
        g.drawString(font, String.format("X %d   Y %d   Z %d", selected.x(), selected.y(), selected.z()), left + 20, top + 108, MUTED);
        g.drawString(font, String.format("WAYPOINT %d / %d", waypointIndex + 1, waypoints.size()), left + 20, top + 132, MUTED);

        if (destination != null) {
            g.drawString(font, "SELECTED DESTINATION: " + destination.name(), left + 20, top + 215, GREEN);
            renderNavigationMetrics(g, left, top, destination);
        } else {
            g.drawString(font, "SELECTED DESTINATION: —", left + 20, top + 215, MUTED);
            g.drawString(font, "Choose a waypoint, then press SET DESTINATION.", left + 20, top + 239, MUTED);
        }
    }

    private void renderNavigationMetrics(GuiGraphics g, int left, int top, NavigationDestination target) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        String dimension = minecraft.level.dimension().location().toString();
        if (!dimension.equals(target.dimension())) {
            g.drawString(font, "DISTANCE: —", left + 20, top + 244, MUTED);
            g.drawString(font, "BEARING: —", left + 210, top + 244, MUTED);
            g.drawString(font, "ETA: —", left + 400, top + 244, MUTED);
            return;
        }

        double dx = target.x() + 0.5D - minecraft.player.getX();
        double dz = target.z() + 0.5D - minecraft.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        if (bearing < 0.0D) bearing += 360.0D;

        g.drawString(font, String.format("DISTANCE: %.1f m", distance), left + 20, top + 244, MUTED);
        g.drawString(font, String.format("BEARING: %03.0f°", bearing), left + 210, top + 244, MUTED);
        g.drawString(font, "ETA: —", left + 400, top + 244, MUTED);
    }

    private void renderFlightControl(GuiGraphics g, int left, int top) {
        FlightControllerState state = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        g.drawString(font, "FLIGHT CONTROL", left + 20, top + 10, TEXT);
        g.drawString(font, "SYSTEM: " + (state.engaged() ? "ENGAGED" : "DISENGAGED"), left + 20, top + 42, state.engaged() ? GREEN : MUTED);
        g.drawString(font, "STABILISER: " + (state.stabiliser() ? "ON" : "OFF"), left + 20, top + 65, state.stabiliser() ? GREEN : MUTED);
        g.drawString(font, "FLIGHT MODE: " + state.flightMode(), left + 20, top + 88, TEXT);
        g.drawString(font, "NAVIGATION TARGET: " + (destination == null ? "NONE" : destination.name()), left + 20, top + 120, CYAN_BRIGHT);
    }

    private void renderDiagnostics(GuiGraphics g, int left, int top) {
        boolean powered = controllerPowered();
        long energy = controller == null ? 0L : controller.getEnergyStorage().getEnergyStored();
        long capacity = controller == null ? 0L : controller.getEnergyStorage().getMaxEnergyStored();
        PowerState powerState = controller == null ? PowerState.NO_POWER : controller.getPowerState();
        XaeroMapViewport.Snapshot view = XaeroMapViewport.read();
        boolean xaeroOnline = xaeroMap.isActive() && view != null && view.finite();

        g.drawString(font, "DIAGNOSTICS", left + 20, top + 10, TEXT);
        g.drawString(font, "FLIGHT COMPUTER", left + 20, top + 42, TEXT);
        g.drawString(font, powered ? "• OPERATIONAL" : "• OFFLINE", left + 265, top + 42, powered ? GREEN : RED);
        g.drawString(font, "LINK", left + 20, top + 65, TEXT);
        g.drawString(font, "• " + linkStatus(), left + 265, top + 65, powered ? GREEN : RED);
        g.drawString(font, "ENERGY", left + 20, top + 88, TEXT);
        g.drawString(font, formatEnergy(energy) + " / " + formatEnergy(capacity) + " FE", left + 265, top + 88, energy > 0 ? GREEN : RED);
        g.drawString(font, "POWER STATE", left + 20, top + 111, TEXT);
        g.drawString(font, powerState.name(), left + 285, top + 111, powerState == PowerState.NO_POWER ? RED : GREEN);
        g.drawString(font, "XAERO", left + 20, top + 150, CYAN_BRIGHT);
        g.drawString(font, xaeroOnline ? "STATUS: ONLINE" : "STATUS: OFFLINE", left + 90, top + 150, xaeroOnline ? GREEN : RED);
        g.drawString(font, "WAYPOINTS: " + xaeroWaypoints.getWaypoints().size(), left + 20, top + 174, MUTED);
        g.drawString(font, "DESTINATION: " + (destination == null ? "NONE" : destination.name()), left + 20, top + 198, MUTED);
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
        if (tab == Tab.MAP && isInsideMap(mouseX, mouseY) && xaeroMap.mouseClicked(mouseX, mouseY, button, mapLeft(), mapTop(), mapWidth(), mapHeight())) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == Tab.MAP && xaeroMap.mouseReleased(mouseX, mouseY, button, mapLeft(), mapTop(), mapWidth(), mapHeight())) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.MAP && xaeroMap.mouseDragged(mouseX, mouseY, button, dragX, dragY, mapLeft(), mapTop(), mapWidth(), mapHeight())) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean isPauseScreen() { return false; }
}
