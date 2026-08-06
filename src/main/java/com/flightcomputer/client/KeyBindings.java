package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KeyBindings {

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.flightcomputer.open_map",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_M,
            "key.categories.flightcomputer"
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }

    private KeyBindings() {}
}
