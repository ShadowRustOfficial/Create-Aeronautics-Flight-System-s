package com.flightcomputer.mixin.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.map.FlightContactRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the map centred on the local controller only when no remote Flight Controller is being tracked. */
@Mixin(NavigationConsoleScreen.class)
public abstract class NavigationConsoleMapTrackingMixin {
    @Shadow private FlightControllerBlockEntity controller;
    @Shadow private double centerX;
    @Shadow private double centerZ;
    @Shadow private double controllerX;
    @Shadow private double controllerZ;

    @Unique private boolean flightcomputer$followController = true;

    @Inject(method = "init", at = @At("TAIL"))
    private void flightcomputer$initialiseMapTracking(CallbackInfo ci) {
        flightcomputer$followController = true;
        FlightContactRegistry.setTrackedController(null);
        flightcomputer$followTelemetryPosition();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void flightcomputer$trackController(CallbackInfo ci) {
        if (flightcomputer$followController && !FlightContactRegistry.isTrackingRemote()) {
            flightcomputer$followTelemetryPosition();
        }
    }

    @Inject(method = "centreController", at = @At("HEAD"))
    private void flightcomputer$enableControllerFollow(CallbackInfo ci) {
        flightcomputer$followController = true;
        FlightContactRegistry.setTrackedController(null);
    }

    @Inject(method = "centrePlayer", at = @At("HEAD"))
    private void flightcomputer$disableControllerFollow(CallbackInfo ci) {
        flightcomputer$followController = false;
        FlightContactRegistry.setTrackedController(null);
    }

    @Unique
    private void flightcomputer$followTelemetryPosition() {
        if (controller == null) return;
        var telemetry = FlightComputerTelemetryClient.get(controller.getControllerId());
        if (telemetry != null) {
            controllerX = telemetry.x();
            controllerZ = telemetry.z();
            centerX = telemetry.x();
            centerZ = telemetry.z();
        } else {
            centerX = controllerX;
            centerZ = controllerZ;
        }
    }
}
