package com.flightcomputer.client.xaerobridge.mixin;

import com.flightcomputer.client.map.XaeroMapHost;
import com.flightcomputer.client.xaerobridge.internal.BridgeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero bridge hook for Minecraft 1.21.1 / Xaero World Map 1.44.2.
 *
 * We deliberately use the safe tail hook rather than guessing a brittle internal
 * MapElementRenderHandler descriptor from another Minecraft point release.
 * The Flight Computer's native host uses the real GuiMap instance created by Xaero.
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class XaeroGuiMapMixin {
    @Unique
    private void flightComputer$capture() {
        XaeroMapHost.captureNativeScreen((Screen) (Object) this);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void flightComputer$captureConstructor(CallbackInfo callback) {
        flightComputer$capture();
    }

    @Inject(method = {"render", "method_25394"}, at = @At("TAIL"), remap = false, require = 0)
    private void flightComputer$renderBridge(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo callback) {
        BridgeRenderer.renderTail((Screen) (Object) this, graphics, ((Screen) (Object) this).width,
                ((Screen) (Object) this).height);
    }
}
