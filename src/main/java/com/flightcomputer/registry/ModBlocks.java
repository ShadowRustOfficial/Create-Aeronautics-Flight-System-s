package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.DebugPowerGeneratorBlock;
import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.block.FlightThrusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(FlightComputer.MOD_ID);

    public static final DeferredHolder<Block, FlightControllerBlock> FLIGHT_CONTROLLER =
            REGISTRY.register("flight_controller", () -> new FlightControllerBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredHolder<Block, DebugPowerGeneratorBlock> DEBUG_POWER_GENERATOR =
            REGISTRY.register("debug_power_generator", () -> new DebugPowerGeneratorBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f, 4.0f).sound(SoundType.METAL)));

    /** Native six-axis physical actuator. Thrust direction follows its FACING property. */
    public static final DeferredHolder<Block, FlightThrusterBlock> FLIGHT_THRUSTER =
            REGISTRY.register("flight_thruster", () -> new FlightThrusterBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 5.0f).sound(SoundType.METAL).noOcclusion()));

    private ModBlocks() {}
}
