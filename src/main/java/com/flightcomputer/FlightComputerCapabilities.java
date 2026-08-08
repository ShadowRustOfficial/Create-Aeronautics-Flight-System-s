package com.flightcomputer;

import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Registers standard NeoForge capabilities for the controller block entity. */
public final class FlightComputerCapabilities {
    private FlightComputerCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.FLIGHT_CONTROLLER.get(),
                (controller, side) -> {
                    Direction back = controller.getBlockState().getValue(FlightControllerBlock.FACING).getOpposite();
                    if (side != null && side != back) return null;
                    return new InputOnlyEnergyStorage(controller.getEnergyStorage());
                });
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FLIGHT_CONTROLLER.get(),
                (controller, side) -> controller.getUpgradeHandler());
    }

    /**
     * The machine needs a non-zero internal extraction limit so its server tick can consume FE,
     * but the exposed block capability remains input-only. External cables cannot drain the controller.
     */
    private static final class InputOnlyEnergyStorage implements IEnergyStorage {
        private final IEnergyStorage delegate;

        private InputOnlyEnergyStorage(IEnergyStorage delegate) {
            this.delegate = delegate;
        }

        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            return delegate.receiveEnergy(maxReceive, simulate);
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override public int getEnergyStored() {
            return delegate.getEnergyStored();
        }

        @Override public int getMaxEnergyStored() {
            return delegate.getMaxEnergyStored();
        }

        @Override public boolean canExtract() {
            return false;
        }

        @Override public boolean canReceive() {
            return delegate.canReceive();
        }
    }
}
