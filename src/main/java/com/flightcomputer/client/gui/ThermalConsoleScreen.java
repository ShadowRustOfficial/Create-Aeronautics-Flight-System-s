package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.ThermalState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Dedicated thermal-management tab. Cooling inventory is intentionally kept on its own tab. */
public final class ThermalConsoleScreen extends Screen {
    private static final int PANEL = 0xE610141A;
    private static final int TEXT = 0xFFE6EEF2;
    private static final int MUTED = 0xFF9DAEB5;
    private static final int CYAN = 0xFF66D9FF;
    private static final int GREEN = 0xFF55FF55;
    private static final int AMBER = 0xFFFFCC55;
    private static final int RED = 0xFFFF5555;

    private final BlockPos controllerPos;
    private FlightControllerBlockEntity controller;
    private int left;
    private int top;

    public ThermalConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Flight Computer - Thermal"));
        this.controllerPos = controllerPos;
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override
    protected void init() {
        controller = getController();
        left = Math.max(10, (width - 640) / 2);
        top = Math.max(20, (height - 340) / 2);
        addRenderableWidget(Button.builder(Component.literal("NAVIGATION"), b -> minecraft.setScreen(new NavigationConsoleScreen(controllerPos)))
                .bounds(left, top, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("THERMAL"), b -> {})
                .bounds(left + 150, top, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("COOLING"), b -> minecraft.setScreen(new CoolingConsoleScreen(controllerPos)))
                .bounds(left + 300, top, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(left + 490, top, 145, 22).build());
    }

    private FlightControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) return null;
        BlockEntity be = minecraft.level.getBlockEntity(controllerPos);
        return be instanceof FlightControllerBlockEntity fc ? fc : null;
    }

    @Override
    public void tick() {
        super.tick();
        controller = getController();
        if (controller == null) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int right = left + 640;
        int bottom = top + 340;
        g.fill(left - 8, top - 8, right + 8, bottom + 8, PANEL);
        g.drawString(font, "FLIGHT COMPUTER / THERMAL MANAGEMENT", left, top + 34, TEXT);
        g.drawString(font, "THERMAL CONTROL / PROTECTION", left, top + 58, MUTED);

        double temperature = controller == null ? 0.0D : controller.getTemperature();
        double maximum = controller == null ? 1.0D : Math.max(1.0D, controller.getMaxTemperature());
        double ratio = Math.max(0.0D, Math.min(1.0D, temperature / maximum));
        ThermalState state = controller == null ? ThermalState.NORMAL : controller.getThermalState();
        int stateColor = state == ThermalState.THERMAL_SHUTDOWN || state == ThermalState.CRITICAL ? RED : state == ThermalState.HOT ? AMBER : GREEN;

        g.drawString(font, "THERMAL STATUS", left + 20, top + 88, TEXT);
        g.drawString(font, state.name().replace('_', ' '), left + 250, top + 88, stateColor);
        g.drawString(font, String.format("TEMPERATURE  %.1f / %.1f", temperature, maximum), left + 20, top + 116, TEXT);
        g.fill(left + 20, top + 134, left + 620, top + 152, 0xFF20272C);
        int bar = (int)(600 * ratio);
        g.fill(left + 20, top + 134, left + 20 + bar, top + 152, stateColor);
        g.drawString(font, String.format("%.1f%%", ratio * 100.0D), left + 285, top + 138, TEXT);

        int cooldown = controller == null ? 0 : controller.getThermalCooldownTicksRemaining();
        g.drawString(font, cooldown > 0 ? String.format("THERMAL LOCKOUT: %.1f s", cooldown / 20.0D) : "THERMAL LOCKOUT: READY", left + 20, top + 176, cooldown > 0 ? RED : GREEN);
        long energy = controller == null ? 0L : controller.getEnergyStorage().getEnergyStored();
        long capacity = controller == null ? 1L : controller.getEnergyStorage().getMaxEnergyStored();
        g.drawString(font, String.format("POWER: %,d / %,d FE", energy, capacity), left + 20, top + 202, TEXT);

        var snapshot = controller == null ? null : FlightComputerTelemetryClient.get(controller.getControllerId());
        if (snapshot != null) {
            g.drawString(font, "LIVE TELEMETRY: " + thermalName(snapshot.thermalState()) + " | COOLING TIER " + snapshot.coolingTier(), left + 20, top + 234, CYAN);
        }
        g.drawString(font, "Cooling modules are managed separately in the COOLING tab.", left + 20, top + 270, MUTED);
        g.drawString(font, "Thermal shutdown prevents propulsion/control output until recovery.", left + 20, top + 294, MUTED);

        super.render(g, mouseX, mouseY, partialTick);
        g.fill(left + 150, top + 20, left + 295, top + 22, CYAN);
    }

    private static String thermalName(int id) {
        return switch (id) {
            case 1 -> "WARM";
            case 2 -> "HOT";
            case 3 -> "CRITICAL";
            case 4 -> "THERMAL SHUTDOWN";
            default -> "NORMAL";
        };
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    @Override public boolean isPauseScreen() { return false; }
}
