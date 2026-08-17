package com.flightcomputer.mixin.client;

import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.network.FlightIdentityNetwork;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends Diagnostics identity changes through the dedicated identity channel. */
@Mixin(NavigationConsoleScreen.class)
public abstract class NavigationConsoleIdentityButtonMixin {
    @Shadow private EditBox nameInput;
    @Shadow private EditBox flightIdInput;

    @Inject(method = "setIdentityName", at = @At("HEAD"), cancellable = true)
    private void flightComputer$setName(CallbackInfo ci) {
        NavigationConsoleScreen screen = (NavigationConsoleScreen)(Object)this;
        if (nameInput != null) FlightIdentityNetwork.setName(screen.controllerPos(), nameInput.getValue());
        ci.cancel();
    }

    @Inject(method = "setIdentityId", at = @At("HEAD"), cancellable = true)
    private void flightComputer$setId(CallbackInfo ci) {
        NavigationConsoleScreen screen = (NavigationConsoleScreen)(Object)this;
        if (flightIdInput != null) FlightIdentityNetwork.setId(screen.controllerPos(), flightIdInput.getValue());
        ci.cancel();
    }
}
