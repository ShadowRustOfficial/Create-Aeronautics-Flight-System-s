package com.flightcomputer.mixin.client;

import com.flightcomputer.client.AudioUiSoundBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.callback.CallbackInfo;

/** Replaces vanilla widget click audio for every Flight Computer screen. */
@Mixin(AbstractWidget.class)
public abstract class NavigationConsoleButtonSoundMixin {

    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$replaceSound(SoundManager soundManager, CallbackInfo ci) {
        Object widget = this;
        if (!(widget instanceof Button button)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!AudioUiSoundBridge.isFlightComputerScreen(minecraft.screen)) return;

        AudioUiSoundBridge.playForButton(button);
        // Do not allow the vanilla Minecraft click sound to play as well.
        ci.cancel();
    }
}
