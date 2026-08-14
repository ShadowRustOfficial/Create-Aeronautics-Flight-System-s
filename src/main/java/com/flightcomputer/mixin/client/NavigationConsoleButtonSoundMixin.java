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
 * Replaces the vanilla widget click sound only while the Flight Computer console is open.
 *
 * Button does not declare playDownSound in the 1.21.1 mappings used by this project;
 * the implementation lives on AbstractWidget. Targeting Button therefore causes a
 * fatal MixinApplyError during mod loading before the game can reach the main menu.
 */
@Mixin(AbstractWidget.class)
public abstract class NavigationConsoleButtonSoundMixin {

    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$replaceSound(SoundManager soundManager, CallbackInfo ci) {
        Object widget = this;
        if (!(widget instanceof Button)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof com.flightcomputer.client.gui.NavigationConsoleScreen)) {
            return;
        }

        AudioUiSoundBridge.playForButton((Button) widget);
        ci.cancel();
    }
}
