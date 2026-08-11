package com.flightcomputer.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.VectorDirection;
import com.flightcomputer.item.FlightLinkToolItem;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class KeyInputHandler {
    private static boolean registered = false;
    private static FlightMode linkMode = FlightMode.STABILIZE;
    private static VectorDirection linkDirection = VectorDirection.NORTH;

    public static void register() {
        if (registered) return;
        NeoForge.EVENT_BUS.register(KeyInputHandler.class);
        registered = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (KeyBindings.OPEN_MAP.consumeClick()) {
            if (mc.level == null || mc.player == null) continue;
            if (!(mc.hitResult instanceof BlockHitResult hit)) continue;
            ClientLevel level = mc.level;
            if (!(level.getBlockEntity(hit.getBlockPos()) instanceof FlightControllerBlockEntity controller)) continue;
            if (controller.getEnergyStorage().getEnergyStored() <= 0 || controller.getPowerState() == PowerState.NO_POWER) continue;
            mc.setScreen(new NavigationConsoleScreen(hit.getBlockPos()));
        }
        while (KeyBindings.OPEN_THERMAL.consumeClick()) {
            if (mc.level == null || mc.player == null) continue;
            if (!(mc.hitResult instanceof BlockHitResult hit)) continue;
            if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof FlightControllerBlockEntity controller)) continue;
            if (controller.getEnergyStorage().getEnergyStored() <= 0 || controller.getPowerState() == PowerState.NO_POWER) continue;
            mc.setScreen(new ThermalConsoleScreen(hit.getBlockPos()));
        }

        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (!held.is(ModItems.FLIGHT_LINK_TOOL.get())) return;

        if (KeyBindings.LINK_MODE_TOGGLE.consumeClick()) {
            linkMode = linkMode == FlightMode.STABILIZE ? FlightMode.CRUISE : FlightMode.STABILIZE;
            FlightComputerNetwork.sendToolConfig(linkMode, linkDirection);
        }
        if (KeyBindings.LINK_VECTOR_NEXT.consumeClick()) {
            linkDirection = linkDirection.next(1);
            FlightComputerNetwork.sendToolConfig(linkMode, linkDirection);
        }
    }

    @SubscribeEvent
    public static void onLinkToolMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!KeyBindings.LINK_FOCUS.isDown()) return;
        if (!mc.player.getMainHandItem().is(ModItems.FLIGHT_LINK_TOOL.get())) return;
        double scroll = event.getScrollDeltaY();
        if (scroll != 0.0D) {
            linkDirection = linkDirection.next(scroll > 0.0D ? 1 : -1);
            FlightComputerNetwork.sendToolConfig(linkMode, linkDirection);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderLinkOverlay(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!KeyBindings.LINK_FOCUS.isDown()) return;
        if (!mc.player.getMainHandItem().is(ModItems.FLIGHT_LINK_TOOL.get())) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int panelW = 310, panelH = 112;
        int left = Math.max(8, (w - panelW) / 2);
        int top = Math.max(8, h - panelH - 14);
        g.fill(left, top, left + panelW, top + panelH, 0xE610151B);
        g.fill(left, top, left + panelW, top + 2, linkMode == FlightMode.STABILIZE ? 0xFF55FFAA : 0xFF55AAFF);
        g.drawString(mc.font, "UZ LINK SCHEMATIC / THRUSTER SELECT", left + 10, top + 9, 0xFFE6EEF2);
        g.drawString(mc.font, "MODE", left + 10, top + 27, 0xFF9DAEB5);
        g.drawString(mc.font, linkMode == FlightMode.STABILIZE ? "STABILISER" : "AUTOPILOT", left + 58, top + 27, linkMode == FlightMode.STABILIZE ? 0xFF55FFAA : 0xFF55AAFF);
        g.drawString(mc.font, "VECTOR", left + 10, top + 43, 0xFF9DAEB5);
        g.drawString(mc.font, linkDirection.name() + " [" + linkDirection.shortName() + "]", left + 58, top + 43, 0xFFFFCC55);
        if (mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            var state = mc.level.getBlockState(hit.getBlockPos());
            g.drawString(mc.font, "SELECTED BLOCK", left + 10, top + 61, 0xFF9DAEB5);
            g.drawString(mc.font, state.getBlock().getName().getString(), left + 95, top + 61, 0xFFE6EEF2);
            g.drawString(mc.font, hit.getBlockPos().toShortString(), left + 10, top + 77, 0xFFBFD0D8);
            g.drawString(mc.font, "RIGHT-CLICK TO LINK", left + 10, top + 94, 0xFF66D9FF);
        } else {
            g.drawString(mc.font, "Aim at a compatible thruster", left + 10, top + 77, 0xFFBFD0D8);
            g.drawString(mc.font, "Right-click a Flight Controller first", left + 10, top + 94, 0xFFFFCC55);
        }
        int sx = left + 180, sy = top + 38;
        VectorDirection[] dirs = VectorDirection.values();
        for (int i = 0; i < dirs.length; i++) {
            int x = sx + (i % 3) * 38, y = sy + (i / 3) * 24;
            boolean active = dirs[i] == linkDirection;
            g.fill(x, y, x + 30, y + 18, active ? 0xFF335566 : 0xFF1C252C);
            g.drawString(mc.font, dirs[i].shortName(), x + 11, y + 5, active ? 0xFFFFCC55 : 0xFF9DAEB5);
        }
    }

    private KeyInputHandler() {}
}
