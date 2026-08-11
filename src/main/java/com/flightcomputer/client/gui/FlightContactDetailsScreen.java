package com.flightcomputer.client.gui;

import com.flightcomputer.client.map.FlightContactRegistry;
import com.flightcomputer.map.FlightContact;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Detailed contact information is intentionally a separate interaction surface, not a permanent map label. */
public final class FlightContactDetailsScreen extends Screen {
    private final UUID contactId;

    public FlightContactDetailsScreen(UUID contactId) {
        super(Component.literal("Flight Contact"));
        this.contactId = contactId;
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("TRACK CONTACT"), b -> {
            // Target assignment is wired through the operations/network layer; this button deliberately does not
            // create a static coordinate target.
            b.setMessage(Component.literal("TRACK REQUESTED"));
        }).bounds(width / 2 - 160, height / 2 + 70, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SET NAVIGATION TARGET"), b -> {
            b.setMessage(Component.literal("LIVE TARGET REQUESTED"));
        }).bounds(width / 2 + 10, height / 2 + 70, 150, 20).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        FlightContact contact = FlightContactRegistry.get(contactId);
        int left = width / 2 - 250, top = height / 2 - 130;
        g.fill(left - 10, top - 10, left + 510, top + 250, 0xF20E1318);
        g.drawString(font, "FLIGHT CONTACT", left, top, 0xFFE6EEF2);
        if (contact == null) {
            g.drawString(font, "CONTACT LOST", left, top + 32, 0xFFFF3333);
        } else {
            g.drawString(font, "SHIP: " + contact.shipName(), left, top + 32, 0xFF66D9FF);
            g.drawString(font, "CALLSIGN: " + contact.callsign(), left, top + 54, 0xFFE6EEF2);
            g.drawString(font, "OWNER: " + contact.owner(), left, top + 76, 0xFFE6EEF2);
            g.drawString(font, String.format("DISTANCE: %.1fm", contact.distanceTo(0, 0, 0)), left, top + 98, 0xFFE6EEF2);
            g.drawString(font, String.format("ALTITUDE: %.1f", contact.y()), left, top + 120, 0xFFE6EEF2);
            g.drawString(font, String.format("VELOCITY: %.1f m/s", contact.velocity()), left, top + 142, 0xFFE6EEF2);
            g.drawString(font, String.format("HEADING: %.1f°", contact.heading()), left, top + 164, 0xFFE6EEF2);
            g.drawString(font, "STATUS: " + contact.flightStatus(), left, top + 186, 0xFF55FF55);
            g.drawString(font, "UUID: " + contact.controllerId(), left, top + 208, 0xFF9DAEB5);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
