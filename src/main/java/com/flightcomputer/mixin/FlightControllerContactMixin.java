package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightControllerContactNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes powered Flight Controllers directly from the controller's authoritative server tick. */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerContactMixin {
    @Inject(method = "serverTick", at = @At("TAIL"))
    private void flightComputer$syncContact(CallbackInfo ci) {
        FlightControllerContactNetwork.sync((FlightControllerBlockEntity)(Object)this);
    }
}
