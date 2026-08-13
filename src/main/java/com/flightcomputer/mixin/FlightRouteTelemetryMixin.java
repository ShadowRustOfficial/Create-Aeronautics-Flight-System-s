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
    private static final java.util.Map<java.util.UUID, Long> LAST_SENT = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightComputer$routeTelemetry(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        long now = controller.getLevel().getGameTime();
        long previous = LAST_SENT.getOrDefault(controller.getControllerId(), Long.MIN_VALUE);
        if (now - previous < 2L) return;
        LAST_SENT.put(controller.getControllerId(), now);
        FlightRouteTelemetryNetwork.send(controller);
    }
}
