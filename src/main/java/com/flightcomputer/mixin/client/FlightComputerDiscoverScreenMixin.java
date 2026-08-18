package com.flightcomputer.mixin.client;

import com.flightcomputer.client.AudioUiSoundBridge;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Plays the controller-block discovery sound once when a Flight Computer screen opens. */
@Mixin(Screen.class)
public abstract class FlightComputerDiscoverScreenMixin {
    private static final Map<Screen, Boolean> PLAYED = Collections.synchronizedMap(new WeakHashMap<>());

    @Inject(method = "init", at = @At("TAIL"))
    private void flightcomputer$discover(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!AudioUiSoundBridge.isFlightComputerScreen(screen)) return;
        if (PLAYED.putIfAbsent(screen, Boolean.TRUE) == null) {
            AudioUiSoundBridge.play(AudioUiSoundBridge.Kind.DISCOVER);
        }
    }
}
