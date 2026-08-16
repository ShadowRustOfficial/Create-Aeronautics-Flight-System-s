package com.flightcomputer.mixin.client;

import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keeps Diagnostics identity fields and Flight Control target/push controls in non-overlapping rows. */
@Mixin(NavigationConsoleScreen.class)
public abstract class NavigationConsoleLayoutMixin {
    @Shadow private EditBox targetPlayerInput;
    @Shadow private EditBox altitudeTargetInput;
    @Shadow private EditBox homeInput;
    @Shadow private EditBox nameInput;
    @Shadow private EditBox flightIdInput;
    @Shadow private Button targetPlayerButton;
    @Shadow private Button targetHomeButton;
    @Shadow private Button setAltitudeButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void flightcomputer$reflowControls(CallbackInfo ci) {
        NavigationConsoleScreen screen = (NavigationConsoleScreen)(Object)this;
        int panel = Math.min(Math.max(760, screen.width - 32), 1240);
        int left = (screen.width - panel) / 2 + 18;
        int width = panel - 36;
        int top = 18;
        int half = (width - 8) / 2;

        // Diagnostics: reserve the lower section for identity instead of placing fields over live telemetry.
        if (nameInput != null && flightIdInput != null) {
            int y = Math.min(screen.height - 72, top + 380);
            int fieldWidth = Math.max(180, half - 120);
            int right = left + half + 8;
            nameInput.setPosition(left, y);
            nameInput.setWidth(fieldWidth);
            flightIdInput.setPosition(right, y);
            flightIdInput.setWidth(fieldWidth);
            repositionButton(screen.children(), "SET NAME", left + fieldWidth + 8, y, 104, 20);
            repositionButton(screen.children(), "SET ID", right + fieldWidth + 8, y, 104, 20);
        }

        // Flight Control: left column is manual push + altitude target; right column is target selection.
        if (targetPlayerInput != null && homeInput != null && altitudeTargetInput != null) {
            int pushY = top + 148;
            int pushWidth = Math.max(62, (half - 30) / 6);
            String[] pushNames = {"F", "B", "U", "D", "L", "R"};
            for (int i = 0; i < pushNames.length; i++) {
                repositionButton(screen.children(), pushNames[i], left + i * (pushWidth + 5), pushY, pushWidth, 24);
            }

            int targetLeft = left + half + 8;
            int targetWidth = width - half - 8;
            int modeWidth = Math.max(90, (targetWidth - 4) / 2);
            if (targetPlayerButton != null) targetPlayerButton.setPosition(targetLeft, pushY);
            if (targetPlayerButton != null) targetPlayerButton.setWidth(modeWidth);
            if (targetHomeButton != null) {
                targetHomeButton.setPosition(targetLeft + modeWidth + 4, pushY);
                targetHomeButton.setWidth(modeWidth);
            }

            targetPlayerInput.setPosition(targetLeft, pushY + 30);
            targetPlayerInput.setWidth(Math.max(150, targetWidth - 118));
            repositionButton(screen.children(), "SET TARGET", targetLeft + targetWidth - 110, pushY + 30, 110, 20);

            homeInput.setPosition(targetLeft, pushY + 56);
            homeInput.setWidth(Math.max(150, targetWidth - 110));
            repositionButton(screen.children(), "SET HOME", targetLeft + targetWidth - 110, pushY + 56, 110, 20);

            int altitudeY = pushY + 56;
            int altitudeWidth = Math.max(180, half - 118);
            altitudeTargetInput.setPosition(left, altitudeY);
            altitudeTargetInput.setWidth(altitudeWidth);
            if (setAltitudeButton != null) {
                setAltitudeButton.setPosition(left + altitudeWidth + 8, altitudeY);
                setAltitudeButton.setWidth(Math.max(170, half - altitudeWidth - 8));
            }
        }
    }

    private static void repositionButton(List<? extends GuiEventListener> children, String text, int x, int y, int width, int height) {
        for (GuiEventListener listener : children) {
            if (!(listener instanceof Button button)) continue;
            if (!button.getMessage().getString().trim().equals(text)) continue;
            button.setPosition(x, y);
            button.setWidth(width);
            button.setHeight(height);
        }
    }
}
