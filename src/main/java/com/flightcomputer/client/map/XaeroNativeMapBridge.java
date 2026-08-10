package com.flightcomputer.client.map;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client-side bridge host. It never constructs GuiMap. Instead it asks Xaero to open
 * its own normal map, captures the resulting live instance, then replaces the opening
 * screen with Flight Computer while keeping that Xaero instance as the map renderer.
 */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class XaeroNativeMapBridge {
    private static BlockPos pendingController;
    private static BlockPos activeController;
    private static boolean navigationConsoleActive;
    private static String status = "Bridge loaded; waiting for Xaero World Map.";

    private XaeroNativeMapBridge() {}

    public static void requestNavigationConsole(BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            status = "Cannot request Xaero map: client world is unavailable.";
            return;
        }

        activeController = controllerPos;
        navigationConsoleActive = true;

        KeyMapping mapKey = findXaeroWorldMapKey(minecraft);
        if (mapKey == null) {
            pendingController = null;
            status = "Xaero World Map is present but its World Map key mapping could not be found.";
            minecraft.setScreen(new NavigationConsoleScreen(controllerPos));
            return;
        }

        pendingController = controllerPos;
        status = "Requesting Xaero World Map native screen through its normal key binding.";
        KeyMapping.click(mapKey.getKey());
    }

    /** Called by the screen lifecycle when the navigation console is actually closed. */
    public static void onNavigationClosed() {
        navigationConsoleActive = false;
        pendingController = null;
        activeController = null;

        // Never retain Xaero's live Screen after leaving the Flight Computer. Retaining
        // it can keep Xaero's GUI/render state alive after the world or screen changes.
        XaeroMapHost.clearNativeScreen();
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Prevent Xaero's normal World Map key from replacing the Navigation Console while
     * it is already hosting the captured GuiMap. The real Xaero key remains usable once
     * the console is closed.
     */
    @SubscribeEvent
    public static void onNavigationKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!navigationConsoleActive || !(event.getScreen() instanceof NavigationConsoleScreen)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && isXaeroWorldMapKey(minecraft, event.getKeyCode(), event.getScanCode())) {
            event.setCanceled(true);
        }
    }

    /** Clear the host state when the player leaves the Navigation Console. */
    @SubscribeEvent
    public static void onNavigationScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof NavigationConsoleScreen) {
            onNavigationClosed();
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen == null || !"xaero.map.gui.GuiMap".equals(screen.getClass().getName())) return;

        XaeroMapHost.captureNativeScreen(screen);
        status = "Captured live Xaero GuiMap instance.";

        BlockPos controllerPos = pendingController;
        if (controllerPos != null) {
            initialiseAndReturnToConsole(event, screen, controllerPos);
            return;
        }

        // Defensive fallback: if another route opens GuiMap while the console is active,
        // do not allow Xaero's full-screen screen to take over the Flight Computer.
        if (navigationConsoleActive && activeController != null) {
            event.setNewScreen(new NavigationConsoleScreen(activeController));
            status = "Blocked Xaero full-screen map takeover; retained native map renderer.";
        }
    }

    private static void initialiseAndReturnToConsole(ScreenEvent.Opening event, Screen screen, BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            // Xaero created this object itself. We only initialise the real instance using
            // the same public Screen lifecycle that Minecraft normally uses.
            screen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
            event.setNewScreen(new NavigationConsoleScreen(controllerPos));
            pendingController = null;
            navigationConsoleActive = true;
            activeController = controllerPos;
            status = "Xaero native map captured; Flight Computer Navigation Console is now hosting it.";
        } catch (RuntimeException exception) {
            pendingController = null;
            status = "Failed to initialise captured Xaero GuiMap: "
                    + exception.getClass().getSimpleName() + " - " + safeMessage(exception);
            event.setNewScreen(new NavigationConsoleScreen(controllerPos));
        }
    }

    public static String status() {
        return status;
    }

    private static boolean isXaeroWorldMapKey(Minecraft minecraft, int keyCode, int scanCode) {
        KeyMapping mapping = findXaeroWorldMapKey(minecraft);
        return mapping != null && mapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    private static KeyMapping findXaeroWorldMapKey(Minecraft minecraft) {
        KeyMapping best = null;
        int bestScore = Integer.MIN_VALUE;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            String name = mapping.getName();
            if (name == null) continue;
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("xaero") || !lower.contains("map") || lower.contains("minimap")) continue;
            if (mapping.isUnbound()) continue;

            int score = 0;
            if (lower.contains("world")) score += 10;
            if (lower.contains("open")) score += 5;
            if (lower.contains("map")) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = mapping;
            }
        }
        return best;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
