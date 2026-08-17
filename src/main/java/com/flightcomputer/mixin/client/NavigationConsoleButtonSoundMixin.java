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

/** Replaces vanilla Flight Computer widget click audio with the server-authoritative block sound. */
@Mixin(AbstractWidget.class)
public abstract class NavigationConsoleButtonSoundMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$replaceVanillaSound(SoundManager soundManager, CallbackInfo ci) {
        Object widget = this;
        if (!(widget instanceof Button button)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!AudioUiSoundBridge.isFlightComputerScreen(minecraft.screen)) return;

        // This is the actual vanilla widget sound path. Replace it here so every Flight Computer
        // button is guaranteed to request the same server-side block playback used by the
        // Emergency Shutdown path. No client-local sound is emitted.
        AudioUiSoundBridge.playForButton(button);
        ci.cancel();
    }
}
