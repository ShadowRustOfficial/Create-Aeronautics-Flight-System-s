package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Autopilot owns pitch/translation; its cruise stabilizer retains the same attitude gains for roll. */
@Mixin(FlightControllerState.class)
public abstract class FlightControllerAutopilotIsolationMixin {
    @Inject(method = "apply", at = @At("RETURN"), cancellable = true)
    private void flightComputer$isolateAutopilot(FlightControllerAction action,
                                                   CallbackInfoReturnable<FlightControllerState> cir) {
        if (action != FlightControllerAction.TOGGLE_AUTOPILOT && action != FlightControllerAction.START_ROUTE) return;
        FlightControllerState state = cir.getReturnValue();
        if (state == null || state.flightMode() != com.flightcomputer.control.FlightMode.AUTOPILOT || !state.stabiliser()) return;
        cir.setReturnValue(new FlightControllerState(
                state.engaged(), false, state.flightMode(),
                state.altitudeHold(), state.headingHold(), state.positionHold(), state.velocityHold(),
                state.navigationEnabled(), state.routeActive()));
    }
}
