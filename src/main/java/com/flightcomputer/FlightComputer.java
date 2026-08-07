package com.flightcomputer;

import com.flightcomputer.registry.ModBlockEntities;
import com.flightcomputer.registry.ModBlocks;
import com.flightcomputer.registry.ModItems;
import com.flightcomputer.registry.ModCreativeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(FlightComputer.MOD_ID)
public final class FlightComputer {
    public static final String MOD_ID = "flightcomputer";

    public FlightComputer(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.REGISTRY.register(modBus);
        ModItems.REGISTRY.register(modBus);
        ModBlockEntities.REGISTRY.register(modBus);
        ModCreativeTabs.REGISTRY.register(modBus);

        modBus.addListener(FlightComputerCapabilities::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, FlightComputerConfig.SPEC);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Reserved for deferred common setup.
    }
}
