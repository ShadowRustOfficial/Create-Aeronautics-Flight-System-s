package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.item.CoolingUpgradeItem;
import com.flightcomputer.item.FlightLinkToolItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(FlightComputer.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> FLIGHT_CONTROLLER = REGISTRY.register("flight_controller", () -> new BlockItem(
            ModBlocks.FLIGHT_CONTROLLER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> DEBUG_POWER_GENERATOR = REGISTRY.register("debug_power_generator", () -> new BlockItem(
            ModBlocks.DEBUG_POWER_GENERATOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> FLIGHT_THRUSTER = REGISTRY.register("flight_thruster", () -> new BlockItem(
            ModBlocks.FLIGHT_THRUSTER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, FlightLinkToolItem> FLIGHT_LINK_TOOL = REGISTRY.register("flight_link_tool", () -> new FlightLinkToolItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, CoolingUpgradeItem> BASIC_COOLING = REGISTRY.register("basic_cooling", () -> new CoolingUpgradeItem(new Item.Properties().stacksTo(1), CoolingUpgradeItem.Tier.BASIC));
    public static final DeferredHolder<Item, CoolingUpgradeItem> IMPROVED_COOLING = REGISTRY.register("improved_cooling", () -> new CoolingUpgradeItem(new Item.Properties().stacksTo(1), CoolingUpgradeItem.Tier.IMPROVED));
    public static final DeferredHolder<Item, CoolingUpgradeItem> ADVANCED_COOLING = REGISTRY.register("advanced_cooling", () -> new CoolingUpgradeItem(new Item.Properties().stacksTo(1), CoolingUpgradeItem.Tier.ADVANCED));

    private ModItems() {}
}
