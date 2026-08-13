package com.flightcomputer.client;

import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/** Client-side UI sound policy shared by the navigation/flight-console screens. */
public final class UiSoundClient {
    private UiSoundClient() { }

    public static void interact() {
        play(ModSounds.UI_INTERACT.get(), 0.72F, 1.0F);
    }

    public static void tab() {
        play(ModSounds.UI_OPEN.get(), 0.78F, 1.0F);
    }

    public static void discover() {
        play(ModSounds.UI_DISCOVER.get(), 0.82F, 1.0F);
    }

    public static void toggle(boolean enabled) {
        play(enabled ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get(), 0.78F, 1.0F);
    }

    public static void playAt(BlockPos pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || pos == null || sound == null) return;
        mc.level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound,
                SoundSource.BLOCKS, volume, pitch, false);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null || sound == null) return;
        player.playSound(sound, volume, pitch);
    }
}
