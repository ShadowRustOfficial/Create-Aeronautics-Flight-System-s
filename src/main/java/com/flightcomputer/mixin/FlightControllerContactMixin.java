package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.network.FlightControllerContactNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes powered controller discovery data from the existing server runtime tick. */
@Mixin(FlightControlRuntimeManager.class)
public abstract class FlightControllerContactMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightComputer$syncContact(FlightControllerBlockEntity controller, CallbackInfo ci) {
        FlightControllerContactNetwork.sync(controller);
    }
}
