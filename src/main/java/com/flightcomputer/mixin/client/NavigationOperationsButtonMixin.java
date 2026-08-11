package com.flightcomputer.mixin.client;

import com.flightcomputer.client.gui.FlightOperationsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the isolated operations console without mixing its widgets into the Navigation screen. */
@Mixin(com.flightcomputer.client.gui.NavigationConsoleScreen.class)
public abstract class NavigationOperationsButtonMixin {
    @Shadow private BlockPos controllerPos;

    @Inject(method = "init", at = @At("TAIL"))
    private void flightComputer$addOperationsButton(CallbackInfo ci) {
        NavigationOperationsButtonMixin self = this;
        if (!(self instanceof net.minecraft.client.gui.screens.Screen screen)) return;
        int left = Math.max(10, (screen.width - 640) / 2);
        screen.addRenderableWidget(Button.builder(net.minecraft.network.chat.Component.literal("OPERATIONS"),
                b -> Minecraft.getInstance().setScreen(new FlightOperationsScreen(controllerPos)))
                .bounds(left + 480, 344, 150, 20).build());
    }
}
