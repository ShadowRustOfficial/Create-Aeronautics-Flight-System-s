package com.flightcomputer.client.gui;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.client.map.TerrainMapCache;
import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerRegistry;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Navigation Console: Map, Route, Flight Control and Diagnostics. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }

    private static final int TERRAIN_STEP = 4;
    private static final int UNLOADED_COLOR = 0xFF16202A;
    private static final int STATUS_ON_COLOR = 0xFF55FF55;
    private static final int STATUS_OFFLINE_COLOR = 0xFFFF5555;

    private final BlockPos controllerPos;
    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
    }

    @Override protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();

        int left = Math.max(10, (width - 640) / 2);
        int top = 20;
        int tabW = 150;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b -> switchTab(Tab.MAP)).bounds(left, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> switchTab(Tab.ROUTE)).bounds(left + 160, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b -> switchTab(Tab.FLIGHT_CONTROL)).bounds(left + 320, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b -> switchTab(Tab.DIAGNOSTICS)).bounds(left + 480, top, tabW, 22).build());

        if (tab == Tab.MAP) {
            addRenderableWidget(Button.builder(terrainLabel(), b -> {
                showTerrain = !showTerrain;
                send(FlightControllerAction.TOGGLE_TERRAIN);
                b.setMessage(terrainLabel());
            }).bounds(left + 500, top + 210, 140, 20).build());
        }

        if (tab == Tab.FLIGHT_CONTROL) {
            addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 30, top + 210, 180, 20).build());
            addRenderableWidget(Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 225, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("MODE SELECT"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 360, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 495, top + 210, 120, 20).build());
        }
    }

    private Component terrainLabel() { return Component.literal("MAP: " + (showTerrain ? "ON" : "OFF")); }

    @Override public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;
        if (!controllerPowered()) { minecraft.setScreen(null); return; }
        if (showTerrain) TerrainMapCache.tick(minecraft.level);
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
        g.fill(left - 8, top - 8, left + 648, Math.min(height - 20, top + 340), 0xE610141A);
        g.fill(left - 8, top + 24, left + 648, Math.min(height - 20, top + 340), 0xE30B0E13);

        boolean powered = controllerPowered();
        String linkStatus = linkStatus();
        g.drawString(font, "◈ NAVIGATION CONSOLE", left, top - 2, 0xFFFFFFFF);
        g.drawString(font, "LINK: " + (powered ? linkStatus : "OFFLINE"), left + 500, top - 2, statusColor(powered));

        switch (tab) {
            case MAP -> renderMap(g, left, top + 42);
            case ROUTE -> renderRoute(g, left, top + 42);
            case FLIGHT_CONTROL -> renderFlightControl(g, left, top + 42);
            case DIAGNOSTICS -> renderDiagnostics(g, left, top + 42);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    private void renderMap(GuiGraphics g, int left, int top) {
        int mapL = left + 20, mapT = top + 8, mapR = left + 620, mapB = top + 260;
        int cx = (mapL + mapR) / 2, cy = (mapT + mapB) / 2;
        double controllerX = controllerPos.getX() + 0.5D;
        double controllerZ = controllerPos.getZ() + 0.5D;
        g.fill(mapL, mapT, mapR, mapB, UNLOADED_COLOR);

        if (showTerrain && minecraft != null && minecraft.level != null)
            renderTerrain(g, minecraft.level, controllerX, controllerZ, mapL, mapT, mapR, mapB, cx, cy);

        for (int x = mapL; x < mapR; x += 32) g.vLine(x, mapT, mapB, 0x551E3037);
        for (int y = mapT; y < mapB; y += 32) g.hLine(mapL, mapR, y, 0x551E3037);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFFFFFFFF);
        g.drawString(font, "▲ FLIGHT CONTROLLER", cx + 10, cy - 5, 0xFFFFFFFF);

        if (minecraft != null && minecraft.level != null) {
            String dim = minecraft.level.dimension().location().toString();
            for (MapMarker marker : MarkerRegistry.all()) {
                if (!dim.equals(marker.dimensionId()) || !MarkerRegistry.isVisible(marker.category())) continue;
                int sx = cx + (int)((marker.x() - controllerX) / 4.0);
                int sy = cy + (int)((marker.z() - controllerZ) / 4.0);
                if (sx < mapL || sx > mapR || sy < mapT || sy > mapB) continue;
                g.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF000000 | marker.category().getColor());
                g.drawString(font, marker.name(), sx + 7, sy - 4, 0xFFFFFFFF);
            }
        }
        g.drawString(font, "DESTINATION: —    DISTANCE: —    BEARING: —    ETA: —", left + 20, top + 275, 0xFFBFC8CC);
    }

    private void renderTerrain(GuiGraphics g, ClientLevel level, double controllerX, double controllerZ,
                               int mapL, int mapT, int mapR, int mapB, int cx, int cy) {
        for (int sy = mapT; sy < mapB; sy += TERRAIN_STEP) {
            double worldZ = controllerZ + (sy - cy) * 4.0;
            for (int sx = mapL; sx < mapR; sx += TERRAIN_STEP) {
                double worldX = controllerX + (sx - cx) * 4.0;
                int color = TerrainMapCache.colorAt(level, (int) Math.floor(worldX), (int) Math.floor(worldZ));
                int x2 = Math.min(sx + TERRAIN_STEP, mapR);
                int y2 = Math.min(sy + TERRAIN_STEP, mapB);
                g.fill(sx, sy, x2, y2, color == 0 ? UNLOADED_COLOR : color);
            }
        }
    }

    private void renderRoute(GuiGraphics g, int left, int top) {
        g.drawString(font, "ROUTE / FLIGHT PLAN", left + 20, top + 10, 0xFFFFFFFF);
        g.drawString(font, "STATUS: DRAFT", left + 430, top + 10, 0xFFFFAA55);
        String[] stops = { "AIRSHIP", "Ironworks", "Refinery", "New London", "Docking Station" };
        for (int i = 0; i < stops.length; i++) {
            int y = top + 45 + i * 38;
            g.drawString(font, i == 0 ? "●" : (i == 1 ? "✓" : "○"), left + 30, y, i == 1 ? 0xFF55FF55 : 0xFFFFFFFF);
            g.drawString(font, stops[i], left + 55, y, 0xFFFFFFFF);
            if (i < stops.length - 1) g.vLine(left + 34, y + 10, y + 36, 0xFF555555);
        }
        g.drawString(font, "NEXT: Refinery", left + 360, top + 65, 0xFFFFFFFF);
        g.drawString(font, "Distance: —", left + 360, top + 88, 0xFFBFC8CC);
        g.drawString(font, "Bearing: —", left + 360, top + 108, 0xFFBFC8CC);
        g.drawString(font, "ETA: —", left + 360, top + 128, 0xFFBFC8CC);
    }

    private void renderFlightControl(GuiGraphics g, int left, int top) {
        FlightControllerState state = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        boolean engaged = state.engaged();
        boolean stabiliser = state.stabiliser();
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
            long stored = controller.getEnergyStorage().getEnergyStored();
            long capacity = controller.getEnergyStorage().getMaxEnergyStored();
            g.drawString(font, String.format("ENERGY              %,d / %,d FE", stored, capacity), left + 20, top + 84, statusColor);
            g.drawString(font, "POWER STATE         " + controller.getPowerState().name(), left + 20, top + 106, statusColor);
        }
        g.drawString(font, "POSITION", left + 20, top + 140, 0xFFFFFFFF);
        g.drawString(font, String.format("X %8.2f", controllerPos.getX() + 0.5D), left + 20, top + 160, 0xFFBFC8CC);
        g.drawString(font, String.format("Y %8.2f", controllerPos.getY() + 0.5D), left + 20, top + 180, 0xFFBFC8CC);
        g.drawString(font, String.format("Z %8.2f", controllerPos.getZ() + 0.5D), left + 20, top + 200, 0xFFBFC8CC);
        g.drawString(font, "SPEED   — m/s", left + 330, top + 160, 0xFFBFC8CC);
        g.drawString(font, "HEADING —°", left + 330, top + 182, 0xFFBFC8CC);
        g.drawString(font, "CONTROL OUTPUTS", left + 20, top + 245, 0xFFFFFFFF);
        g.drawString(font, "UP 04      DOWN 00      WEST 00      EAST 07", left + 20, top + 267, 0xFFBFC8CC);
    }

    @Override public boolean isPauseScreen() { return false; }
}
