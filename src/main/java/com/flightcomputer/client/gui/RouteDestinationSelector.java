package com.flightcomputer.client.gui;

import com.flightcomputer.FlightComputer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Compatibility shim retained for older integrations. Route destination controls are now
 * created directly by NavigationConsoleScreen.initRouteControls(), so this listener deliberately
 * does not inject a second set of buttons when tabs are rebuilt.
 */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class RouteDestinationSelector {
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Intentionally empty. The Route tab owns its controls directly.
    }

    private RouteDestinationSelector() {}
}
