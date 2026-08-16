package com.flightcomputer.client;

import com.flightcomputer.client.gui.CoolingConsoleScreen;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.network.FlightComputerUiSoundNetwork;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.lang.reflect.Field;

/** Centralised UI sound policy for all Flight Computer screens. */
public final class AudioUiSoundBridge {
    private AudioUiSoundBridge() {}

    public enum Kind { TOGGLE_ON, TOGGLE_OFF, TAB, INTERACT, DISCOVER }

    public static boolean isFlightComputerScreen(Screen screen) {
        return screen instanceof NavigationConsoleScreen
                || screen instanceof ThermalConsoleScreen
                || screen instanceof CoolingConsoleScreen;
    }

    public static void play(Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        BlockPos controllerPos = controllerPos(mc.screen);
        if (controllerPos != null && isFlightComputerScreen(mc.screen)) {
            request(controllerPos, kind);
            return;
        }

        var holder = switch (kind) {
            case TOGGLE_ON -> ModSounds.UI_TOGGLE_ON;
            case TOGGLE_OFF -> ModSounds.UI_TOGGLE_OFF;
            case TAB -> ModSounds.UI_OPEN;
            case DISCOVER -> ModSounds.UI_DISCOVER;
            case INTERACT -> ModSounds.UI_INTERACT;
        };
        mc.getSoundManager().play(SimpleSoundInstance.forUI(holder.get(), 1.0F));
    }

    /** Guaranteed local feedback for Navigation Console buttons; the block/server audio path is used elsewhere. */
    public static void playForButton(Button button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == null || mc == null || !isFlightComputerScreen(mc.screen)) return;

        String text = button.getMessage().getString().trim().toUpperCase(java.util.Locale.ROOT);

        if (text.equals("MAP") || text.equals("ROUTE") || text.equals("FLIGHT CONTROL")
                || text.equals("DIAGNOSTICS") || text.equals("THERMAL") || text.equals("COOLING")) {
            UiSoundClient.tab();
            return;
        }

        boolean toggle = text.contains("SYSTEM:")
                || text.contains("STABILISER:")
                || text.contains("AUTOPILOT:")
                || text.contains("ALTITUDE HOLD:")
                || text.contains("HEADING HOLD:")
                || text.contains("POSITION HOLD:")
                || text.contains("VELOCITY HOLD:")
                || text.contains("NAVIGATION:")
                || text.startsWith("TERRAIN:")
                || text.startsWith("FLIGHT MAP:")
                || text.startsWith("WAYPOINTS:");

        if (toggle) {
            boolean currentlyOn = text.contains(": ON") || text.contains(": ENGAGED");
            UiSoundClient.toggle(!currentlyOn);
            return;
        }

        UiSoundClient.interact();
    }

    private static void request(BlockPos controllerPos, Kind kind) {
        FlightComputerUiSoundNetwork.request(controllerPos, soundId(kind));
    }

    private static int soundId(Kind kind) {
        return switch (kind) {
            case TOGGLE_ON -> 0;
            case TOGGLE_OFF -> 1;
            case TAB -> 2;
            case INTERACT -> 3;
            case DISCOVER -> 4;
        };
    }

    private static BlockPos controllerPos(Screen screen) {
        if (screen == null) return null;
        try {
            Field field = screen.getClass().getDeclaredField("controllerPos");
            field.setAccessible(true);
            Object value = field.get(screen);
            return value instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
