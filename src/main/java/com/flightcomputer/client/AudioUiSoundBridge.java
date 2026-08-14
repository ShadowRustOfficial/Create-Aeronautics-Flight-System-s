package com.flightcomputer.client;

import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Centralised UI sound policy for the Flight Computer console. */
public final class AudioUiSoundBridge {
    private AudioUiSoundBridge() {}

    public enum Kind { TOGGLE_ON, TOGGLE_OFF, TAB, INTERACT, DISCOVER }

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

    /** Called by the Button sound mixin immediately before vanilla would play its click sound. */
    public static void playForButton(Button button) {
        if (!(Minecraft.getInstance().screen instanceof NavigationConsoleScreen) || button == null) return;
        String text = button.getMessage().getString().trim().toUpperCase(java.util.Locale.ROOT);
        if (text.equals("MAP") || text.equals("ROUTE") || text.equals("FLIGHT CONTROL") || text.equals("DIAGNOSTICS")) {
            play(Kind.TAB);
            return;
        }

        // These controls expose an explicit state in their label. The sound describes the state
        // being entered, so clicking an ON control produces OFF and clicking an OFF control produces ON.
        boolean toggle = text.contains("SYSTEM:") || text.contains("STABILISER:") || text.contains("AUTOPILOT:")
                || text.contains("ALTITUDE HOLD:") || text.contains("HEADING HOLD:")
                || text.contains("POSITION HOLD:") || text.contains("VELOCITY HOLD:")
                || text.contains("NAVIGATION:") || text.startsWith("TERRAIN:")
                || text.startsWith("FLIGHT MAP:") || text.startsWith("WAYPOINTS:");
        if (toggle) {
            boolean currentlyOn = text.contains(": ON") || text.contains(": ENGAGED");
            play(currentlyOn ? Kind.TOGGLE_OFF : Kind.TOGGLE_ON);
            return;
        }
        play(Kind.INTERACT);
    }

    public static boolean toggleState(boolean enabled) { return enabled; }
}
