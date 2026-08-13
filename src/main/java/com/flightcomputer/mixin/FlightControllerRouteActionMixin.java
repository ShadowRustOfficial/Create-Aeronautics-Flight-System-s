package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerActionResult;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Route Active/AUTOPILOT state from being enabled without a real destination. */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerRouteActionMixin {
    @Inject(method = "applyAction", at = @At("HEAD"), cancellable = true)
    private void flightComputer$guardStartRoute(FlightControllerAction action,
                                                 CallbackInfoReturnable<FlightControllerActionResult> cir) {
        if (action != FlightControllerAction.START_ROUTE) return;
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (!FlightControlRuntimeManager.hasTarget(controller)) {
            cir.setReturnValue(FlightControllerActionResult.rejected(
                    controller.getControllerState(), action, "NO_NAVIGATION_TARGET"));
        }
    }
}
