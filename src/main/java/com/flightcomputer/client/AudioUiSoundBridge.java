package com.flightcomputer.client;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightHold;
import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.gui.CoolingConsoleScreen;
import com.flightcomputer.client.gui.FlightOperationsScreen;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * Routes only generic/non-controller-action UI audio to the existing server-authoritative
 * controller-block sound path. Controller actions themselves are sounded from applyAction.
 */
public final class AudioUiSoundBridge {
    private AudioUiSoundBridge() {}

    public enum Kind { TOGGLE_ON, TOGGLE_OFF, TAB, INTERACT, DISCOVER }

    public static boolean isFlightComputerScreen(Screen screen) {
        return screen instanceof NavigationConsoleScreen
                || screen instanceof ThermalConsoleScreen
                || screen instanceof CoolingConsoleScreen
                || screen instanceof FlightOperationsScreen;
    }

    public static void play(Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BlockPos pos = controllerPos(mc.screen);
        if (pos != null) FlightComputerNetwork.sendUiButtonSound(pos, soundId(kind));
    }

    /**
     * Called from Button.onPress before the button callback executes. Vanilla button audio is
     * muted separately. ControllerAction buttons are deliberately excluded here because their
     * server-side FlightControllerBlockEntity.applyAction() owns their sound.
     */
    public static void playForButton(Button button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == null || mc == null || !isFlightComputerScreen(mc.screen)) return;

        BlockPos pos = controllerPos(mc.screen);
        if (pos == null) return;

        String text = button.getMessage().getString().trim().toUpperCase(Locale.ROOT);

        // Emergency Shutdown has its dedicated controller-block sound and must not also emit
        // the generic UI interaction sound.
        if (text.equals("EMERGENCY SHUTDOWN")) return;

        // Cooling inventory operations have dedicated server-side sounds emitted by the
        // CoolingSlot packet handler, so do not emit UI_INTERACT here as well.
        if (text.equals("INSERT HELD") || text.equals("REMOVE")) return;

        // These buttons already send FlightControllerAction through the existing controller
        // action packet. Their corresponding sound is emitted server-side from applyAction().
        if (isControllerActionButton(text)) return;

        if (isTabButton(text)) {
            FlightComputerNetwork.sendUiButtonSound(pos, soundId(Kind.TAB));
            return;
        }

        FlightControllerBlockEntity controller = controller(mc.screen);
        Kind toggle = toggleKind(mc.screen, controller, text);
        if (toggle != null) {
            FlightComputerNetwork.sendUiButtonSound(pos, soundId(toggle));
            return;
        }

        FlightComputerNetwork.sendUiButtonSound(pos, soundId(Kind.INTERACT));
    }

    private static boolean isControllerActionButton(String text) {
        return text.equals("SYSTEM")
                || text.startsWith("SYSTEM:")
                || text.equals("STABILISER")
                || text.startsWith("STABILISER:")
                || text.equals("MODE")
                || text.startsWith("MODE:")
                || text.equals("AUTOPILOT")
                || text.startsWith("AUTOPILOT:")
                || text.equals("ALTITUDE HOLD")
                || text.startsWith("ALTITUDE HOLD:")
                || text.equals("HEADING HOLD")
                || text.startsWith("HEADING HOLD:")
                || text.equals("POSITION HOLD")
                || text.startsWith("POSITION HOLD:")
                || text.equals("VELOCITY HOLD")
                || text.startsWith("VELOCITY HOLD:")
                || text.equals("NAVIGATION")
                || text.startsWith("NAVIGATION:")
                || text.equals("START ROUTE")
                || text.equals("ABORT ROUTE")
                || text.equals("DISPLAY TEST")
                || text.equals("F")
                || text.equals("B")
                || text.equals("U")
                || text.equals("D")
                || text.equals("L")
                || text.equals("R");
    }

    private static boolean isTabButton(String text) {
        return text.equals("MAP")
                || text.equals("ROUTE")
                || text.equals("FLIGHT CONTROL")
                || text.equals("DIAGNOSTICS")
                || text.equals("THERMAL")
                || text.equals("COOLING")
                || text.equals("NAVIGATION")
                || text.equals("IDENTITY")
                || text.equals("COMBAT")
                || text.equals("LANDING")
                || text.equals("DOCKING")
                || text.equals("SYSTEM");
    }

    private static Kind toggleKind(Screen screen, FlightControllerBlockEntity controller, String text) {
        if (text.startsWith("TERRAIN:")
                || text.startsWith("FLIGHT MAP:")
                || text.startsWith("WAYPOINTS:")
                || text.startsWith("STABILISER AMBIENT:")
                || text.startsWith("MAP CONTACT:")) {
            return text.contains(": ON") || text.contains(": ENGAGED") ? Kind.TOGGLE_OFF : Kind.TOGGLE_ON;
        }

        if (screen instanceof FlightOperationsScreen && text.endsWith(" HOLD") && controller instanceof FlightOperationsHolder holder) {
            String holdName = text.substring(0, text.length() - " HOLD".length()).trim();
            try {
                FlightHold hold = FlightHold.valueOf(holdName);
                return holder.getFlightOperations().hasHold(hold) ? Kind.TOGGLE_OFF : Kind.TOGGLE_ON;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
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
        if (screen instanceof FlightOperationsScreen operations) return operations.controllerPos();
        return null;
    }

    private static FlightControllerBlockEntity controller(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        BlockPos pos = controllerPos(screen);
        if (pos == null) return null;
        return mc.level.getBlockEntity(pos) instanceof FlightControllerBlockEntity fc ? fc : null;
    }
}
