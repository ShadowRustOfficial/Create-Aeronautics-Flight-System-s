package com.flightcomputer.client.map;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Forwards only the MAP viewport input to Xaero's real GuiMap instance. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class XaeroNavigationInputBridge {
    private XaeroNavigationInputBridge() {}

    @SubscribeEvent
    public static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        Viewport viewport = viewport(screen);
        if (XaeroMapHost.forwardMouseClicked(event.getMouseX(), event.getMouseY(), event.getButton(),
                viewport.left, viewport.top, viewport.width, viewport.height)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        Viewport viewport = viewport(screen);
        if (XaeroMapHost.forwardMouseReleased(event.getMouseX(), event.getMouseY(), event.getButton(),
                viewport.left, viewport.top, viewport.width, viewport.height)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        Viewport viewport = viewport(screen);
        if (XaeroMapHost.forwardMouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(),
                event.getDragX(), event.getDragY(), viewport.left, viewport.top,
                viewport.width, viewport.height)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        Viewport viewport = viewport(screen);
        if (XaeroMapHost.forwardMouseScrolled(event.getMouseX(), event.getMouseY(),
                event.getScrollDeltaX(), event.getScrollDeltaY(), viewport.left, viewport.top,
                viewport.width, viewport.height)) {
            event.setCanceled(true);
        }
    }

    private static Viewport viewport(NavigationConsoleScreen screen) {
        int left = Math.max(10, (screen.width - 640) / 2) + 20;
        int top = 20 + 42 + 8;
        return new Viewport(left, top, 600, 260);
    }

    private record Viewport(int left, int top, int width, int height) {}
}
