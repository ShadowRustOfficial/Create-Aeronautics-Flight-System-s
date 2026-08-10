package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.client.map.TerrainMapCache;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client-only commands for maintaining the Flight Computer's independent map cache. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class FlightComputerClientCommands {

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("flightmap")
                .then(Commands.literal("refresh")
                        .executes(context -> refreshMap())));
    }

    private static int refreshMap() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0;
        }

        TerrainMapCache.clear();
        minecraft.gui.getChat().addMessage(Component.literal("Flight Map terrain cache refreshed."));
        return 1;
    }

    private FlightComputerClientCommands() {
    }
}
