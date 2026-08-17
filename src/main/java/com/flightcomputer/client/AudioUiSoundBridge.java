package com.flightcomputer.client;

import com.flightcomputer.client.gui.CoolingConsoleScreen;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/**
 * Maps Flight Computer UI controls directly onto the same server-authoritative network path
 * used by the controller actions.  The server resolves the controller block and performs
 * Level.playSound(..., SoundSource.BLOCKS), exactly like Emergency Shutdown.
 */
public final class AudioUiSoundBridge {
    private AudioUiSoundBridge() {}

    public enum Kind { TOGGLE_ON, TOGGLE_OFF, TAB, INTERACT, DISCOVER }

    public static boolean isFlightComputerScreen(Screen screen) {
        return screen instanceof NavigationConsoleScreen
                || screen instanceof ThermalConsoleScreen
                || screen instanceof CoolingConsoleScreen;
    }

    public static void play(Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BlockPos pos = controllerPos(mc.screen);
        if (pos != null) FlightComputerNetwork.sendUiButtonSound(pos, soundId(kind));
    }

    /**
     * Called from Button.onPress before the button callback executes.  Vanilla button audio is
     * muted separately; this is the only UI audio trigger.  No client-side sound is played here.
     */
    public static void playForButton(Button button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == null || mc == null || !isFlightComputerScreen(mc.screen)) return;

        BlockPos pos = controllerPos(mc.screen);
        if (pos == null) return;

        String text = button.getMessage().getString().trim().toUpperCase(java.util.Locale.ROOT);

        if (text.equals("MAP") || text.equals("ROUTE") || text.equals("FLIGHT CONTROL")
                || text.equals("DIAGNOSTICS") || text.equals("THERMAL") || text.equals("COOLING")
                || text.equals("NAVIGATION")) {
            FlightComputerNetwork.sendUiButtonSound(pos, soundId(Kind.TAB));
            return;
        }

        boolean toggle = text.contains("SYSTEM:")
                || text.contains("STABILISER:")
                || text.contains("AUTOPILOT:")
                || text.contains("ALTITUDE HOLD:")
                || text.contains("HEADING HOLD:")
                || text.contains("POSITION HOLD:")
                || text.contains("VELOCITY HOLD:")
                || text.contains("NAVIGATION:")
                || text.startsWith("TERRAIN:")
                || text.startsWith("FLIGHT MAP:")
                || text.startsWith("WAYPOINTS:");

        if (toggle) {
            boolean currentlyOn = text.contains(": ON") || text.contains(": ENGAGED");
            FlightComputerNetwork.sendUiButtonSound(pos,
                    soundId(currentlyOn ? Kind.TOGGLE_OFF : Kind.TOGGLE_ON));
            return;
        }

        FlightComputerNetwork.sendUiButtonSound(pos, soundId(Kind.INTERACT));
    }

    private static int soundId(Kind kind) {
        return switch (kind) {
            case TOGGLE_ON -> 0;
            case TOGGLE_OFF -> 1;
            case TAB -> 2;
            case INTERACT -> 3;
            case DISCOVER -> 4;
        };
    }

    private static BlockPos controllerPos(Screen screen) {
        if (screen instanceof NavigationConsoleScreen navigation) return navigation.controllerPos();
        if (screen instanceof ThermalConsoleScreen thermal) return thermal.controllerPos();
        if (screen instanceof CoolingConsoleScreen cooling) return cooling.controllerPos();
        return null;
    }
}
