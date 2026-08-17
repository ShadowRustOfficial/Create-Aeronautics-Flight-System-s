package com.flightcomputer.mixin.client;

import com.flightcomputer.client.map.FlightContactRegistry;
import com.flightcomputer.map.FlightContact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/** Adds a live powered-controller selector and controller labels without changing Navigation Console control paths. */
@Mixin(com.flightcomputer.client.gui.NavigationConsoleScreen.class)
public abstract class NavigationConsoleFlightControllerContactsMixin extends Screen {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private Font font;
    @Shadow private double centerX;
    @Shadow private double centerZ;

    @Unique private Button flightComputer$controllerSelector;
    @Unique private UUID flightComputer$selectedController;
    @Unique private int flightComputer$selectionIndex;

    protected NavigationConsoleFlightControllerContactsMixin() {
        super(Component.literal("Navigation Console"));
    }

    @Inject(method = "initMap", at = @At("TAIL"))
    private void flightComputer$initControllerSelector(int left, int width, CallbackInfo ci) {
        int y = top() + 374;
        int buttonWidth = Math.min(260, Math.max(190, width - 8));
        flightComputer$controllerSelector = Button.builder(
                Component.literal("CONTROLLER: (None) ▼"),
                b -> flightComputer$cycleController())
                .bounds(left, y, buttonWidth, 20)
                .build();
        addRenderableWidget(flightComputer$controllerSelector);
        flightComputer$refreshControllerSelector();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void flightComputer$refreshControllerSelectorTick(CallbackInfo ci) {
        flightComputer$refreshControllerSelector();
    }

    @Inject(method = "renderMap", at = @At("TAIL"))
    private void flightComputer$renderControllerContacts(GuiGraphics graphics, int left, int mapTop, CallbackInfo ci) {
        if (minecraft == null || minecraft.level == null) return;

        int mapLeft = left;
        int mapRight = left + Math.max(1, panelWidth() - 36);
        int mapTopPx = mapTop + 8;
        int mapBottom = mapTop + 330;
        int mapWidth = Math.max(1, mapRight - mapLeft);
        int mapHeight = Math.max(1, mapBottom - mapTopPx);

        List<FlightContact> contacts = FlightContactRegistry.active(minecraft.level.getGameTime());
        for (FlightContact contact : contacts) {
            int x = (int) Math.round(mapLeft + mapWidth / 2.0D + (contact.x() - centerX));
            int y = (int) Math.round(mapTopPx + mapHeight / 2.0D + (contact.z() - centerZ));
            if (x < mapLeft || x >= mapRight || y < mapTopPx || y >= mapBottom) continue;

            int markerColor = contact.controllerId().equals(flightComputer$selectedController)
                    ? 0xFF55FF55 : 0xFFFFAA55;
            graphics.fill(x - 4, y, x + 5, y + 1, markerColor);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, markerColor);
            graphics.fill(x - 1, y - 3, x + 2, y + 4, markerColor);
            graphics.drawString(font, contact.displayId(), x + 7, y - 5, markerColor);
        }
    }

    @Unique
    private void flightComputer$refreshControllerSelector() {
        if (flightComputer$controllerSelector == null || minecraft == null || minecraft.level == null) return;
        List<FlightContact> contacts = FlightContactRegistry.active(minecraft.level.getGameTime());
        if (contacts.isEmpty()) {
            flightComputer$selectedController = null;
            flightComputer$selectionIndex = 0;
            flightComputer$controllerSelector.setMessage(Component.literal("CONTROLLER: (None) ▼"));
            return;
        }

        if (flightComputer$selectedController != null) {
            for (int i = 0; i < contacts.size(); i++) {
                if (contacts.get(i).controllerId().equals(flightComputer$selectedController)) {
                    flightComputer$selectionIndex = i;
                    flightComputer$controllerSelector.setMessage(Component.literal(
                            "CONTROLLER: " + contacts.get(i).displayId() + " ▼"));
                    return;
                }
            }
        }

        flightComputer$selectionIndex = Math.floorMod(flightComputer$selectionIndex, contacts.size());
        FlightContact contact = contacts.get(flightComputer$selectionIndex);
        flightComputer$selectedController = contact.controllerId();
        flightComputer$controllerSelector.setMessage(Component.literal(
                "CONTROLLER: " + contact.displayId() + " ▼"));
    }

    @Unique
    private void flightComputer$cycleController() {
        if (minecraft == null || minecraft.level == null) return;
        List<FlightContact> contacts = FlightContactRegistry.active(minecraft.level.getGameTime());
        if (contacts.isEmpty()) {
            flightComputer$selectedController = null;
            flightComputer$selectionIndex = 0;
            flightComputer$refreshControllerSelector();
            return;
        }

        flightComputer$selectionIndex = Math.floorMod(flightComputer$selectionIndex + 1, contacts.size());
        FlightContact selected = contacts.get(flightComputer$selectionIndex);
        flightComputer$selectedController = selected.controllerId();
        centerX = selected.x();
        centerZ = selected.z();
        flightComputer$refreshControllerSelector();
    }

    @Shadow private int top() { throw new IllegalStateException("Mixin shadow"); }
    @Shadow private int panelWidth() { throw new IllegalStateException("Mixin shadow"); }
}
