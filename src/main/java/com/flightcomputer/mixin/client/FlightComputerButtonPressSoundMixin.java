package com.flightcomputer.mixin.client;

import com.flightcomputer.client.AudioUiSoundBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends the Flight Computer button's custom sound request when its action actually fires. */
@Mixin(Button.class)
public abstract class FlightComputerButtonPressSoundMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void flightcomputer$playBlockSound(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !AudioUiSoundBridge.isFlightComputerScreen(minecraft.screen)) return;
        AudioUiSoundBridge.playForButton((Button) (Object) this);
    }
}
