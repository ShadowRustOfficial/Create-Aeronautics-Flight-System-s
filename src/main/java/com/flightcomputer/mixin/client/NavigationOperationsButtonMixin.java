package com.flightcomputer.mixin.client;

import com.flightcomputer.client.gui.FlightOperationsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.Renderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the isolated operations console without mixing its widgets into the Navigation screen. */
@Mixin(com.flightcomputer.client.gui.NavigationConsoleScreen.class)
public abstract class NavigationOperationsButtonMixin {
    @Shadow private BlockPos controllerPos;
    @Shadow protected int width;
    @Shadow protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Inject(method = "init", at = @At("TAIL"))
    private void flightComputer$addOperationsButton(CallbackInfo ci) {
        int left = Math.max(10, (width - 640) / 2);
        addRenderableWidget(Button.builder(net.minecraft.network.chat.Component.literal("OPERATIONS"),
                b -> Minecraft.getInstance().setScreen(new FlightOperationsScreen(controllerPos)))
                .bounds(left + 480, 344, 150, 20).build());
    }
}
