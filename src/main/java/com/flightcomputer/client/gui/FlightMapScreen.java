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
 * A first-party, purpose-built alternative to hooking into Xaero's Minimap/World Map -
 * neither publishes a public API for third-party marker registration. This screen draws
 * points of interest that Flight Computer itself has pushed into MarkerRegistry, plus a
 * terrain layer sampled from the client's own loaded chunks (see TerrainMapCache). It
 * does not track entities/mobs, and terrain is read-only - there's no editing here.
 */
public class FlightMapScreen extends Screen {

    /** World units shown per pixel of map. Lower = more zoomed in. */
    private static final double SCALE = 4.0;

    /** Screen-pixel spacing between terrain samples - each sample is drawn as a
     *  STEP x STEP block. Coarser than 1px keeps the per-frame draw-call count sane
     *  on larger screens; the underlying chunk data is still cached at full detail. */
    private static final int TERRAIN_STEP = 3;

    private static final int UNLOADED_COLOR = 0xFF1B242A;

    private boolean showTerrain = true;

    public FlightMapScreen() {
        super(Component.literal("Flight Map"));
    }

    @Override
    protected void init() {
        int buttonY = this.height - 28;
        int buttonX = 10;

        Button terrainToggle = Button.builder(
                terrainLabel(),
                button -> {
                    showTerrain = !showTerrain;
                    button.setMessage(terrainLabel());
                }
        ).bounds(buttonX, buttonY, 100, 20).build();
        this.addRenderableWidget(terrainToggle);
        buttonX += 110;

        for (MarkerCategory category : MarkerCategory.values()) {
            Button toggle = Button.builder(
                    toggleLabel(category),
                    button -> {
                        MarkerRegistry.toggle(category);
                        button.setMessage(toggleLabel(category));
                    }
            ).bounds(buttonX, buttonY, 150, 20).build();
            this.addRenderableWidget(toggle);
            buttonX += 160;
        }
    }

    private Component terrainLabel() {
        return Component.literal("Terrain: " + (showTerrain ? "On" : "Off"));
    }

    private Component toggleLabel(MarkerCategory category) {
        String state = MarkerRegistry.isVisible(category) ? "On" : "Off";
        return Component.literal(category.getLabel() + ": " + state);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && this.minecraft.level != null) {
            TerrainMapCache.tick(this.minecraft.level);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        if (this.minecraft == null || this.minecraft.player == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        double playerX = this.minecraft.player.getX();
        double playerZ = this.minecraft.player.getZ();
        String currentDimension = this.minecraft.player.level().dimension().location().toString();

        graphics.fill(0, 0, this.width, this.height, 0xFF101018);

        if (showTerrain && this.minecraft.level != null) {
            renderTerrain(graphics, this.minecraft.level, playerX, playerZ, centerX, centerY);
        }

        graphics.hLine(centerX - 4, centerX + 4, centerY, 0xFFFFFFFF);
        graphics.vLine(centerX, centerY - 4, centerY + 4, 0xFFFFFFFF);

        for (MapMarker marker : MarkerRegistry.all()) {
            if (!marker.dimensionId().equals(currentDimension)) continue;
            if (!MarkerRegistry.isVisible(marker.category())) continue;

            int screenX = centerX + (int) ((marker.x() - playerX) / SCALE);
            int screenZ = centerY + (int) ((marker.z() - playerZ) / SCALE);
            if (screenX < 0 || screenX > this.width || screenZ < 0 || screenZ > this.height) continue;

            graphics.fill(screenX - 3, screenZ - 3, screenX + 3, screenZ + 3, 0xFF000000 | marker.category().getColor());
            graphics.drawCenteredString(this.font, marker.name(), screenX, screenZ + 5, 0xFFFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Draws the terrain layer by sampling world columns on a coarse screen-pixel grid,
     * reading colors from TerrainMapCache (cheap - just a chunk-keyed lookup) rather
     * than scanning blocks directly here. Samples whose chunk isn't cached yet just
     * fall back to a flat "unloaded" color for that frame; they'll fill in once
     * TerrainMapCache finishes queuing and computing that chunk over the next tick(s).
     */
    private void renderTerrain(GuiGraphics graphics, ClientLevel level, double playerX, double playerZ,
                                int centerX, int centerY) {
        for (int sy = 0; sy <= this.height; sy += TERRAIN_STEP) {
            double worldZ = playerZ + (sy - centerY) * SCALE;
            for (int sx = 0; sx <= this.width; sx += TERRAIN_STEP) {
                double worldX = playerX + (sx - centerX) * SCALE;
                int color = TerrainMapCache.colorAt(level, (int) Math.floor(worldX), (int) Math.floor(worldZ));
                if (color == 0) {
                    color = UNLOADED_COLOR;
                }
                graphics.fill(sx, sy, sx + TERRAIN_STEP, sy + TERRAIN_STEP, color);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
