package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.map.XaeroMapHost;
import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Navigation Console. Phase 1 hosts Xaero's own World Map renderer in the MAP viewport. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }

    private static final int PANEL = 0xE610141A;
    private static final int MAP_BG = 0xFF101A22;
    private static final int CYAN = 0xFF55AAFF;
    private static final int CYAN_BRIGHT = 0xFF66D9FF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int TEXT = 0xFFE6EEF2;
    private static final int MUTED = 0xFF9DAEB5;

    private final BlockPos controllerPos;
    private final XaeroMapHost xaeroMap = new XaeroMapHost();
    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;

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
        if (tab == Tab.FLIGHT_CONTROL) {
            addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 30, top + 210, 180, 20).build());
            addRenderableWidget(Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 225, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("MODE SELECT"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 360, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 495, top + 210, 120, 20).build());
        }
    }

    private void initMapControls(int left, int top) {
        int y = top + 310;
        int x = left + 20;
        int w = 92;
        int gap = 4;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoomOut()).bounds(x, y, 28, 20).build());
        x += 32;
        addRenderableWidget(Button.builder(Component.literal("ZOOM"), b -> centrePlayer()).bounds(x, y, 78, 20).build());
        x += 82;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomIn()).bounds(x, y, 28, 20).build());
        x += 32;
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer()).bounds(x, y, 118, 20).build());

        y += 24;
        x = left + 20;
        addRenderableWidget(Button.builder(terrainLabel(), b -> {
            showTerrain = !showTerrain;
            send(FlightControllerAction.TOGGLE_TERRAIN);
            b.setMessage(terrainLabel());
        }).bounds(x, y, w, 20).build());
        x += w + gap;
        for (MarkerCategory category : MarkerCategory.values()) {
            MarkerCategory selected = category;
            addRenderableWidget(Button.builder(markerLabel(selected), b -> {
                MarkerRegistry.toggle(selected);
                b.setMessage(markerLabel(selected));
            }).bounds(x, y, w, 20).build());
            x += w + gap;
        }
    }

    private Component terrainLabel() { return Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF")); }

    private Component markerLabel(MarkerCategory c) {
        String label = switch (c) {
            case XAERO_WAYPOINT -> "XAERO WP";
            case FLIGHT_WAYPOINT -> "FLIGHT WP";
            case WAYSTONE -> "WAYSTONES";
            case CLAIMED_SUBLEVEL -> "CLAIMS";
            case LANDING_PAD -> "PADS";
        };
        return Component.literal(label + ": " + (MarkerRegistry.isVisible(c) ? "ON" : "OFF"));
    }

    private void zoomIn() {
        int mapL = Math.max(10, (width - 640) / 2) + 20;
        xaeroMap.mouseScrolled(mapL + 300, 70 + 130, 0, 1, mapL, 70, 600, 260);
    }

    private void zoomOut() {
        int mapL = Math.max(10, (width - 640) / 2) + 20;
        xaeroMap.mouseScrolled(mapL + 300, 70 + 130, 0, -1, mapL, 70, 600, 260);
    }

    private void centrePlayer() {
        // Reinitialise Xaero's own screen so its native camera is recreated from
        // Xaero's normal current-player context. No second camera is maintained.
        xaeroMap.clear();
        xaeroMap.tick(600, 260);
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
        xaeroMap.tick(600, 260);
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private void drawAccents(GuiGraphics g, int left, int top) {
        int activeX = switch (tab) { case MAP -> left; case ROUTE -> left + 160; case FLIGHT_CONTROL -> left + 320; case DIAGNOSTICS -> left + 480; };
        g.fill(activeX, top + 20, activeX + 150, top + 22, CYAN_BRIGHT);
        if (tab == Tab.MAP) {
            int mapL = left + 20, mapT = top + 50, mapR = left + 620, mapB = top + 310;
            g.hLine(mapL, mapR, mapT, CYAN);
            g.hLine(mapL, mapR, mapB, CYAN);
            g.vLine(mapL, mapT, mapB, CYAN);
            g.vLine(mapR, mapT, mapB, CYAN);
        }
    }

    private void renderMap(GuiGraphics g, int left, int top, int mouseX, int mouseY, float partialTick) {
        int mapL = left + 20, mapT = top + 8, mapR = left + 620, mapB = top + 268;
        g.fill(mapL, mapT, mapR, mapB, MAP_BG);

        if (showTerrain) {
            xaeroMap.render(g, mapL, mapT, mapR - mapL, mapB - mapT, mouseX, mouseY, partialTick);
        }

        g.drawString(font, "XAERO TERRAIN: " + (showTerrain && xaeroMap.isActive() ? "ONLINE" : "OFFLINE"),
                mapL + 8, mapT + 8, showTerrain && xaeroMap.isActive() ? GREEN : RED);
        g.drawString(font, "XAERO NATIVE MAP", mapR - 122, mapT + 8, CYAN_BRIGHT);
        g.drawString(font, "CENTRE PLAYER  •  XAERO CONTROLS PAN / ZOOM", mapL + 8, mapB - 14, MUTED);
    }

    private void renderRoute(GuiGraphics g, int left, int top) {
        g.drawString(font, "ROUTE / FLIGHT PLAN", left + 20, top + 10, TEXT);
        g.drawString(font, "NEXT: Refinery", left + 20, top + 45, CYAN_BRIGHT);
        g.drawString(font, "DISTANCE: —", left + 20, top + 70, MUTED);
        g.drawString(font, "BEARING: —", left + 20, top + 92, MUTED);
        g.drawString(font, "ETA: —", left + 20, top + 114, MUTED);
    }

    private void renderFlightControl(GuiGraphics g, int left, int top) {
        FlightControllerState state = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        g.drawString(font, "FLIGHT CONTROL", left + 20, top + 10, TEXT);
        g.drawString(font, "SYSTEM: " + (state.engaged() ? "ENGAGED" : "DISENGAGED"), left + 20, top + 42, state.engaged() ? GREEN : MUTED);
        g.drawString(font, "STABILIZER: " + (state.stabiliser() ? "ON" : "OFF"), left + 20, top + 65, state.stabiliser() ? GREEN : MUTED);
        g.drawString(font, "FLIGHT MODE: " + state.flightMode(), left + 20, top + 88, TEXT);
    }

    private void renderDiagnostics(GuiGraphics g, int left, int top) {
        boolean powered = controllerPowered();
        long energy = controller == null ? 0L : controller.getEnergyStorage().getEnergyStored();
        long capacity = controller == null ? 0L : controller.getEnergyStorage().getMaxEnergyStored();
        PowerState powerState = controller == null ? PowerState.NO_POWER : controller.getPowerState();

        g.drawString(font, "DIAGNOSTICS", left + 20, top + 10, TEXT);
        g.drawString(font, "FLIGHT COMPUTER", left + 20, top + 42, TEXT);
        g.drawString(font, powered ? "• OPERATIONAL" : "• OFFLINE", left + 265, top + 42, powered ? GREEN : RED);
        g.drawString(font, "LINK", left + 20, top + 65, TEXT);
        g.drawString(font, "• " + linkStatus(), left + 265, top + 65, powered ? GREEN : RED);
        g.drawString(font, "ENERGY", left + 20, top + 88, TEXT);
        g.drawString(font, formatEnergy(energy) + " / " + formatEnergy(capacity) + " FE", left + 265, top + 88, energy > 0 ? GREEN : RED);
        g.drawString(font, "POWER STATE", left + 20, top + 111, TEXT);
        g.drawString(font, powerState.name(), left + 285, top + 111, powerState == PowerState.NO_POWER ? RED : GREEN);

        g.drawString(font, "MAP SOURCES", left + 20, top + 150, TEXT);
        drawSourceLine(g, left + 20, top + 174, MarkerCategory.FLIGHT_WAYPOINT);
        drawSourceLine(g, left + 20, top + 196, MarkerCategory.XAERO_WAYPOINT);
        drawSourceLine(g, left + 20, top + 218, MarkerCategory.WAYSTONE);
        drawSourceLine(g, left + 20, top + 240, MarkerCategory.CLAIMED_SUBLEVEL);
        drawSourceLine(g, left + 20, top + 262, MarkerCategory.LANDING_PAD);

        g.drawString(font, "POSITION", left + 405, top + 150, TEXT);
        g.drawString(font, String.format("CTRL X  %.2f", (double) controllerPos.getX()), left + 405, top + 174, MUTED);
        g.drawString(font, String.format("CTRL Y  %.2f", (double) controllerPos.getY()), left + 405, top + 196, MUTED);
        g.drawString(font, String.format("CTRL Z  %.2f", (double) controllerPos.getZ()), left + 405, top + 218, MUTED);
        if (minecraft != null && minecraft.player != null) {
            g.drawString(font, String.format("PLAYER X  %.2f", minecraft.player.getX()), left + 405, top + 240, MUTED);
            g.drawString(font, String.format("PLAYER Z  %.2f", minecraft.player.getZ()), left + 405, top + 262, MUTED);
        }

        g.drawString(font, "XAERO", left + 20, top + 292, CYAN_BRIGHT);
        String[] lines = xaeroMap.diagnostics().split("\\n");
        int diagnosticY = top + 312;
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            g.drawString(font, lines[i], left + 20, diagnosticY + i * 16, MUTED);
        }
    }

    private void drawSourceLine(GuiGraphics g, int x, int y, MarkerCategory category) {
        long count = MarkerRegistry.all().stream().filter(marker -> marker.category() == category).count();
        long visible = MarkerRegistry.isVisible(category) ? count : 0L;
        g.drawString(font, category.getLabel() + ": " + count + " " + (visible > 0 ? "VISIBLE" : "HIDDEN"), x, y, MUTED);
    }

    private String formatEnergy(long value) {
        return String.format("%,d", Math.max(0L, value));
    }

    private boolean isInsideMap(double x, double y) {
        int left = Math.max(10, (width - 640) / 2);
        return x >= left + 20 && x < left + 620 && y >= 70 && y < 330;
    }

    private int mapLeft() { return Math.max(10, (width - 640) / 2) + 20; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.MAP && isInsideMap(mouseX, mouseY)) {
            if (xaeroMap.mouseScrolled(mouseX, mouseY, scrollX, scrollY, mapLeft(), 70, 600, 260)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.MAP && isInsideMap(mouseX, mouseY)
                && xaeroMap.mouseClicked(mouseX, mouseY, button, mapLeft(), 70, 600, 260)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == Tab.MAP && xaeroMap.mouseReleased(mouseX, mouseY, button, mapLeft(), 70, 600, 260)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.MAP && xaeroMap.mouseDragged(mouseX, mouseY, button, dragX, dragY,
                mapLeft(), 70, 600, 260)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
