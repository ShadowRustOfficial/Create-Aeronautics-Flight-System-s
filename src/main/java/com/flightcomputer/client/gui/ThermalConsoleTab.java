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

/** Adds only navigation entry buttons; thermal/cooling content is rendered on dedicated screens. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class ThermalConsoleTab {
    private static final Map<NavigationConsoleScreen, Button> THERMAL_BUTTONS = new WeakHashMap<>();
    private static final Map<NavigationConsoleScreen, Button> COOLING_BUTTONS = new WeakHashMap<>();
    private static final Method ADD_RENDERABLE_WIDGET = findAddRenderableWidget();

    private ThermalConsoleTab() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof NavigationConsoleScreen screen) {
            installButtons(screen, event::addListener);
        }
    }

    /** NavigationConsoleScreen rebuilds widgets while changing pages, so restore the two entry buttons if necessary. */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        if (THERMAL_BUTTONS.containsKey(screen) && COOLING_BUTTONS.containsKey(screen)) return;
        installButtonsReflectively(screen);
    }

    private static void installButtons(NavigationConsoleScreen screen, java.util.function.Consumer<Button> adder) {
        if (THERMAL_BUTTONS.containsKey(screen)) return;
        Button thermal = createThermalButton(screen);
        Button cooling = createCoolingButton(screen);
        THERMAL_BUTTONS.put(screen, thermal);
        COOLING_BUTTONS.put(screen, cooling);
        adder.accept(thermal);
        adder.accept(cooling);
    }

    private static void installButtonsReflectively(NavigationConsoleScreen screen) {
        if (ADD_RENDERABLE_WIDGET == null || THERMAL_BUTTONS.containsKey(screen)) return;
        Button thermal = createThermalButton(screen);
        Button cooling = createCoolingButton(screen);
        try {
            ADD_RENDERABLE_WIDGET.invoke(screen, thermal);
            ADD_RENDERABLE_WIDGET.invoke(screen, cooling);
            THERMAL_BUTTONS.put(screen, thermal);
            COOLING_BUTTONS.put(screen, cooling);
        } catch (ReflectiveOperationException ignored) {
            // Init.Post remains the normal registration path.
        }
    }

    private static Button createThermalButton(NavigationConsoleScreen screen) {
        int left = Math.max(10, (screen.width - 640) / 2);
        return Button.builder(Component.literal("THERMAL"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.setScreen(new ThermalConsoleScreen(screen.controllerPos()));
        }).bounds(left + 480, 46, 75, 20).build();
    }

    private static Button createCoolingButton(NavigationConsoleScreen screen) {
        int left = Math.max(10, (screen.width - 640) / 2);
        return Button.builder(Component.literal("COOLING"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.setScreen(new CoolingConsoleScreen(screen.controllerPos()));
        }).bounds(left + 555, 46, 75, 20).build();
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
