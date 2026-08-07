package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.item.FlightLinkToolItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items REGISTRY =
            DeferredRegister.createItems(FlightComputer.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> FLIGHT_CONTROLLER =
            REGISTRY.register("flight_controller", () -> new BlockItem(
                    ModBlocks.FLIGHT_CONTROLLER.get(),
                    new Item.Properties()
            ));

    public static final DeferredHolder<Item, FlightLinkToolItem> FLIGHT_LINK_TOOL =
            REGISTRY.register("flight_link_tool", () -> new FlightLinkToolItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
