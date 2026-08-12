package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KeyBindings {
    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.flightcomputer.open_map", InputConstants.Type.KEYSYM, InputConstants.KEY_M, "key.categories.flightcomputer");
    public static final KeyMapping OPEN_THERMAL = new KeyMapping(
            "key.flightcomputer.open_thermal", InputConstants.Type.KEYSYM, InputConstants.KEY_J, "key.categories.flightcomputer");
    public static final KeyMapping LINK_FOCUS = new KeyMapping(
            "key.flightcomputer.link_focus", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, "key.categories.flightcomputer");
    /** World-only mode switch for the Flight Link Tool; it is deliberately not active while a GUI is open. */
    public static final KeyMapping LINK_MODE_TOGGLE = new KeyMapping(
            "key.flightcomputer.link_mode_toggle", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.flightcomputer");
    public static final KeyMapping LINK_VECTOR_NEXT = new KeyMapping(
            "key.flightcomputer.link_vector_next", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_B, "key.categories.flightcomputer");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
        event.register(OPEN_THERMAL);
        event.register(LINK_FOCUS);
        event.register(LINK_MODE_TOGGLE);
        event.register(LINK_VECTOR_NEXT);
    }

    private KeyBindings() {}
}
