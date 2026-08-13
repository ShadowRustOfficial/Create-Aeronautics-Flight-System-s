package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.network.FlightRouteTelemetryNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends authoritative route/control state after the existing server control tick. */
@Mixin(FlightControlRuntimeManager.class)
public abstract class FlightRouteTelemetryMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightComputer$routeTelemetry(FlightControllerBlockEntity controller, CallbackInfo ci) {
        FlightRouteTelemetryNetwork.send(controller);
    }
}
