package com.flightcomputer.mixin.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Navigation Console map centred on the Flight Computer's authoritative logical
 * position while preserving manual map panning. The controller BlockEntity may remain at a
 * Sable plot/storage coordinate, so the map must follow telemetry rather than raw BlockPos.
 */
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
        flightcomputer$followTelemetryPosition();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void flightcomputer$trackController(CallbackInfo ci) {
        if (flightcomputer$followController) {
            flightcomputer$followTelemetryPosition();
        }
    }

    /**
     * NavigationConsoleScreen.mouseDragged returns boolean in Minecraft 1.21.1, so this
     * injection must receive CallbackInfoReturnable rather than CallbackInfo.
     */
    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void flightcomputer$manualPanDisablesFollow(double mouseX, double mouseY, int button,
                                                         double dragX, double dragY,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) {
            flightcomputer$followController = false;
        }
    }

    @Inject(method = "centreController", at = @At("HEAD"))
    private void flightcomputer$enableControllerFollow(CallbackInfo ci) {
        flightcomputer$followController = true;
    }

    @Inject(method = "centrePlayer", at = @At("HEAD"))
    private void flightcomputer$disableControllerFollow(CallbackInfo ci) {
        flightcomputer$followController = false;
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
