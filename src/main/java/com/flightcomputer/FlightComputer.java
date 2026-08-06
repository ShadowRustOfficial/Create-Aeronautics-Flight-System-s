package com.flightcomputer;

import com.flightcomputer.registry.ModBlockEntities;
import com.flightcomputer.registry.ModBlocks;
import com.flightcomputer.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import software.bernie.geckolib.GeckoLib;

@Mod(FlightComputer.MOD_ID)
public final class FlightComputer {

    public static final String MOD_ID = "flightcomputer";

    public FlightComputer(IEventBus modBus, ModContainer modContainer) {
        GeckoLib.initialize();

        ModBlocks.REGISTRY.register(modBus);
        ModItems.REGISTRY.register(modBus);
        ModBlockEntities.REGISTRY.register(modBus);

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Deferred init that needs to run after registries are populated goes here.
    }
}
