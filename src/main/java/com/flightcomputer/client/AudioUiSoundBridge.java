package com.flightcomputer.client;

import com.flightcomputer.client.gui.CoolingConsoleScreen;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

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
        var holder = switch (kind) {
            case TOGGLE_ON -> ModSounds.UI_TOGGLE_ON;
            case TOGGLE_OFF -> ModSounds.UI_TOGGLE_OFF;
            case TAB -> ModSounds.UI_OPEN;
            case DISCOVER -> ModSounds.UI_DISCOVER;
            case INTERACT -> ModSounds.UI_INTERACT;
        };
        mc.getSoundManager().play(SimpleSoundInstance.forUI(holder.get(), 1.0F));
    }

    /** Called by the shared Button sound hook immediately before vanilla would play its click sound. */
    public static void playForButton(Button button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == null || mc == null || !isFlightComputerScreen(mc.screen)) return;

        String text = button.getMessage().getString().trim().toUpperCase(java.util.Locale.ROOT);

        // Navigation / Thermal / Cooling are panel tabs. The four Navigation Console tabs are
        // included here as well. All use the supplied UI Open sound, never the vanilla click.
        if (text.equals("MAP") || text.equals("ROUTE") || text.equals("FLIGHT CONTROL")
                || text.equals("DIAGNOSTICS") || text.equals("NAVIGATION")
                || text.equals("THERMAL") || text.equals("COOLING")) {
            play(Kind.TAB);
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
            // The button label represents the state before this click, so play the state being entered.
            boolean currentlyOn = text.contains(": ON") || text.contains(": ENGAGED");
            play(currentlyOn ? Kind.TOGGLE_OFF : Kind.TOGGLE_ON);
            return;
        }

        play(Kind.INTERACT);
    }
}
