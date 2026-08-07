package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, FlightComputer.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLIGHT_COMPUTER = REGISTRY.register("flight_computer", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.flightcomputer"))
            .icon(() -> new ItemStack(ModItems.FLIGHT_CONTROLLER.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.FLIGHT_CONTROLLER.get());
                output.accept(ModItems.FLIGHT_LINK_TOOL.get());
            }).build());
    private ModCreativeTabs() {}
}
