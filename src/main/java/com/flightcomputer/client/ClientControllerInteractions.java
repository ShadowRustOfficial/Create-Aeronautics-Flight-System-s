package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.avionics.PowerState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Client-only entry point for the non-container navigation console. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class ClientControllerInteractions {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FlightControllerBlock)
                || event.getEntity().isShiftKeyDown()
                || Minecraft.getInstance().screen != null) {
            return;
        }

        if (event.getLevel().getBlockEntity(event.getPos()) instanceof FlightControllerBlockEntity controller
                && controller.getEnergyStorage().getEnergyStored() > 0
                && controller.getPowerState() != PowerState.NO_POWER) {
            Minecraft.getInstance().setScreen(new NavigationConsoleScreen(event.getPos()));
        }
    }

    private ClientControllerInteractions() {}
}
