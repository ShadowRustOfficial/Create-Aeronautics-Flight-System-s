package com.flightcomputer.mixin.client;

import com.flightcomputer.client.AudioUiSoundBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends Flight Computer button audio through the dedicated server-authoritative block-audio path. */
@Mixin(Button.class)
public abstract class NavigationConsoleButtonServerSoundMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void flightComputer$serverBlockSound(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !AudioUiSoundBridge.isFlightComputerScreen(minecraft.screen)) return;
        AudioUiSoundBridge.playForButton((Button) (Object) this);
    }
}
