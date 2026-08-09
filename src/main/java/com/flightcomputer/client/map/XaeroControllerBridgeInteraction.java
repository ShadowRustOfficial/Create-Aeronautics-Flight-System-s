package com.flightcomputer.client.map;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Intercepts Flight Controller opening so the Xaero-native bridge can own the map lifecycle. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class XaeroControllerBridgeInteraction {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FlightControllerBlock)
                || event.getEntity().isShiftKeyDown()
                || minecraft.screen != null) {
            return;
        }

        if (event.getLevel().getBlockEntity(event.getPos()) instanceof FlightControllerBlockEntity controller
                && controller.getEnergyStorage().getEnergyStored() > 0
                && controller.getPowerState() != PowerState.NO_POWER) {
            XaeroNativeMapBridge.requestNavigationConsole(event.getPos());
            event.setCanceled(true);
        }
    }

    private XaeroControllerBridgeInteraction() {}
}
