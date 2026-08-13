package com.flightcomputer.client;

import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;

/** Centralised, non-looping UI sound policy for the Flight Computer console. */
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
            case DISCOVER -> ModSounds.UI_OPEN;
            case INTERACT -> ModSounds.UI_INTERACT;
        };
        mc.getSoundManager().play(SimpleSoundInstance.forUI(holder.get(), 1.0F));
    }

    public static boolean toggleState(boolean enabled) {
        return enabled;
    }
}
