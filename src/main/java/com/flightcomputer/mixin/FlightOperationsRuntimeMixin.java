package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.control.FlightOperationsRuntimeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the operations layer attached to the existing runtime without duplicating its control loop. */
@Mixin(FlightControlRuntimeManager.class)
public abstract class FlightOperationsRuntimeMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightComputer$reconcileOperations(FlightControllerBlockEntity controller, CallbackInfo ci) {
        FlightOperationsRuntimeBridge.reconcile(controller);
    }
}
