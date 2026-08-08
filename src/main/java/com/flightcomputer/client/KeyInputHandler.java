package com.flightcomputer.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.BlockHitResult;
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
            if (mc.level == null || mc.player == null) continue;
            if (!(mc.hitResult instanceof BlockHitResult hit)) continue;

            ClientLevel level = mc.level;
            if (!(level.getBlockEntity(hit.getBlockPos()) instanceof FlightControllerBlockEntity controller)) continue;
            if (controller.getEnergyStorage().getEnergyStored() <= 0 || controller.getPowerState() == PowerState.NO_POWER) continue;

            // The console is owned by the controller being interacted with, never by the player.
            mc.setScreen(new NavigationConsoleScreen(hit.getBlockPos()));
        }
    }

    private KeyInputHandler() {}
}
