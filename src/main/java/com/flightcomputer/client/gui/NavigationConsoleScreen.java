package com.flightcomputer.client.gui;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.client.map.TerrainMapCache;
import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
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

/** Navigation Console: controller-centred Map, Route, Flight Control and Diagnostics. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }

    private static final int TERRAIN_STEP = 4;
    private static final int MAP_SCALE_BLOCKS_PER_PIXEL = 4;
    private static final int MAP_PRELOAD_RADIUS_BLOCKS = 1200;
    private static final int UNLOADED_COLOR = 0xFF16202A;
    private static final int STATUS_ON_COLOR = 0xFF55FF55;
    private static final int STATUS_OFFLINE_COLOR = 0xFFFF5555;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;
    private static final int CONTROLLER_COLOR = 0xFF55AAFF;

    private final BlockPos controllerPos;
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
        addRenderableWidget(Button.builder(Component.literal("MAP"), b -> switchTab(Tab.MAP))
                .bounds(left, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> switchTab(Tab.ROUTE))
                .bounds(left + 160, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b -> switchTab(Tab.FLIGHT_CONTROL))
                .bounds(left + 320, top, tabW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b -> switchTab(Tab.DIAGNOSTICS))
                .bounds(left + 480, top, tabW, 22).build());

        if (tab == Tab.MAP) initMapControls(left, top);

        if (tab == Tab.FLIGHT_CONTROL) {
            addRenderableWidget(Button.builder(Component.literal("ENGAGE / DISENGAGE"),
                            b -> send(FlightControllerAction.TOGGLE_ENGAGED))
                    .bounds(left + 30, top + 210, 180, 20).build());
            addRenderableWidget(Button.builder(Component.literal("STABILISER"),
                            b -> send(FlightControllerAction.TOGGLE_STABILISER))
                    .bounds(left + 225, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("MODE SELECT"),
                            b -> send(FlightControllerAction.CYCLE_MODE))
                    .bounds(left + 360, top + 210, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"),
                            b -> send(FlightControllerAction.PULSE_DISPLAY))
                    .bounds(left + 495, top + 210, 120, 20).build());
        }
    }

    private void initMapControls(int left, int top) {
        int y = top + 275;
        int x = left + 20;
        int w = 98;
        int gap = 2;

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

    private Component terrainLabel() {
        return Component.literal("TERRAIN: " + (showTerrain ? "ON" : "OFF"));
    }

    private Component markerLabel(MarkerCategory category) {
        String label = switch (category) {
            case XAERO_WAYPOINT -> "XAERO WP";
            case FLIGHT_WAYPOINT -> "FLIGHT WP";
            case WAYSTONE -> "WAYSTONES";
            case CLAIMED_SUBLEVEL -> "CLAIMS";
            case LANDING_PAD -> "PADS";
        };
        return Component.literal(label + ": " + (MarkerRegistry.isVisible(category) ? "ON" : "OFF"));
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;
        if (!controllerPowered()) {
            minecraft.setScreen(null);
            return;
        }

        if (showTerrain) {
            // The console is an instrument mounted at the Flight Controller. Its map stays
            // centred on that controller instead of jumping around with the player's camera.
            int centerX = controllerPos.getX();
            int centerZ = controllerPos.getZ();
            TerrainMapCache.requestViewport(minecraft.level, centerX, centerZ, MAP_PRELOAD_RADIUS_BLOCKS);
            TerrainMapCache.tick(minecraft.level);
        } else {
            // Marker providers are independent of the terrain layer and still need servicing.
            TerrainMapCache.tick(minecraft.level);
        }
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        clearWidgets();
        init();
    }

    private void send(FlightControllerAction action) {
        FlightComputerNetwork.sendControllerAction(controllerPos, action);
    }

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

    private int statusColor(boolean online) {
        return online ? STATUS_ON_COLOR : STATUS_OFFLINE_COLOR;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(10, (width - 640) / 2);
        int top = 20;
        g.fill(left - 8, top - 8, left + 648, Math.min(height - 20, top + 340), 0xE610141A);
        g.fill(left - 8, top + 24, left + 648, Math.min(height - 20, top + 340), 0xE30B0E13);

        boolean powered = controllerPowered();
        String linkStatus = linkStatus();
        g.drawString(font, "◈ NAVIGATION CONSOLE", left, top - 2, 0xFFFFFFFF);
        g.drawString(font, "LINK: " + (powered ? linkStatus : "OFFLINE"), left + 500, top - 2,
                statusColor(powered));

        switch (tab) {
            case MAP -> renderMap(g, left, top + 42);
            case ROUTE -> renderRoute(g, left, top + 42);
            case FLIGHT_CONTROL -> renderFlightControl(g, left, top + 42);
            case DIAGNOSTICS -> renderDiagnostics(g, left, top + 42);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The console supplies its own dark instrument panel background.
    }

    private void renderMap(GuiGraphics g, int left, int top) {
        int mapL = left + 20;
        int mapT = top + 8;
        int mapR = left + 620;
        int mapB = top + 260;
        int cx = (mapL + mapR) / 2;
        int cy = (mapT + mapB) / 2;

        // The Flight Computer is mounted at the controller, so the instrument map is
        // deliberately controller-centred. The player is rendered as a separate marker.
        double centerX = controllerPos.getX() + 0.5D;
        double centerZ = controllerPos.getZ() + 0.5D;
        g.fill(mapL, mapT, mapR, mapB, UNLOADED_COLOR);

        if (showTerrain && minecraft != null && minecraft.level != null) {
            renderTerrain(g, minecraft.level, centerX, centerZ, mapL, mapT, mapR, mapB, cx, cy);
        }

        for (int x = mapL; x < mapR; x += 32) g.vLine(x, mapT, mapB, 0x551E3037);
        for (int y = mapT; y < mapB; y += 32) g.hLine(mapL, mapR, y, 0x551E3037);

        // Controller is always the centre reference.
        g.fill(cx - 5, cy - 5, cx + 5, cy + 5, CONTROLLER_COLOR);
        g.drawString(font, "▲ FLIGHT CONTROLLER", cx + 10, cy - 5, 0xFFFFFFFF);

        // Player position is useful when operating the console remotely, but never controls
        // the map centre. This avoids the old map jumping when the player moves.
        if (minecraft != null && minecraft.player != null) {
            int playerX = cx + (int) ((minecraft.player.getX() - centerX) / MAP_SCALE_BLOCKS_PER_PIXEL);
            int playerY = cy + (int) ((minecraft.player.getZ() - centerZ) / MAP_SCALE_BLOCKS_PER_PIXEL);
            if (playerX >= mapL && playerX <= mapR && playerY >= mapT && playerY <= mapB) {
                g.fill(playerX - 4, playerY - 4, playerX + 4, playerY + 4, PLAYER_COLOR);
                g.drawString(font, "▲ PLAYER", playerX + 9, playerY - 5, 0xFFFFFFFF);
            }
        }

        if (minecraft != null && minecraft.level != null) {
            String dim = minecraft.level.dimension().location().toString();
            for (MapMarker marker : MarkerRegistry.all()) {
                if (!dim.equals(marker.dimensionId()) || !MarkerRegistry.isVisible(marker.category())) continue;
                int sx = cx + (int) ((marker.x() - centerX) / MAP_SCALE_BLOCKS_PER_PIXEL);
                int sy = cy + (int) ((marker.z() - centerZ) / MAP_SCALE_BLOCKS_PER_PIXEL);
                if (sx < mapL || sx > mapR || sy < mapT || sy > mapB) continue;
                int color = 0xFF000000 | marker.category().getColor();
                g.fill(sx - 3, sy - 3, sx + 3, sy + 3, color);
                g.drawString(font, marker.name(), sx + 7, sy - 4, 0xFFFFFFFF);
            }
        }

        g.drawString(font, "MAP: " + (showTerrain ? "ON" : "OFF"), mapR - 120, mapB + 6, 0xFFBFC8CC);
        g.drawString(font, "CENTRE: FLIGHT CONTROLLER", left + 20, top + 275, 0xFFBFC8CC);
        g.drawString(font, "X " + controllerPos.getX() + "  Z " + controllerPos.getZ(), left + 270, top + 275, 0xFFBFC8CC);
    }

    private void renderTerrain(GuiGraphics g, ClientLevel level, double centerWorldX, double centerWorldZ,
                               int mapL, int mapT, int mapR, int mapB, int cx, int cy) {
        // Render only normalized tiles already prepared by the tick thread. No disk reads,
        // chunk scans, or Xaero decoding occur from render().
        for (int sy = mapT; sy < mapB; sy += TERRAIN_STEP) {
            double worldZ = centerWorldZ + (sy - cy) * MAP_SCALE_BLOCKS_PER_PIXEL;
            for (int sx = mapL; sx < mapR; sx += TERRAIN_STEP) {
                double worldX = centerWorldX + (sx - cx) * MAP_SCALE_BLOCKS_PER_PIXEL;
                int color = TerrainMapCache.cachedColorAt(level, (int) Math.floor(worldX), (int) Math.floor(worldZ));
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
            g.drawString(font, i == 0 ? "●" : (i == 1 ? "✓" : "○"), left + 30, y,
                    i == 1 ? 0xFF55FF55 : 0xFFFFFFFF);
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
        g.drawString(font, "SYSTEM: " + (engaged ? "● ENGAGED" : "○ DISENGAGED"), left + 20, top + 40,
                engaged ? 0xFF55FF55 : 0xFFAAAAAA);
        g.drawString(font, "STABILIZER: " + (stabiliser ? "ON" : "OFF"), left + 20, top + 65,
                stabiliser ? 0xFF55FF55 : 0xFFAAAAAA);
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
        g.drawString(font, "FLIGHT COMPUTER     ● " + (powered ? "OPERATIONAL" : "OFFLINE"), left + 20, top + 40,
                statusColor);
        g.drawString(font, "LINK                ● " + (powered ? linkStatus() : "OFFLINE"), left + 20, top + 62,
                statusColor);
        if (controller != null) {
            long stored = controller.getEnergyStorage().getEnergyStored();
            long capacity = controller.getEnergyStorage().getMaxEnergyStored();
            g.drawString(font, String.format("ENERGY              %,d / %,d FE", stored, capacity), left + 20, top + 84,
                    statusColor);
            g.drawString(font, "POWER STATE         " + controller.getPowerState().name(), left + 20, top + 106,
                    statusColor);
        }

        int markerY = top + 130;
        g.drawString(font, "MAP SOURCES", left + 20, markerY, 0xFFFFFFFF);
        for (MarkerCategory category : MarkerCategory.values()) {
            int count = markerCount(category);
            g.drawString(font, category.getLabel() + ": " + count + "  "
                    + (MarkerRegistry.isVisible(category) ? "VISIBLE" : "HIDDEN"),
                    left + 20, markerY += 18, 0xFFBFC8CC);
        }

        g.drawString(font, "POSITION", left + 330, top + 130, 0xFFFFFFFF);
        double positionX = controllerPos.getX() + 0.5D;
        double positionY = controllerPos.getY() + 0.5D;
        double positionZ = controllerPos.getZ() + 0.5D;
        g.drawString(font, String.format("CTRL X %8.2f", positionX), left + 330, top + 150, 0xFFBFC8CC);
        g.drawString(font, String.format("CTRL Y %8.2f", positionY), left + 330, top + 170, 0xFFBFC8CC);
        g.drawString(font, String.format("CTRL Z %8.2f", positionZ), left + 330, top + 190, 0xFFBFC8CC);

        if (minecraft != null && minecraft.player != null) {
            g.drawString(font, String.format("PLAYER X %8.2f", minecraft.player.getX()), left + 330, top + 212, 0xFFBFC8CC);
            g.drawString(font, String.format("PLAYER Z %8.2f", minecraft.player.getZ()), left + 330, top + 232, 0xFFBFC8CC);
        }

        g.drawString(font, "XAERO", left + 20, top + 255, 0xFFFFFFFF);
        String diagnostics = TerrainMapCache.xaeroDiagnostics();
        String[] diagnosticLines = diagnostics.split("\\n");
        for (int i = 0; i < Math.min(3, diagnosticLines.length); i++) {
            g.drawString(font, diagnosticLines[i], left + 85, top + 255 + i * 16, 0xFFBFC8CC);
        }
    }

    private int markerCount(MarkerCategory category) {
        int count = 0;
        for (MapMarker marker : MarkerRegistry.all()) {
            if (marker.category() == category) count++;
        }
        return count;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
