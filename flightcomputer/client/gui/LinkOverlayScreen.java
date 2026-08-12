package com.flightcomputer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Create-schematic-inspired wiring overlay. Selection/binding actions are deliberately staged for the next link adapter pass. */
public final class LinkOverlayScreen extends Screen {
    public LinkOverlayScreen() { super(Component.literal("Flight Computer Link Tool")); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int l = width / 2 - 260, t = height / 2 - 150;
        g.fill(l, t, l + 520, t + 300, 0xE010141A);
        g.drawString(font, "FLIGHT LINK OVERLAY", l + 16, t + 14, 0xFFFFFFFF);
        g.drawString(font, "SELECTED CONTROLLER: —", l + 16, t + 40, 0xFFBFC8CC);
        g.drawString(font, "MODE: WIRING / LINKED RECEIVING", l + 16, t + 62, 0xFFBFC8CC);
        g.drawString(font, "VECTOR OUTPUTS", l + 16, t + 95, 0xFFFFFFFF);
        String[] vectors = {"UP", "DOWN", "NORTH", "SOUTH", "EAST", "WEST"};
        for (int i = 0; i < vectors.length; i++) {
            int x = l + 24 + (i % 3) * 160, y = t + 120 + (i / 3) * 42;
            g.fill(x, y, x + 140, y + 28, 0xFF1C252B);
            g.drawString(font, vectors[i] + "  →  UNBOUND", x + 8, y + 9, 0xFFBFC8CC);
        }
        g.drawString(font, "Click a vector, then select a compatible target block.", l + 16, t + 220, 0xFF777F84);
        g.drawString(font, "ESC  CLOSE", l + 16, t + 270, 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
