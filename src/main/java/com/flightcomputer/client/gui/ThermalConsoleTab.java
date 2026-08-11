package com.flightcomputer.client.gui;

import com.flightcomputer.FlightComputer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Provides the Thermal entry point for the navigation console.
 *
 * Thermal is deliberately a separate full-screen console surface. The previous
 * implementation painted a semi-transparent thermal panel over the map during
 * ScreenEvent.Render.Pre, which caused map text and widgets to bleed through it
 * and made the controls disappear when the navigation screen was rebuilt.
 */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class ThermalConsoleTab {
    private static final Map<NavigationConsoleScreen, Button> BUTTONS = new WeakHashMap<>();
    private static final Method ADD_RENDERABLE_WIDGET = findAddRenderableWidget();

    private ThermalConsoleTab() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof NavigationConsoleScreen screen) {
            installButton(screen, event::addListener);
        }
    }

    /**
     * NavigationConsoleScreen rebuilds its widgets when changing pages without
     * emitting another ScreenEvent.Init.Post. Render.Pre is therefore used as a
     * small compatibility fallback to restore the Thermal entry point.
     */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        if (BUTTONS.containsKey(screen)) return;
        installButtonReflectively(screen);
    }

    private static void installButton(NavigationConsoleScreen screen, java.util.function.Consumer<Button> adder) {
        if (BUTTONS.containsKey(screen)) return;
        Button button = createButton(screen);
        BUTTONS.put(screen, button);
        adder.accept(button);
    }

    private static void installButtonReflectively(NavigationConsoleScreen screen) {
        if (ADD_RENDERABLE_WIDGET == null || BUTTONS.containsKey(screen)) return;
        Button button = createButton(screen);
        try {
            ADD_RENDERABLE_WIDGET.invoke(screen, button);
            BUTTONS.put(screen, button);
        } catch (ReflectiveOperationException ignored) {
            // The normal Init.Post path remains the primary path. This fallback
            // only exists because the navigation screen rebuilds widgets itself.
        }
    }

    private static Button createButton(NavigationConsoleScreen screen) {
        int left = Math.max(10, (screen.width - 640) / 2);
        return Button.builder(Component.literal("THERMAL"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.setScreen(new ThermalConsoleScreen(screen.controllerPos()));
        }).bounds(left + 480, 46, 150, 20).build();
    }

    private static Method findAddRenderableWidget() {
        try {
            Method method = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod(
                    "addRenderableWidget", net.minecraft.client.gui.components.events.GuiEventListener.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
