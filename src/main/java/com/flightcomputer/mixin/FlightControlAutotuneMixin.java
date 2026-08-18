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
    /** Adaptive PID profile application belongs at the controller-runtime boundary. */
    @Inject(method = "tick(Lcom/flightcomputer/block/FlightControllerBlockEntity;)V", at = @At("TAIL"), require = 1)
    private static void flightcomputer$autoTune(FlightControllerBlockEntity controller, CallbackInfo ci) {
        AutoTuneRuntimeBridge.tick(controller);
    }
}
