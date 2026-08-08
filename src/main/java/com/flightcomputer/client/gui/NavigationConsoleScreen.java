package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.map.TerrainMapCache;
import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Navigation Console with a proper pannable/zoomable avionics map. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }

    private static final int PANEL = 0xE610141A;
    private static final int MAP_BG = 0xFF101A22;
    private static final int MAP_GRID = 0x55304A55;
    private static final int CYAN = 0xFF55AAFF;
    private static final int CYAN_BRIGHT = 0xFF66D9FF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int TEXT = 0xFFE6EEF2;
    private static final int MUTED = 0xFF9DAEB5;
    private static final int TERRAIN_STEP = 4;
    private static final int[] ZOOM_LEVELS = {1, 2, 4, 8, 16};

    private final BlockPos controllerPos;
    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private boolean showTerrain = true;
    private int zoomIndex = 2;
    private double centerX;
    private double centerZ;
    private boolean centerInitialised;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
    }

    @Override
    protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();
        if (!centerInitialised) centreController();

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
        addRenderableWidget(Button.builder(zoomLabel(), b -> centreController()).bounds(x, y, 78, 20).build());
        x += 82;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomIn()).bounds(x, y, 28, 20).build());
        x += 32;
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer()).bounds(x, y, 118, 20).build());
        x += 122;
        addRenderableWidget(Button.builder(Component.literal("CENTRE CTRL"), b -> centreController()).bounds(x, y, 100, 20).build());

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

    private int zoom() { return ZOOM_LEVELS[zoomIndex]; }
    private Component zoomLabel() { return Component.literal("ZOOM " + zoom() + "x"); }
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

    private void zoomIn() { if (zoomIndex > 0) { zoomIndex--; rebuildMapControls(); } }
    private void zoomOut() { if (zoomIndex < ZOOM_LEVELS.length - 1) { zoomIndex++; rebuildMapControls(); } }
    private void rebuildMapControls() { if (tab == Tab.MAP) { clearWidgets(); init(); } }

    private void centreController() {
        centerX = controllerPos.getX() + 0.5;
        centerZ = controllerPos.getZ() + 0.5;
        centerInitialised = true;
    }

    private void centrePlayer() {
        if (minecraft != null && minecraft.player != null) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
            centerInitialised = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;
        if (!controllerPowered()) { minecraft.setScreen(null); return; }

        int mapW = 600;
        int mapH = 260;
        int radius = Math.max(256, Math.min(1536, Math.max(mapW, mapH) * zoom() / 2 + 64));
        TerrainMapCache.requestViewport(minecraft.level, (int) Math.floor(centerX), (int) Math.floor(centerZ), radius);
        TerrainMapCache.tick(minecraft.level);
    }

    private void switchTab(Tab next) { tab = next; clearWidgets(); init(); }
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

    private void renderMap(GuiGraphics g, int left, int top) {
        int mapL = left + 20, mapT = top + 8, mapR = left + 620, mapB = top + 268;
        int cx = (mapL + mapR) / 2, cy = (mapT + mapB) / 2;
        g.fill(mapL, mapT, mapR, mapB, MAP_BG);
        renderTerrain(g, mapL, mapT, mapR, mapB, cx, cy);
        for (int x = mapL; x < mapR; x += 32) g.vLine(x, mapT, mapB, MAP_GRID);
        for (int y = mapT; y < mapB; y += 32) g.hLine(mapL, mapR, y, MAP_GRID);

        drawMarker(g, cx, cy, CYAN_BRIGHT, "FLIGHT CONTROLLER");
        if (minecraft != null && minecraft.player != null) {
            int px = cx + (int)((minecraft.player.getX() - centerX) / zoom());
            int pz = cy + (int)((minecraft.player.getZ() - centerZ) / zoom());
            if (px >= mapL && px <= mapR && pz >= mapT && pz <= mapB) drawMarker(g, px, pz, 0xFFFFFFFF, "PLAYER");
        }

        if (minecraft != null && minecraft.level != null) {
            String dim = minecraft.level.dimension().location().toString();
            for (MapMarker marker : MarkerRegistry.all()) {
                if (!dim.equals(marker.dimensionId()) || !MarkerRegistry.isVisible(marker.category())) continue;
                int sx = cx + (int)((marker.x() - centerX) / zoom());
                int sy = cy + (int)((marker.z() - centerZ) / zoom());
                if (sx < mapL || sx > mapR || sy < mapT || sy > mapB) continue;
                drawMarker(g, sx, sy, 0xFF000000 | marker.category().getColor(), marker.name());
            }
        }
        g.drawString(font, "TERRAIN: " + (showTerrain ? "ONLINE" : "HIDDEN"), mapL + 8, mapT + 8, showTerrain ? GREEN : RED);
        g.drawString(font, "ZOOM " + zoom() + " blocks/px", mapR - 112, mapT + 8, MUTED);
        g.drawString(font, "CENTRE X " + Math.round(centerX) + "  Z " + Math.round(centerZ), mapL + 8, mapB - 14, MUTED);
        g.drawString(font, "DRAG TO PAN  •  SCROLL TO ZOOM", mapR - 190, mapB - 14, MUTED);
    }

    private void renderTerrain(GuiGraphics g, int mapL, int mapT, int mapR, int mapB, int cx, int cy) {
        if (!showTerrain || minecraft == null || minecraft.level == null) return;
        for (int sy = mapT; sy < mapB; sy += TERRAIN_STEP) {
            double wz = centerZ + (sy - cy) * zoom();
            for (int sx = mapL; sx < mapR; sx += TERRAIN_STEP) {
                double wx = centerX + (sx - cx) * zoom();
                int color = TerrainMapCache.cachedColorAt(minecraft.level, (int)Math.floor(wx), (int)Math.floor(wz));
                g.fill(sx, sy, Math.min(sx + TERRAIN_STEP, mapR), Math.min(sy + TERRAIN_STEP, mapB), color == 0 ? MAP_BG : color);
            }
        }
    }

    private void drawMarker(GuiGraphics g, int x, int y, int color, String label) {
        g.fill(x - 4, y - 4, x + 4, y + 4, color);
        if (label != null) g.drawString(font, label, x + 9, y - 4, TEXT);
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
        g.drawString(font, "DIAGNOSTICS", left + 20, top + 10, TEXT);
        g.drawString(font, "FLIGHT COMPUTER: " + (controllerPowered() ? "OPERATIONAL" : "OFFLINE"), left + 20, top + 40, controllerPowered() ? GREEN : RED);
        g.drawString(font, "LINK: " + linkStatus(), left + 20, top + 62, controllerPowered() ? GREEN : RED);
        g.drawString(font, "XAERO", left + 20, top + 92, CYAN_BRIGHT);
        String[] lines = TerrainMapCache.xaeroDiagnostics().split("\\n");
        for (int i = 0; i < Math.min(8, lines.length); i++) g.drawString(font, lines[i], left + 20, top + 110 + i * 16, MUTED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.MAP && isInsideMap(mouseX, mouseY)) {
            if (scrollY > 0) zoomIn(); else if (scrollY < 0) zoomOut();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.MAP && button == 0 && isInsideMap(mouseX, mouseY)) {
            dragging = true; lastMouseX = mouseX; lastMouseY = mouseY; return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.MAP && button == 0 && dragging) {
            centerX -= (mouseX - lastMouseX) * zoom();
            centerZ -= (mouseY - lastMouseY) * zoom();
            lastMouseX = mouseX; lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean isInsideMap(double x, double y) {
        int left = Math.max(10, (width - 640) / 2);
        return x >= left + 20 && x <= left + 620 && y >= 70 && y <= 330;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
