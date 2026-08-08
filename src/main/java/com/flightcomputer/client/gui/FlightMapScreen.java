package com.flightcomputer.client.gui;

import com.flightcomputer.client.map.TerrainMapCache;
import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * First-party Flight Computer map view.
 *
 * Xaero supplies the terrain source; this screen only renders the normalized,
 * already-prepared cache and the independent marker layers.
 */
public final class FlightMapScreen extends Screen {
    private static final int TERRAIN_STEP = 4;
    private static final int MIN_SCALE = 1;
    private static final int MAX_SCALE = 16;
    private static final int UNLOADED_COLOR = 0xFF16202A;
    private static final int PANEL_COLOR = 0xE610141A;
    private static final int MAP_COLOR = 0xFF0B0E13;
    private static final int GRID_COLOR = 0x55283B43;
    private static final int PRIMARY_TEXT = 0xFFE6EEF2;
    private static final int SECONDARY_TEXT = 0xFF9DAEB5;
    private static final int ACCENT = 0xFF55AAFF;
    private static final int ACTIVE = 0xFF55FFAA;
    private static final int DISABLED = 0xFF66747A;

    private int scale = 4;
    private double panX;
    private double panZ;
    private boolean showTerrain = true;
    private boolean dragging;

    public FlightMapScreen() {
        super(Component.literal("Flight Map"));
    }

    @Override
    protected void init() {
        clearWidgets();

        int left = Math.max(8, (width - 720) / 2);
        int mapBottom = Math.min(height - 62, 430);
        int rowY = mapBottom + 8;
        int buttonW = 92;
        int gap = 4;
        int x = left;

        addRenderableWidget(Button.builder(terrainLabel(), button -> {
            showTerrain = !showTerrain;
            button.setMessage(terrainLabel());
        }).bounds(x, rowY, buttonW, 20).build());
        x += buttonW + gap;

        for (MarkerCategory category : MarkerCategory.values()) {
            MarkerCategory selected = category;
            addRenderableWidget(Button.builder(markerLabel(selected), button -> {
                MarkerRegistry.toggle(selected);
                button.setMessage(markerLabel(selected));
            }).bounds(x, rowY, buttonW, 20).build());
            x += buttonW + gap;
            if (x + buttonW > left + 710) {
                x = left;
                rowY += 24;
            }
        }

        int controlY = Math.min(height - 28, rowY + 24);
        int controlX = left;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoomOut())
                .bounds(controlX, controlY, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ZOOM " + scale + "x"), b -> resetView())
                .bounds(controlX + 32, controlY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomIn())
                .bounds(controlX + 124, controlY, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer())
                .bounds(controlX + 156, controlY, 118, 20).build());
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

    private void zoomIn() {
        scale = Math.max(MIN_SCALE, scale / 2);
        refreshZoomButton();
    }

    private void zoomOut() {
        scale = Math.min(MAX_SCALE, scale * 2);
        refreshZoomButton();
    }

    private void resetView() {
        scale = 4;
        panX = 0;
        panZ = 0;
        refreshZoomButton();
    }

    private void centrePlayer() {
        panX = 0;
        panZ = 0;
    }

    private void refreshZoomButton() {
        for (var widget : children()) {
            if (widget instanceof Button button && button.getMessage().getString().startsWith("ZOOM ")) {
                button.setMessage(Component.literal("ZOOM " + scale + "x"));
                break;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;

        // Prepare Xaero data outside render(). Marker layers are serviced regardless of
        // whether the terrain layer is currently visible.
        if (minecraft.player != null) {
            int centerX = (int) Math.floor(minecraft.player.getX() + panX);
            int centerZ = (int) Math.floor(minecraft.player.getZ() + panZ);
            TerrainMapCache.requestViewport(minecraft.level, centerX, centerZ, preloadRadius());
        }
        TerrainMapCache.tick(minecraft.level);
    }

    private int preloadRadius() {
        return Math.max(256, Math.min(1536, Math.max(width, height) * scale / 2));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF080B0E);

        int left = Math.max(8, (width - 720) / 2);
        int right = Math.min(width - 8, left + 720);
        int top = 10;
        int mapTop = top + 34;
        int mapBottom = Math.min(height - 62, 430);

        graphics.fill(left - 8, top - 8, right + 8, Math.min(height - 8, mapBottom + 68), PANEL_COLOR);
        graphics.fill(left, mapTop, right, mapBottom, MAP_COLOR);
        graphics.drawString(font, "◈ FLIGHT COMPUTER / NAVIGATION MAP", left, top, PRIMARY_TEXT);

        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            graphics.drawString(font, "NO CLIENT WORLD", left + 12, mapTop + 12, 0xFFFF5555);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        double centerWorldX = playerX + panX;
        double centerWorldZ = playerZ + panZ;
        int centerX = (left + right) / 2;
        int centerY = (mapTop + mapBottom) / 2;
        String dimension = minecraft.level.dimension().location().toString();

        if (showTerrain) {
            renderTerrain(graphics, minecraft.level, centerWorldX, centerWorldZ,
                    left, mapTop, right, mapBottom, centerX, centerY);
        }

        for (int x = left; x <= right; x += 32) graphics.vLine(x, mapTop, mapBottom, GRID_COLOR);
        for (int y = mapTop; y <= mapBottom; y += 32) graphics.hLine(left, right, y, GRID_COLOR);

        renderMarkers(graphics, dimension, centerWorldX, centerWorldZ, left, mapTop, right, mapBottom,
                centerX, centerY);

        // Player aircraft/navigation reference remains visually distinct from map markers.
        graphics.hLine(centerX - 7, centerX + 7, centerY, ACCENT);
        graphics.vLine(centerX, centerY - 7, centerY + 7, ACCENT);
        graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFFFFFFFF);

        graphics.drawString(font, "DIM: " + dimension, left + 8, mapTop + 8, SECONDARY_TEXT);
        graphics.drawString(font, "SCALE: " + scale + " blocks/px", right - 118, mapTop + 8, SECONDARY_TEXT);
        graphics.drawString(font, "X " + Math.round(centerWorldX) + "  Z " + Math.round(centerWorldZ),
                left + 8, mapBottom - 14, SECONDARY_TEXT);
        graphics.drawString(font, "XAERO TERRAIN: " + (showTerrain ? "ONLINE" : "HIDDEN"),
                right - 150, mapBottom - 14, showTerrain ? ACTIVE : DISABLED);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTerrain(GuiGraphics graphics, ClientLevel level, double centerWorldX, double centerWorldZ,
                               int mapLeft, int mapTop, int mapRight, int mapBottom,
                               int centerX, int centerY) {
        for (int sy = mapTop; sy < mapBottom; sy += TERRAIN_STEP) {
            double worldZ = centerWorldZ + (sy - centerY) * scale;
            for (int sx = mapLeft; sx < mapRight; sx += TERRAIN_STEP) {
                double worldX = centerWorldX + (sx - centerX) * scale;
                int color = TerrainMapCache.cachedColorAt(level,
                        (int) Math.floor(worldX), (int) Math.floor(worldZ));
                graphics.fill(sx, sy,
                        Math.min(sx + TERRAIN_STEP, mapRight),
                        Math.min(sy + TERRAIN_STEP, mapBottom),
                        color == 0 ? UNLOADED_COLOR : color);
            }
        }
    }

    private void renderMarkers(GuiGraphics graphics, String dimension, double centerWorldX, double centerWorldZ,
                               int mapLeft, int mapTop, int mapRight, int mapBottom,
                               int centerX, int centerY) {
        for (MapMarker marker : MarkerRegistry.all()) {
            if (!dimension.equals(marker.dimensionId()) || !MarkerRegistry.isVisible(marker.category())) continue;

            int screenX = centerX + (int) ((marker.x() - centerWorldX) / scale);
            int screenZ = centerY + (int) ((marker.z() - centerWorldZ) / scale);
            if (screenX < mapLeft || screenX > mapRight || screenZ < mapTop || screenZ > mapBottom) continue;

            int markerColor = 0xFF000000 | marker.category().getColor();
            graphics.fill(screenX - 3, screenZ - 3, screenX + 4, screenZ + 4, markerColor);
            graphics.drawString(font, marker.name(), screenX + 7, screenZ - 4, PRIMARY_TEXT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) zoomIn();
        else if (scrollY < 0) zoomOut();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = true;
            return true;
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
        if (button == 0 && dragging) {
            panX -= dragX * scale;
            panZ -= dragY * scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
