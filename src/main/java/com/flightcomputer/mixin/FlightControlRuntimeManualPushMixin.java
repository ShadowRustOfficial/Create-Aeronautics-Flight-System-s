package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightComputer;
import com.flightcomputer.control.ManualControlBridge;
import com.flightcomputer.control.ManualControlBridge.Pulse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager$Runtime")
public abstract class FlightControlRuntimeManualPushMixin {
    @Shadow private FlightComputer computer;

    @Inject(method = "control", at = @At(value = "INVOKE", target = "Lcom/flightcomputer/control/FlightComputer;tick(DZZ)V", shift = At.Shift.BEFORE))
    private void flightcomputer$applyPush(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (controller == null || computer == null) return;
        Pulse pulse = ManualControlBridge.consume(controller.getControllerId());
        if (pulse != null) computer.setManualPulse(pulse.axis(), pulse.value());
    }
}