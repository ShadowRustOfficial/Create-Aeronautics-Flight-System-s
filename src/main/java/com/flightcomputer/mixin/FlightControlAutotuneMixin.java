package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.autotune.AutoTuneRuntimeBridge;
import com.flightcomputer.control.FlightControlRuntimeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlightControlRuntimeManager.class)
public abstract class FlightControlAutotuneMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightcomputer$autoTune(FlightControllerBlockEntity controller, CallbackInfo ci) {
        AutoTuneRuntimeBridge.tick(controller);
    }
}
