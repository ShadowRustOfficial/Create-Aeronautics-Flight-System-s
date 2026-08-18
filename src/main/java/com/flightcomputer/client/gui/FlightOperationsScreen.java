package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.CombatMode;
import com.flightcomputer.avionics.DockingState;
import com.flightcomputer.avionics.FlightControlProfile;
import com.flightcomputer.avionics.FlightHold;
import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.avionics.FlightOperationsState;
import com.flightcomputer.avionics.LandingMode;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightOperationsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Isolated Phase 5.2 operations console. Each tab owns only its own widgets and render content.
 */
public final class FlightOperationsScreen extends Screen {
    private enum Tab { IDENTITY, COMBAT, LANDING, DOCKING, SYSTEM }

    private static final int PANEL = 0xF20E1318;
    private static final int CYAN = 0xFF66D9FF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF3333;
    private static final int TEXT = 0xFFE6EEF2;
    private static final int MUTED = 0xFF9DAEB5;

    private final BlockPos controllerPos;
    private Tab tab = Tab.IDENTITY;
    private EditBox shipName;
    private EditBox callsign;
    private EditBox home;
    private EditBox target;
    private FlightControllerBlockEntity controller;

    public FlightOperationsScreen(BlockPos controllerPos) {
        super(Component.literal("Aero Flight Operations"));
        this.controllerPos = controllerPos;
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override protected void init() {
        controller = getController();
        int left = Math.max(10, (width - 760) / 2);
        int top = 24;
        int w = 132;
        addRenderableWidget(Button.builder(Component.literal("IDENTITY"), b -> switchTab(Tab.IDENTITY)).bounds(left, top, w, 22).build());
        addRenderableWidget(Button.builder(Component.literal("COMBAT"), b -> switchTab(Tab.COMBAT)).bounds(left + 138, top, w, 22).build());
        addRenderableWidget(Button.builder(Component.literal("LANDING"), b -> switchTab(Tab.LANDING)).bounds(left + 276, top, w, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DOCKING"), b -> switchTab(Tab.DOCKING)).bounds(left + 414, top, w, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SYSTEM"), b -> switchTab(Tab.SYSTEM)).bounds(left + 552, top, w, 22).build());

        switch (tab) {
            case IDENTITY -> initIdentity(left, top);
            case COMBAT -> initCombat(left, top);
            case LANDING -> initLanding(left, top);
            case DOCKING -> initDocking(left, top);
            case SYSTEM -> initSystem(left, top);
        }
    }

    private void initIdentity(int left, int top) {
        FlightOperationsState s = operations();
        shipName = new EditBox(font, left + 32, top + 86, 430, 20, Component.literal("Ship Name"));
        shipName.setValue(s.shipName());
        addRenderableWidget(shipName);
        addRenderableWidget(Button.builder(Component.literal("SET NAME"), b -> FlightOperationsNetwork.sendIdentity(controllerPos, false, shipName.getValue())).bounds(left + 472, top + 86, 120, 20).build());
        callsign = new EditBox(font, left + 32, top + 136, 430, 20, Component.literal("Callsign"));
        callsign.setValue(s.callsign());
        addRenderableWidget(callsign);
        addRenderableWidget(Button.builder(Component.literal("SET CALLSIGN"), b -> FlightOperationsNetwork.sendIdentity(controllerPos, true, callsign.getValue())).bounds(left + 472, top + 136, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("MAP CONTACT: " + (s.mapContactVisible() ? "ON" : "OFF")), b -> {
            FlightOperationsNetwork.setMapVisibility(controllerPos, !operations().mapContactVisible());
            b.setMessage(Component.literal("MAP CONTACT: " + (!operations().mapContactVisible() ? "ON" : "OFF")));
        }).bounds(left + 32, top + 190, 180, 20).build());
    }

    private void initCombat(int left, int top) {
        FlightOperationsState s = operations();
        addRenderableWidget(Button.builder(Component.literal("DEFENSIVE"), b -> FlightOperationsNetwork.sendCombatMode(controllerPos, CombatMode.DEFENSIVE)).bounds(left + 32, top + 70, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("OFFENSIVE"), b -> FlightOperationsNetwork.sendCombatMode(controllerPos, CombatMode.OFFENSIVE)).bounds(left + 220, top + 70, 180, 20).build());
        home = new EditBox(font, left + 32, top + 116, 420, 20, Component.literal("Home / Escape location"));
        home.setValue(s.defensiveHome());
        home.setHint(Component.literal("coordinates or named home"));
        addRenderableWidget(home);
        addRenderableWidget(Button.builder(Component.literal("SET HOME"), b -> FlightOperationsNetwork.sendCombatHome(controllerPos, home.getValue())).bounds(left + 462, top + 116, 120, 20).build());
        target = new EditBox(font, left + 32, top + 162, 420, 20, Component.literal("Target callsign"));
        target.setValue(s.offensiveCallsign());
        target.setHint(Component.literal("target callsign"));
        addRenderableWidget(target);
        addRenderableWidget(Button.builder(Component.literal("SET TARGET"), b -> FlightOperationsNetwork.sendCombatTarget(controllerPos, target.getValue())).bounds(left + 462, top + 162, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ENGAGE COMBAT ASSIST"), b -> FlightOperationsNetwork.engageCombat(controllerPos)).bounds(left + 32, top + 208, 230, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT"), b -> FlightOperationsNetwork.abortCombat(controllerPos)).bounds(left + 272, top + 208, 120, 20).build());
    }

    private void initLanding(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("SCAN / LANDING ASSIST"), b -> FlightOperationsNetwork.sendLandingMode(controllerPos, LandingMode.SCAN_ONLY)).bounds(left + 32, top + 76, 230, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SAFE LANDING"), b -> FlightOperationsNetwork.sendLandingMode(controllerPos, LandingMode.SAFE_LANDING)).bounds(left + 272, top + 76, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ENGAGE LANDING"), b -> FlightOperationsNetwork.engageLanding(controllerPos)).bounds(left + 32, top + 122, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT LANDING"), b -> FlightOperationsNetwork.abortLanding(controllerPos)).bounds(left + 222, top + 122, 180, 20).build());
    }

    private void initDocking(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("SCAN + AUTO-DOCK"), b -> FlightOperationsNetwork.engageDocking(controllerPos)).bounds(left + 32, top + 82, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLEAR DOCKING"), b -> FlightOperationsNetwork.clearDocking(controllerPos)).bounds(left + 262, top + 82, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("RED OVERRIDE"), b -> FlightOperationsNetwork.overrideDocking(controllerPos)).bounds(left + 32, top + 128, 220, 24).build());
    }

    private void initSystem(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("NORMAL"), b -> FlightOperationsNetwork.setProfile(controllerPos, FlightControlProfile.NORMAL)).bounds(left + 32, top + 74, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("COMBAT PROFILE"), b -> FlightOperationsNetwork.setProfile(controllerPos, FlightControlProfile.COMBAT)).bounds(left + 180, top + 74, 170, 20).build());
        addRenderableWidget(Button.builder(Component.literal("LANDING PROFILE"), b -> FlightOperationsNetwork.setProfile(controllerPos, FlightControlProfile.LANDING)).bounds(left + 358, top + 74, 180, 20).build());
        int y = top + 120;
        for (FlightHold hold : FlightHold.values()) {
            addRenderableWidget(Button.builder(Component.literal(hold.name() + " HOLD"), b -> {
                boolean enabled = !operations().hasHold(hold);
                FlightOperationsNetwork.setHold(controllerPos, hold, enabled);
                b.setMessage(Component.literal(hold.name() + " HOLD: " + (enabled ? "ON" : "OFF")));
            }).bounds(left + 32 + (hold.ordinal() % 2) * 220, y + (hold.ordinal() / 2) * 28, 200, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("EMERGENCY RETURN"), b -> FlightOperationsNetwork.setEmergencyReturn(controllerPos, true)).bounds(left + 32, top + 190, 200, 20).build());
    }

    private FlightOperationsState operations() {
        if (controller == null) controller = getController();
        return controller instanceof FlightOperationsHolder holder ? holder.getFlightOperations() : new FlightOperationsState();
    }

    private FlightControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) return null;
        BlockEntity be = minecraft.level.getBlockEntity(controllerPos);
        return be instanceof FlightControllerBlockEntity fc ? fc : null;
    }

    private void switchTab(Tab next) {
        tab = next;
        clearWidgets();
        init();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(10, (width - 760) / 2), top = 24, right = left + 684;
        g.fill(left - 10, top - 10, right + 40, Math.min(height - 10, top + 290), PANEL);
        g.drawString(font, "AERO FLIGHT OPERATIONS", left, top - 1, TEXT);
        g.drawString(font, "IDENTITY / COMBAT / LANDING / DOCKING / SYSTEM", left + 230, top - 1, MUTED);
        int activeX = switch (tab) {
            case IDENTITY -> left; case COMBAT -> left + 138; case LANDING -> left + 276; case DOCKING -> left + 414; case SYSTEM -> left + 552;
        };
        g.fill(activeX, top + 20, activeX + 132, top + 22, CYAN);
        g.drawString(font, title(), left + 32, top + 48, CYAN);
        drawStatus(g, left, top);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String title() {
        return switch (tab) {
            case IDENTITY -> "SHIP IDENTITY";
            case COMBAT -> "COMBAT FLIGHT PROFILE";
            case LANDING -> "LANDING ASSIST / SCAN";
            case DOCKING -> "AUTO-DOCKING";
            case SYSTEM -> "SYSTEM / EMERGENCY";
        };
    }

    private void drawStatus(GuiGraphics g, int left, int top) {
        FlightOperationsState s = operations();
        String status = "PROFILE: " + s.profile() + "  |  COMBAT: " + s.combatMode() + "  |  DOCK: " + s.dockingState();
        g.drawString(font, status, left + 32, top + 258, s.dockingOverride() ? RED : MUTED);
        if (s.dockingOverride()) g.drawString(font, "DOCKING OVERRIDE ACTIVE — PILOT CONTROL", left + 32, top + 278, RED);
        if (s.preflightPassed()) g.drawString(font, "PREFLIGHT: PASS", left + 520, top + 258, GREEN);
    }

    @Override public boolean isPauseScreen() { return false; }
}
