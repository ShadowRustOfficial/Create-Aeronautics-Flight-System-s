package com.flightcomputer.mixin;

import com.flightcomputer.control.AdaptiveFlightTuner;
import com.flightcomputer.control.FlightComputer;
import com.flightcomputer.control.SixAxisStabilizer;
import com.flightcomputer.control.ThrusterRegistry;
import com.flightcomputer.control.VehicleState;
import com.flightcomputer.control.VehicleStateProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recomputes controller gains when the physical sub-level actually changes. */
@Mixin(FlightComputer.class)
public abstract class FlightComputerAdaptiveTuningMixin {
    @Shadow private VehicleStateProvider stateProvider;
    @Shadow private ThrusterRegistry registry;
    @Shadow private SixAxisStabilizer stabilizeStabilizer;
    @Shadow private SixAxisStabilizer cruiseStabilizer;

    private final AdaptiveFlightTuner flightcomputer$adaptiveTuner = new AdaptiveFlightTuner();

    @Inject(method = "tick(DZZ)V", at = @At("HEAD"))
    private void flightcomputer$adaptiveTuning(double dt, boolean stabiliserEnabled, boolean autopilotEnabled, CallbackInfo ci) {
        if(stateProvider==null||registry==null||stabilizeStabilizer==null||cruiseStabilizer==null)return;
        VehicleState state=stateProvider.getState();
        if(state!=null)flightcomputer$adaptiveTuner.update(state,registry,stabilizeStabilizer,cruiseStabilizer);
    }
}
