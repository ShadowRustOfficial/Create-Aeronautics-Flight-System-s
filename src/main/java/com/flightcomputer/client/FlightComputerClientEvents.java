package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class FlightComputerClientEvents {
    private FlightComputerClientEvents() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        // Ambient flight audio remains client-side. Controller UI and warning sounds do not:
        // those are emitted by the server from the Flight Controller block.
        FlightComputerSoundClient.tick();
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof NavigationConsoleScreen console)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        var be = minecraft.level.getBlockEntity(console.controllerPos());
        if (!(be instanceof FlightControllerBlockEntity controller)) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = 150;
        int x = Math.max(4, screenWidth - width - 8);
        int y = Math.max(4, screenHeight - 28);
        Button mute = Button.builder(Component.literal(label(controller)), button -> {
            boolean muted = FlightComputerSoundClient.toggleMuted(controller.getControllerId());
            button.setMessage(Component.literal(muted ? "STABILISER AMBIENT: MUTED" : "STABILISER AMBIENT: ON"));
        }).bounds(x, y, width, 20).build();
        event.addListener(mute);
    }

    private static String label(FlightControllerBlockEntity controller) {
        return FlightComputerSoundClient.isMuted(controller.getControllerId())
                ? "STABILISER AMBIENT: MUTED"
                : "STABILISER AMBIENT: ON";
    }
}
