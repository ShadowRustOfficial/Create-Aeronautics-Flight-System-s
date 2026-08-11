package com.flightcomputer.client.map;

import com.flightcomputer.client.gui.FlightContactDetailsScreen;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Map interaction boundary: contact identity is opened on demand rather than rendered continuously. */
public final class FlightContactMarkerInteraction {
    private FlightContactMarkerInteraction() { }

    public static boolean rightClick(UUID contactId) {
        if (contactId == null || FlightContactRegistry.get(contactId) == null) return false;
        Minecraft.getInstance().setScreen(new FlightContactDetailsScreen(contactId));
        return true;
    }
}
