package com.flightcomputer.client;

import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class KeyInputHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        NeoForge.EVENT_BUS.register(KeyInputHandler.class);
        registered = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_MAP.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                mc.setScreen(new NavigationConsoleScreen(mc.player.blockPosition()));
            }
        }
    }

    private KeyInputHandler() {}
}
