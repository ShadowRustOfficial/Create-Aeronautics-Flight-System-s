package com.flightcomputer.mixin.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keeps the identity controls readable: name and ID occupy separate full-width rows. */
@Mixin(targets = "com.flightcomputer.client.gui.NavigationConsoleScreen")
public abstract class NavigationConsoleDiagnosticsLayoutMixin {
    @Shadow private EditBox nameInput;
    @Shadow private EditBox flightIdInput;
    @Shadow private int width;
    @Shadow public abstract List<? extends GuiEventListener> children();

    @Inject(method = "init", at = @At("TAIL"))
    private void flightcomputer$layoutIdentityFields(CallbackInfo ci) {
        if (nameInput == null || flightIdInput == null) return;

        int panel = Math.min(Math.max(760, width - 32), 1240);
        int left = (width - panel) / 2 + 18;
        int contentWidth = panel - 36;
        int fieldWidth = Math.max(240, contentWidth - 110);
        int y = 18 + 220;

        nameInput.setPosition(left, y);
        nameInput.setWidth(fieldWidth);
        flightIdInput.setPosition(left, y + 28);
        flightIdInput.setWidth(fieldWidth);

        for (GuiEventListener listener : children()) {
            if (!(listener instanceof Button button)) continue;
            String text = button.getMessage().getString().trim();
            if (text.equals("SET NAME")) {
                button.setPosition(left + fieldWidth + 8, y);
                button.setWidth(102);
            } else if (text.equals("SET ID")) {
                button.setPosition(left + fieldWidth + 8, y + 28);
                button.setWidth(102);
            }
        }
    }
}
