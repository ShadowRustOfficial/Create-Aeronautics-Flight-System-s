package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.control.NativeThrusterPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the already-commanded native thruster outputs to Sable exactly once per control tick. */
@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager$Runtime")
public abstract class FlightControlNativeThrusterPhysicsMixin {
    @Inject(method = "control", at = @At("RETURN"))
    private void flightcomputer$applyNativePhysics(FlightControllerBlockEntity controller, CallbackInfo ci) {
        NativeThrusterPhysics.tick(controller);
    }
}
