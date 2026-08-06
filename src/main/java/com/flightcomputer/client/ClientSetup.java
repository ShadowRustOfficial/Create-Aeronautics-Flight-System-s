package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.client.render.FlightControllerRenderer;
import com.flightcomputer.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.FLIGHT_CONTROLLER.get(), context -> new FlightControllerRenderer());
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        KeyInputHandler.register();
    }

    private ClientSetup() {}
}
