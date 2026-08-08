package com.flightcomputer;

import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Registers standard NeoForge capabilities for the controller block entity. */
public final class FlightComputerCapabilities {
    private FlightComputerCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.FLIGHT_CONTROLLER.get(),
                (controller, side) -> {
                    Direction back = controller.getBlockState().getValue(FlightControllerBlock.FACING).getOpposite();
                    return side == null || side == back ? controller.getEnergyStorage() : null;
                });
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FLIGHT_CONTROLLER.get(),
                (controller, side) -> controller.getUpgradeHandler());
    }
}
