package com.flightcomputer.client.gui;

import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A first-party, purpose-built alternative to hooking into Xaero's Minimap/World Map -
 * neither publishes a public API for third-party marker registration. This screen only
 * draws points of interest that Flight Computer itself has pushed into MarkerRegistry.
 * It does not track entities/mobs and does not render terrain or chunk data.
 */
public class FlightMapScreen extends Screen {

    /** World units shown per pixel of map. Lower = more zoomed in. */
    private static final double SCALE = 4.0;

    public FlightMapScreen() {
        super(Component.literal("Flight Map"));
    }

    @Override
    protected void init() {
        int buttonY = this.height - 28;
        int buttonX = 10;
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

    private Component toggleLabel(MarkerCategory category) {
        String state = MarkerRegistry.isVisible(category) ? "On" : "Off";
        return Component.literal(category.getLabel() + ": " + state);
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

        graphics.fill(0, 0, this.width, this.height, 0xCC101018);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
