package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.DebugPowerGeneratorBlockEntity;
import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FlightComputer.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlightControllerBlockEntity>> FLIGHT_CONTROLLER =
            REGISTRY.register("flight_controller", () -> BlockEntityType.Builder.of(
                    FlightControllerBlockEntity::new, ModBlocks.FLIGHT_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DebugPowerGeneratorBlockEntity>> DEBUG_POWER_GENERATOR =
            REGISTRY.register("debug_power_generator", () -> BlockEntityType.Builder.of(
                    DebugPowerGeneratorBlockEntity::new, ModBlocks.DEBUG_POWER_GENERATOR.get()).build(null));

    private ModBlockEntities() {}
}
