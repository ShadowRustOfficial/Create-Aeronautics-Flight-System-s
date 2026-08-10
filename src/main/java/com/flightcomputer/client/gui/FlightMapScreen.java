package com.flightcomputer.client.gui;

import com.flightcomputer.client.map.TerrainMapCache;
import com.flightcomputer.client.map.TerrainProviderDiagnostics;
import com.flightcomputer.client.map.TerrainProviderState;
import com.flightcomputer.client.map.TerrainViewport;
import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/** Lightweight first-party map view backed by the renderer-neutral terrain provider. */
public class FlightMapScreen extends Screen {
    private static final double SCALE = 4.0D;
    private static final int TERRAIN_STEP = 4;
    private static final int UNLOADED_COLOR = 0xFF1B242A;
    private boolean showTerrain = true;

    public FlightMapScreen() { super(Component.literal("Flight Map")); }

    @Override
    protected void init() {
        int buttonY = this.height - 28;
        int buttonX = 10;
        Button terrainToggle = Button.builder(terrainLabel(), button -> {
            showTerrain = !showTerrain;
            button.setMessage(terrainLabel());
        }).bounds(buttonX, buttonY, 100, 20).build();
        this.addRenderableWidget(terrainToggle);
        buttonX += 110;

        for (MarkerCategory category : MarkerCategory.values()) {
            Button toggle = Button.builder(toggleLabel(category), button -> {
                MarkerRegistry.toggle(category);
                button.setMessage(toggleLabel(category));
            }).bounds(buttonX, buttonY, 150, 20).build();
            this.addRenderableWidget(toggle);
            buttonX += 160;
        }
    }

    private Component terrainLabel() { return Component.literal("MAP: " + (showTerrain ? "ON" : "OFF")); }
    private Component toggleLabel(MarkerCategory category) {
        return Component.literal(category.getLabel() + ": " + (MarkerRegistry.isVisible(category) ? "On" : "Off"));
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null || !showTerrain) return;

        double playerX = minecraft.player != null ? minecraft.player.getX() : 0.0D;
        double playerZ = minecraft.player != null ? minecraft.player.getZ() : 0.0D;
        double radius = Math.ceil(Math.hypot(width, height) * SCALE * 0.65D);
        TerrainMapCache.requestViewport(minecraft.level, playerX, playerZ, radius, SCALE);
        TerrainMapCache.tick(minecraft.level);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null || minecraft.player == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int centerX = width / 2;
        int centerY = height / 2;
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        String currentDimension = minecraft.player.level().dimension().location().toString();
        graphics.fill(0, 0, width, height, 0xFF101018);

        if (showTerrain && minecraft.level != null) {
            TerrainViewport viewport = new TerrainViewport(
                    playerX, playerZ,
                    Math.ceil(Math.hypot(width, height) * SCALE * 0.65D), SCALE);
            renderTerrain(graphics, minecraft.level, viewport, centerX, centerY);
        }

        graphics.hLine(centerX - 4, centerX + 4, centerY, 0xFFFFFFFF);
        graphics.vLine(centerX, centerY - 4, centerY + 4, 0xFFFFFFFF);

        for (MapMarker marker : MarkerRegistry.all()) {
            if (!marker.dimensionId().equals(currentDimension) || !MarkerRegistry.isVisible(marker.category())) continue;
            int screenX = centerX + (int) ((marker.x() - playerX) / SCALE);
            int screenZ = centerY + (int) ((marker.z() - playerZ) / SCALE);
            if (screenX < 0 || screenX > width || screenZ < 0 || screenZ > height) continue;
            graphics.fill(screenX - 3, screenZ - 3, screenX + 3, screenZ + 3,
                    0xFF000000 | marker.category().getColor());
            graphics.drawCenteredString(font, marker.name(), screenX, screenZ + 5, 0xFFFFFFFF);
        }

        renderTerrainStatus(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTerrain(GuiGraphics graphics, ClientLevel level, TerrainViewport viewport,
                               int centerX, int centerY) {
        for (int sy = 0; sy < height; sy += TERRAIN_STEP) {
            double worldZ = viewport.centerZ() + (sy - centerY) * viewport.blocksPerPixel();
            for (int sx = 0; sx < width; sx += TERRAIN_STEP) {
                double worldX = viewport.centerX() + (sx - centerX) * viewport.blocksPerPixel();
                int color = TerrainMapCache.colorAt(level,
                        (int) Math.floor(worldX), (int) Math.floor(worldZ), viewport.blocksPerPixel());
                graphics.fill(sx, sy, Math.min(sx + TERRAIN_STEP, width),
                        Math.min(sy + TERRAIN_STEP, height),
                        color == 0 ? UNLOADED_COLOR : color);
            }
        }
    }

    /** Compact in-UI operational status; no diagnostics are drawn outside this screen. */
    private void renderTerrainStatus(GuiGraphics graphics) {
        TerrainProviderDiagnostics d = TerrainMapCache.diagnostics(minecraft.level);
        String status = "TERRAIN " + d.state().name()
                + "  R:" + d.loadedRegions() + "/" + d.requestedRegions()
                + "  L:" + d.decodedLeaves()
                + "  C:" + d.cachedLeaves();
        int statusColor = switch (d.state()) {
            case READY -> 0xFF66FF88;
            case LOADING, INITIALIZING -> 0xFFFFCC66;
            case DEGRADED -> 0xFFFFAA55;
            case ERROR -> 0xFFFF6666;
            case OFFLINE -> 0xFFAAAAAA;
        };
        graphics.fill(6, 6, Math.min(width - 6, 280), 22, 0xCC080B0D);
        graphics.drawString(font, status, 10, 10, statusColor, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
