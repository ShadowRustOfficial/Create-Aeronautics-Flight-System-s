package com.flightcomputer.client.gui;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.client.map.FlightMapRenderer;
import com.flightcomputer.client.map.FlightMapTracker;
import com.flightcomputer.client.map.FlightMapViewport;
import com.flightcomputer.client.map.TerrainMapCache;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Navigation Console: Map, Route, Flight Control and Diagnostics. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }

    private static final int MAP_TRACK_RADIUS_BLOCKS = 1200;
    private static final int STATUS_ON_COLOR = 0xFF55FF55;
    private static final int STATUS_OFFLINE_COLOR = 0xFFFF5555;

    private final BlockPos controllerPos;
    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;
    private FlightMapViewport flightMapViewport;
    private boolean draggingMap;
    private double lastMouseX;
    private double lastMouseY;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
    }

    @Override protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();
        ensureViewport();

        int left = Math.max(10, (width - 640) / 2);
        int top = 20;
        int tabW = 150;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b -> switchTab(Tab.MAP)).bounds(left, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> switchTab(Tab.ROUTE)).bounds(left + 160, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b -> switchTab(Tab.FLIGHT_CONTROL)).bounds(left + 320, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b -> switchTab(Tab.DIAGNOSTICS)).bounds(left + 480, top, tabW, 22).build());

        if (tab == Tab.MAP) {
            addRenderableWidget(Button.builder(Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF")), b -> {
                showTerrain = !showTerrain;
                send(FlightControllerAction.TOGGLE_TERRAIN);
                b.setMessage(Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF")));
            }).bounds(left + 500, top + 290, 140, 20).build());
        }

        if (tab == Tab.FLIGHT_CONTROL) {
            addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 30, top + 210, 180, 20).build());
            addRenderableWidget(Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 225, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("MODE SELECT"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 360, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 495, top + 210, 120, 20).build());
        }
    }

    private void ensureViewport() {
        if (minecraft == null || minecraft.level == null) return;
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (flightMapViewport == null || !dimension.equals(flightMapViewport.dimension())) {
            BlockPos anchor = controller != null ? controller.getBlockPos() : controllerPos;
            flightMapViewport = new FlightMapViewport(anchor.getX() + 0.5D, anchor.getZ() + 0.5D, 4.0D, dimension);
        }
    }

    @Override public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;
        if (!controllerPowered()) { minecraft.setScreen(null); return; }
        ensureViewport();

        if (showTerrain) {
            // The active controller is the tracking anchor. The player is not used to decide
            // what terrain the Flight Computer knows about.
            BlockPos anchor = controller != null ? controller.getBlockPos() : controllerPos;
            TerrainMapCache.requestViewport(minecraft.level, anchor.getX(), anchor.getZ(), MAP_TRACK_RADIUS_BLOCKS);
            TerrainMapCache.tick(minecraft.level);
        }
    }

    private void switchTab(Tab newTab) { tab = newTab; clearWidgets(); init(); }
    private void send(FlightControllerAction action) { FlightComputerNetwork.sendControllerAction(controllerPos, action); }

    private FlightControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) return null;
        BlockEntity be = minecraft.level.getBlockEntity(controllerPos);
        return be instanceof FlightControllerBlockEntity fc ? fc : null;
    }

    private boolean controllerPowered() {
        if (controller == null) controller = getController();
        return controller != null && controller.getEnergyStorage().getEnergyStored() > 0
                && controller.getPowerState() != PowerState.NO_POWER;
    }

    private String linkStatus() {
        if (!controllerPowered()) return "OFFLINE";
        return controller != null && controller.getLinkedControllerId() != null ? "CONNECTED" : "NOT LINKED";
    }

    private int statusColor(boolean online) { return online ? STATUS_ON_COLOR : STATUS_OFFLINE_COLOR; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(10, (width - 640) / 2);
        int top = 20;
        g.fill(left - 8, top - 8, left + 648, Math.min(height - 20, top + 360), 0xE610141A);
        g.fill(left - 8, top + 24, left + 648, Math.min(height - 20, top + 360), 0xE30B0E13);

        boolean powered = controllerPowered();
        String linkStatus = linkStatus();
        g.drawString(font, "◈ NAVIGATION CONSOLE", left, top - 2, 0xFFFFFFFF);
        g.drawString(font, "LINK: " + (powered ? linkStatus : "OFFLINE"), left + 500, top - 2, statusColor(powered));

        switch (tab) {
            case MAP -> renderMap(g, left, top + 42, mouseX, mouseY);
            case ROUTE -> renderRoute(g, left, top + 42);
            case FLIGHT_CONTROL -> renderFlightControl(g, left, top + 42);
            case DIAGNOSTICS -> renderDiagnostics(g, left, top + 42);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    private void renderMap(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int mapL = left + 20, mapT = top + 8, mapR = left + 620, mapB = top + 260;
        ensureViewport();
        if (minecraft != null && minecraft.level != null && flightMapViewport != null && controller != null) {
            BlockPos liveControllerPos = controller.getBlockPos();
            FlightMapTracker tracker = new FlightMapTracker(
                    controller.getControllerId(),
                    minecraft.level.dimension().location(),
                    liveControllerPos,
                    MAP_TRACK_RADIUS_BLOCKS);
            Vec3 playerPos = minecraft.player == null ? null : minecraft.player.position();
            FlightMapRenderer.render(g, font, minecraft.level, flightMapViewport, tracker,
                    liveControllerPos, playerPos, mapL, mapT, mapR, mapB);
        } else {
            g.fill(mapL, mapT, mapR, mapB, 0xFF101820);
        }

        g.drawString(font, "TRACK: " + MAP_TRACK_RADIUS_BLOCKS + "m", mapL + 8, mapB + 6, 0xFFBFC8CC);
        double viewX = flightMapViewport == null ? controllerPos.getX() : flightMapViewport.centerX();
        double viewZ = flightMapViewport == null ? controllerPos.getZ() : flightMapViewport.centerZ();
        g.drawString(font, "CENTRE X " + String.format("%.1f", viewX) + "  Z " + String.format("%.1f", viewZ), mapL + 8, mapB - 18, 0xFFBFC8CC);
        g.drawString(font, "DRAG TO PAN | SCROLL TO ZOOM", mapL + 8, mapB - 2, 0xFFBFC8CC);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.MAP && button == 0 && isInsideMap(mouseX, mouseY)) {
            draggingMap = true; lastMouseX = mouseX; lastMouseY = mouseY; return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingMap) { draggingMap = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.MAP && draggingMap && button == 0 && flightMapViewport != null) {
            flightMapViewport.panPixels(mouseX - lastMouseX, mouseY - lastMouseY);
            lastMouseX = mouseX; lastMouseY = mouseY; return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (tab == Tab.MAP && isInsideMap(mouseX, mouseY) && flightMapViewport != null) {
            flightMapViewport.zoom(deltaY); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private boolean isInsideMap(double x, double y) {
        int left = Math.max(10, (width - 640) / 2), top = 20;
        int mapL = left + 20, mapT = top + 50, mapR = left + 620, mapB = top + 302;
        return x >= mapL && x <= mapR && y >= mapT && y <= mapB;
    }

    private void renderRoute(GuiGraphics g, int left, int top) {
        g.drawString(font, "ROUTE / FLIGHT PLAN", left + 20, top + 10, 0xFFFFFFFF);
        g.drawString(font, "STATUS: DRAFT", left + 430, top + 10, 0xFFFFAA55);
        String[] stops = { "CONTROLLER", "TRACKED AREA", "DESTINATION", "DOCKING POINT" };
        for (int i = 0; i < stops.length; i++) {
            int y = top + 45 + i * 38;
            g.drawString(font, i == 0 ? "●" : "○", left + 30, y, i == 0 ? 0xFF55FF55 : 0xFFFFFFFF);
            g.drawString(font, stops[i], left + 55, y, 0xFFFFFFFF);
            if (i < stops.length - 1) g.vLine(left + 34, y + 10, y + 36, 0xFF555555);
        }
        g.drawString(font, "NEXT: —", left + 360, top + 65, 0xFFFFFFFF);
        g.drawString(font, "Distance: —", left + 360, top + 88, 0xFFBFC8CC);
        g.drawString(font, "Bearing: —", left + 360, top + 108, 0xFFBFC8CC);
        g.drawString(font, "ETA: —", left + 360, top + 128, 0xFFBFC8CC);
    }

    private void renderFlightControl(GuiGraphics g, int left, int top) {
        FlightControllerState state = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        boolean engaged = state.engaged(), stabiliser = state.stabiliser();
        g.drawString(font, "FLIGHT CONTROL", left + 20, top + 10, 0xFFFFFFFF);
        g.drawString(font, "SYSTEM: " + (engaged ? "● ENGAGED" : "○ DISENGAGED"), left + 20, top + 40, engaged ? 0xFF55FF55 : 0xFFAAAAAA);
        g.drawString(font, "STABILIZER: " + (stabiliser ? "ON" : "OFF"), left + 20, top + 65, stabiliser ? 0xFF55FF55 : 0xFFAAAAAA);
        g.drawString(font, "FLIGHT MODE: " + state.flightMode(), left + 20, top + 90, 0xFFFFFFFF);
        g.drawString(font, "AUTOPILOT", left + 330, top + 40, 0xFFFFFFFF);
        g.drawString(font, "Automatic Navigation     ○", left + 330, top + 65, 0xFFBFC8CC);
        g.drawString(font, "Automatic Braking        ○", left + 330, top + 87, 0xFFBFC8CC);
        g.drawString(font, "Automatic Altitude       ○", left + 330, top + 109, 0xFFBFC8CC);
        g.drawString(font, "Automatic Docking        ○", left + 330, top + 131, 0xFFBFC8CC);
    }

    private void renderDiagnostics(GuiGraphics g, int left, int top) {
        boolean powered = controllerPowered();
        int statusColor = statusColor(powered);
        g.drawString(font, "DIAGNOSTICS", left + 20, top + 10, 0xFFFFFFFF);
        g.drawString(font, "FLIGHT COMPUTER     ● " + (powered ? "OPERATIONAL" : "OFFLINE"), left + 20, top + 40, statusColor);
        g.drawString(font, "LINK                ● " + (powered ? linkStatus() : "OFFLINE"), left + 20, top + 62, statusColor);
        if (controller != null) {
            long stored = controller.getEnergyStorage().getEnergyStored(), capacity = controller.getEnergyStorage().getMaxEnergyStored();
            g.drawString(font, String.format("ENERGY              %,d / %,d FE", stored, capacity), left + 20, top + 84, statusColor);
            g.drawString(font, "POWER STATE         " + controller.getPowerState().name(), left + 20, top + 106, statusColor);
        }
        g.drawString(font, "POSITION", left + 20, top + 140, 0xFFFFFFFF);
        double positionX = minecraft != null && minecraft.player != null ? minecraft.player.getX() : controllerPos.getX() + 0.5D;
        double positionY = minecraft != null && minecraft.player != null ? minecraft.player.getY() : controllerPos.getY() + 0.5D;
        double positionZ = minecraft != null && minecraft.player != null ? minecraft.player.getZ() : controllerPos.getZ() + 0.5D;
        g.drawString(font, String.format("X %8.2f", positionX), left + 20, top + 160, 0xFFBFC8CC);
        g.drawString(font, String.format("Y %8.2f", positionY), left + 20, top + 180, 0xFFBFC8CC);
        g.drawString(font, String.format("Z %8.2f", positionZ), left + 20, top + 200, 0xFFBFC8CC);
        g.drawString(font, "SPEED   — m/s", left + 330, top + 160, 0xFFBFC8CC);
        g.drawString(font, "HEADING —°", left + 330, top + 182, 0xFFBFC8CC);
        g.drawString(font, "CONTROL OUTPUTS", left + 20, top + 245, 0xFFFFFFFF);
        g.drawString(font, "UP 04      DOWN 00      WEST 00      EAST 07", left + 20, top + 267, 0xFFBFC8CC);
    }

    @Override public boolean isPauseScreen() { return false; }
}
