package com.flightcomputer.mixin;

import com.flightcomputer.control.SixAxisStabilizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents the first measured hover demand from being slowly ramped from zero. */
@Mixin(SixAxisStabilizer.class)
public abstract class SixAxisStabilizerStartupMixin {
    @Shadow private double lastVerticalForce;
    @Shadow private boolean verticalForceInitialized;

    @Inject(method = "slewVerticalForce", at = @At("HEAD"), cancellable = true)
    private void flightComputer$seedHoverDemand(double requested, double mass, double dt,
                                                  CallbackInfoReturnable<Double> cir) {
        if (!Double.isFinite(requested) || requested <= 1.0e-9D) return;
        // The first tick can legitimately report zero external force because the Sable velocity
        // derivative has not produced a sample yet. Do not spend 8+ seconds slewing from zero to
        // hover on a large vessel. Accept the first real positive hover demand immediately; later
        // changes still use the normal slew limiter.
        if (!verticalForceInitialized || lastVerticalForce <= 1.0e-9D) {
            lastVerticalForce = requested;
            verticalForceInitialized = true;
            cir.setReturnValue(requested);
        }
    }
}
