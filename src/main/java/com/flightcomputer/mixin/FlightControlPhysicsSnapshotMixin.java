package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.SableDynamicsReader;
import com.flightcomputer.control.VehicleState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the adaptive controller fed from Sable's live physics panel without coupling common code to Sable API types. */
@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager$Runtime")
public abstract class FlightControlPhysicsSnapshotMixin {
    @Shadow private VehicleState snapshot;

    @Inject(method = "update", at = @At("TAIL"))
    private void flightcomputer$readPhysicsPanel(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (snapshot == null || controller == null) return;
        SableDynamicsReader.readPhysicsPanelForController(controller, snapshot);
    }
}
