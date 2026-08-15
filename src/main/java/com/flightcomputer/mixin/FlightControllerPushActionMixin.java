package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerActionResult;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.ControlAxis;
import com.flightcomputer.control.ManualControlBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerPushActionMixin {
    @Inject(method = "applyAction", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$handlePush(FlightControllerAction action,
                                           CallbackInfoReturnable<FlightControllerActionResult> cir) {
        if (action == null || !action.isIndependentPush()) return;

        FlightControllerBlockEntity controller = (FlightControllerBlockEntity)(Object)this;
        if (!controller.isOperationPermitted(action)) {
            cir.setReturnValue(FlightControllerActionResult.rejected(
                    controller.getControllerState(), action,
                    controller.isThermalLockout() ? "THERMAL_SHUTDOWN" : "NO_POWER"));
            return;
        }
        if (!controller.isEngaged()) {
            cir.setReturnValue(FlightControllerActionResult.rejected(
                    controller.getControllerState(), action, "SYSTEM_DISENGAGED"));
            return;
        }

        ControlAxis axis;
        double value;
        switch (action) {
            case PUSH_FORWARD -> { axis = ControlAxis.LONGITUDINAL; value = 0.65D; }
            case PUSH_BACKWARD -> { axis = ControlAxis.LONGITUDINAL; value = -0.65D; }
            case PUSH_UP -> { axis = ControlAxis.VERTICAL; value = 0.65D; }
            case PUSH_DOWN -> { axis = ControlAxis.VERTICAL; value = -0.65D; }
            case PUSH_LEFT -> { axis = ControlAxis.LATERAL; value = -0.65D; }
            case PUSH_RIGHT -> { axis = ControlAxis.LATERAL; value = 0.65D; }
            default -> { return; }
        }

        ManualControlBridge.request(controller.getControllerId(), axis, value);
        cir.setReturnValue(FlightControllerActionResult.accepted(controller.getControllerState(), action, "PUSH"));
    }
}