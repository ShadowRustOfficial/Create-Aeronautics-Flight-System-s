package com.flightcomputer.mixin.client;

import com.flightcomputer.client.AudioUiSoundBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flight Computer buttons use server-authoritative block audio.
 * The actual sound is played by the Flight Controller with Level.playSound(..., SoundSource.BLOCKS),
 * exactly like Emergency Shutdown. Vanilla widget audio is suppressed separately.
 */
@Mixin(AbstractWidget.class)
public abstract class NavigationConsoleButtonSoundMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$muteVanillaSound(SoundManager soundManager, CallbackInfo ci) {
        Object widget = this;
        if (!(widget instanceof Button)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!AudioUiSoundBridge.isFlightComputerScreen(minecraft.screen)) return;
        ci.cancel();
    }

    /** Fires once from the actual Button action rather than from widget audio handling. */
    @Inject(method = "onPress", at = @At("HEAD"))
    private void flightcomputer$playBlockSound(CallbackInfo ci) {
        Object widget = this;
        if (!(widget instanceof Button button)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!AudioUiSoundBridge.isFlightComputerScreen(minecraft.screen)) return;
        AudioUiSoundBridge.playForButton(button);
    }
}
