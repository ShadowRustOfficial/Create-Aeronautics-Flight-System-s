package com.flightcomputer.client;

import com.flightcomputer.client.gui.CoolingConsoleScreen;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.network.FlightComputerUiSoundNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/**
 * Maps Flight Computer UI controls to server-authoritative block audio.
 * No vanilla widget sound and no LocalPlayer.playSound path is used here.
 */
public final class AudioUiSoundBridge {
    private AudioUiSoundBridge() {}

    public enum Kind { TOGGLE_ON, TOGGLE_OFF, TAB, INTERACT, DISCOVER }

    public static boolean isFlightComputerScreen(Screen screen) {
        return screen instanceof NavigationConsoleScreen
                || screen instanceof ThermalConsoleScreen
                || screen instanceof CoolingConsoleScreen;
    }

    /** Sends a request for the controller block to play the sound using SoundSource.BLOCKS. */
    public static void play(Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BlockPos pos = controllerPos(mc.screen);
        if (pos != null) FlightComputerUiSoundNetwork.request(pos, soundId(kind));
    }

    /** Determines the correct supplied UI sound for a button and sends it to the controller block. */
    public static void playForButton(Button button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == null || mc == null || !isFlightComputerScreen(mc.screen)) return;

        String text = button.getMessage().getString().trim().toUpperCase(java.util.Locale.ROOT);
        BlockPos pos = controllerPos(mc.screen);
        if (pos == null) return;

        if (text.equals("MAP") || text.equals("ROUTE") || text.equals("FLIGHT CONTROL")
                || text.equals("DIAGNOSTICS") || text.equals("THERMAL") || text.equals("COOLING")
                || text.equals("NAVIGATION")) {
            FlightComputerUiSoundNetwork.request(pos, soundId(Kind.TAB));
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
            FlightComputerUiSoundNetwork.request(pos, soundId(currentlyOn ? Kind.TOGGLE_OFF : Kind.TOGGLE_ON));
            return;
        }

        FlightComputerUiSoundNetwork.request(pos, soundId(Kind.INTERACT));
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
