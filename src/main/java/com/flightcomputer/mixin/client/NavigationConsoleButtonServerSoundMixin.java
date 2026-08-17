package com.flightcomputer.mixin.client;

import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends Navigation Console button audio to the server so it is emitted from the Flight Controller block. */
@Mixin(Button.class)
public abstract class NavigationConsoleButtonServerSoundMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void flightComputer$serverBlockSound(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof NavigationConsoleScreen screen) || screen.controllerPos() == null) return;

        Button button = (Button)(Object)this;
        String text = button.getMessage().getString().trim();
        if (text.equals("EMERGENCY SHUTDOWN")) return; // Emergency Shutdown already emits its dedicated two-sound block sequence.

        int soundId = soundId(text);
        FlightComputerNetwork.sendUiButtonSound(screen.controllerPos(), soundId);
    }

    private static int soundId(String text) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains(": OFF") || upper.endsWith(" OFF") || upper.contains("DISENGAGED")) return 0; // toggle on
        if (upper.contains(": ON") || upper.endsWith(" ON") || upper.contains("ENGAGED")) return 1; // toggle off
        if (upper.equals("MAP") || upper.equals("ROUTE") || upper.equals("FLIGHT CONTROL")
                || upper.equals("DIAGNOSTICS") || upper.equals("THERMAL") || upper.equals("COOLING")) return 2; // open/navigate
        if (upper.contains("DISCOVER") || upper.contains("REFRESH") || upper.contains("SELECT")) return 4;
        return 3; // ordinary controller interaction
    }
}
