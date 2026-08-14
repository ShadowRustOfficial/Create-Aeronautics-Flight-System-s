package com.flightcomputer.mixin.client;

import com.flightcomputer.client.AudioUiSoundBridge;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class NavigationConsoleButtonSoundMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$replaceSound(SoundManager soundManager, CallbackInfo ci) {
        if (!(this instanceof Button button)) return;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.flightcomputer.client.gui.NavigationConsoleScreen) {
            AudioUiSoundBridge.playForButton(button);
            ci.cancel();
        }
    }
}
