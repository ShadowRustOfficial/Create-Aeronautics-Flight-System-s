package com.flightcomputer.client.gui;

import com.flightcomputer.FlightComputer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Provides the Thermal entry point for the navigation console.
 *
 * Thermal is deliberately a separate full-screen console surface. The previous
 * implementation painted a semi-transparent thermal panel over the map during
 * ScreenEvent.Render.Pre, which caused map text and widgets to bleed through it
 * and also made the controls disappear when the navigation screen was rebuilt.
 */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class ThermalConsoleTab {
    private static final Map<NavigationConsoleScreen, Button> BUTTONS = new WeakHashMap<>();

    private ThermalConsoleTab() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof NavigationConsoleScreen screen) {
            installButton(screen, event::addListener);
        }
    }

    /**
     * NavigationConsoleScreen rebuilds its widgets when changing pages without
     * emitting another ScreenEvent.Init.Post. Render.Pre is therefore also used
     * as a lightweight safety net to keep the Thermal entry point present.
     */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        if (BUTTONS.containsKey(screen)) return;
        installButton(screen, screen::addRenderableWidget);
    }

    private static void installButton(NavigationConsoleScreen screen, java.util.function.Consumer<Button> adder) {
        if (BUTTONS.containsKey(screen)) return;
        int left = Math.max(10, (screen.width - 640) / 2);
        Button button = Button.builder(Component.literal("THERMAL"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.setScreen(new ThermalConsoleScreen(screen.controllerPos()));
        }).bounds(left + 480, 46, 150, 20).build();
        BUTTONS.put(screen, button);
        adder.accept(button);
    }
}
