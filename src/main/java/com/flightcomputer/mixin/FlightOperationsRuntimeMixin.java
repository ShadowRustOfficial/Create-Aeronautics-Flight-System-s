package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightComputer;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.control.FlightOperationsRuntimeBridge;
import com.flightcomputer.control.ManualControlBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/** Keeps the operations layer attached to the existing runtime without duplicating its control loop. */
@Mixin(FlightControlRuntimeManager.class)
public abstract class FlightOperationsRuntimeMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/flightcomputer/control/FlightControlRuntimeManager$Runtime;control(Lcom/flightcomputer/block/FlightControllerBlockEntity;)V", shift = At.Shift.BEFORE))
    private static void flightComputer$applyManualPush(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (controller == null) return;
        ManualControlBridge.Pulse pulse = ManualControlBridge.consume(controller.getControllerId());
        if (pulse == null) return;
        try {
            Object runtime = FlightControlRuntimeManager.runtime(controller);
            Field computerField = runtime.getClass().getDeclaredField("computer");
            computerField.setAccessible(true);
            Object value = computerField.get(runtime);
            if (value instanceof FlightComputer computer) computer.setManualPulse(pulse.axis(), pulse.value());
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightComputer$reconcileOperations(FlightControllerBlockEntity controller, CallbackInfo ci) {
        FlightOperationsRuntimeBridge.reconcile(controller);
    }
}