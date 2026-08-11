package com.flightcomputer.client.gui;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Dedicated thermal tab overlay for the existing Navigation Console. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class ThermalConsoleTab {
    private static final Map<NavigationConsoleScreen, State> STATES = new IdentityHashMap<>();
    private ThermalConsoleTab() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        int left = Math.max(10, (screen.width - 640) / 2);
        State state = new State();
        STATES.put(screen, state);
        Button tab = Button.builder(Component.literal("THERMAL"), b -> { state.open = !state.open; updateVisibility(state); })
                .bounds(left + 480, 46, 150, 20).build();
        event.addListener(tab); state.tabButton = tab;
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            Button slotButton = Button.builder(Component.literal("SLOT " + (i + 1)), b -> clickSlot(screen, slot, b))
                    .bounds(left + 40 + i * 185, 285, 165, 20).build();
            slotButton.visible = false;
            event.addListener(slotButton); state.slotButtons[i] = slotButton;
        }
    }

    private static void updateVisibility(State state) { for (Button button : state.slotButtons) if (button != null) button.visible = state.open; }

    private static void clickSlot(NavigationConsoleScreen screen, int slot, Button button) {
        FlightControllerBlockEntity controller = getController(screen); if (controller == null) return;
        ItemStack existing = controller.getUpgradeHandler().getStackInSlot(slot);
        FlightComputerNetwork.sendCoolingSlot(screen.controllerPos(), slot, existing.isEmpty() ? 0 : 1);
        button.setMessage(Component.literal(existing.isEmpty() ? "INSERTING..." : "REMOVING..."));
    }

    /** Draw before the screen so the normal widgets remain visible above the thermal panel. */
    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        State state = STATES.get(screen); if (state == null || !state.open) return;
        Minecraft mc = Minecraft.getInstance(); FlightControllerBlockEntity controller = getController(screen); if (controller == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int left = Math.max(10, (screen.width - 640) / 2), top = 70, right = left + 640, bottom = Math.min(screen.height - 12, top + 285);
        var telemetry = FlightComputerTelemetryClient.get(controller.getControllerId());
        g.fill(left, top, right, bottom, 0xF00B1116);
        g.fill(left, top, right, top + 2, thermalColor(controller.getThermalState().ordinal()));
        g.drawString(mc.font, "THERMAL MANAGEMENT", left + 20, top + 12, 0xFFE6EEF2);
        g.drawString(mc.font, "LIVE THERMAL CONTROL / PROTECTION", left + 20, top + 30, 0xFF9DAEB5);
        double temperature = telemetry == null ? controller.getTemperature() : telemetry.temperature();
        double maximum = telemetry == null ? controller.getMaxTemperature() : telemetry.maxTemperature();
        int thermalState = telemetry == null ? controller.getThermalState().ordinal() : telemetry.thermalState();
        double fraction = maximum <= 0 ? 0 : Math.max(0, Math.min(1, temperature / maximum));
        int color = thermalColor(thermalState);
        g.drawString(mc.font, String.format("TEMPERATURE  %.1f C / %.1f C", temperature, maximum), left + 20, top + 55, color);
        int barL = left + 20, barT = top + 72, barR = right - 20, barB = top + 86;
        g.fill(barL, barT, barR, barB, 0xFF252B30); g.fill(barL, barT, barL + (int)((barR - barL) * fraction), barB, color);
        g.drawString(mc.font, "LOAD " + Math.round(fraction * 100) + "%   STATE " + thermalName(thermalState), left + 20, top + 95, color);
        g.drawString(mc.font, "COOLING TIER: " + (telemetry == null ? controller.getCoolingTier().name() : telemetry.coolingTier()), left + 20, top + 114, 0xFF66D9FF);
        int cooldown = telemetry == null ? controller.getThermalCooldownTicksRemaining() : telemetry.cooldownTicks();
        g.drawString(mc.font, cooldown > 0 ? String.format("THERMAL LOCKOUT: %.1f s", cooldown / 20.0D) : "THERMAL LOCKOUT: READY", left + 20, top + 133, cooldown > 0 ? 0xFFFF5555 : 0xFF55FF55);
        g.drawString(mc.font, "POWER: " + String.format("%,d", controller.getEnergyStorage().getEnergyStored()) + " / " + String.format("%,d", controller.getEnergyStorage().getMaxEnergyStored()) + " FE", left + 20, top + 152, 0xFFBFD0D8);
        g.drawString(mc.font, "COOLING UPGRADES", left + 20, top + 177, 0xFFE6EEF2);
        for (int i = 0; i < 3; i++) {
            ItemStack stack = controller.getUpgradeHandler().getStackInSlot(i); int x = left + 58 + i * 185;
            g.fill(x - 4, top + 197, x + 26, top + 227, 0xFF20282D);
            if (!stack.isEmpty()) { g.renderItem(stack, x, top + 201); g.renderItemDecorations(mc.font, stack, x, top + 201); }
            g.drawString(mc.font, "BAY " + (i + 1), x + 35, top + 204, 0xFF9DAEB5);
            g.drawString(mc.font, stack.isEmpty() ? "EMPTY" : stack.getHoverName().getString(), x + 35, top + 218, stack.isEmpty() ? 0xFF6F7C82 : 0xFFE6EEF2);
        }
        g.drawString(mc.font, "CLICK A BAY: INSERT COOLING UPGRADE / REMOVE INSTALLED UPGRADE", left + 20, bottom - 22, 0xFF9DAEB5);
    }

    private static FlightControllerBlockEntity getController(NavigationConsoleScreen screen) { Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return null; return mc.level.getBlockEntity(screen.controllerPos()) instanceof FlightControllerBlockEntity fc ? fc : null; }
    private static int thermalColor(int state) { return switch (state) { case 0 -> 0xFF55FF55; case 1 -> 0xFFFFFF55; case 2 -> 0xFFFFAA33; case 3 -> 0xFFFF5555; default -> 0xFFFF2222; }; }
    private static String thermalName(int state) { return switch (state) { case 0 -> "NORMAL"; case 1 -> "WARM"; case 2 -> "HOT"; case 3 -> "CRITICAL"; default -> "THERMAL SHUTDOWN"; }; }
    private static final class State { boolean open; Button tabButton; final Button[] slotButtons = new Button[3]; }
}
