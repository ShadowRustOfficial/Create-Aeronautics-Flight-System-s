package com.flightcomputer.client.map;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;

/** Removes obsolete zoom controls from the Navigation Console after its widgets are created. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class NavigationMapUiSanitizer {
    private NavigationMapUiSanitizer() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen)) return;

        // ScreenEvent.Init.Post exposes a live listener collection. Xaero can cause a
        // screen transition while its input state is being processed, so removing from
        // that collection during iteration can throw ConcurrentModificationException.
        // Iterate over a snapshot and keep the existing removal behaviour unchanged.
        for (GuiEventListener listener : new ArrayList<>(event.getListenersList())) {
            if (!(listener instanceof Button button)) continue;
            String label = button.getMessage().getString().trim();
            if (label.equals("−") || label.equals("+") || label.equalsIgnoreCase("ZOOM")) {
                event.removeListener(listener);
            }
        }
    }
}
