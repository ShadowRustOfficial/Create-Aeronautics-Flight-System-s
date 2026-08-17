package com.flightcomputer.mixin.client;

import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the vanilla UI click sound only while the Navigation Console is open. */
@Mixin(AbstractWidget.class)
public abstract class NavigationConsoleMuteVanillaButtonSoundMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void flightComputer$muteVanillaButtonSound(SoundManager soundManager, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof NavigationConsoleScreen) ci.cancel();
    }
}
