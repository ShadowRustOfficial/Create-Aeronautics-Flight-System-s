package com.flightcomputer.client;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.VectorDirection;
import com.flightcomputer.item.FlightLinkToolItem;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class LinkToolClientHandler {
    private LinkToolClientHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            LinkToolClientState.clear();
            return;
        }

        if (!isHoldingTool(mc)) {
            LinkToolClientState.clear();
            return;
        }

        if (KeyBindings.LINK_MODE_TOGGLE.consumeClick() && KeyBindings.LINK_FOCUS.isDown()) {
            LinkToolClientState.toggleMode();
        }

        if (!KeyBindings.LINK_FOCUS.isDown()) {
            while (mc.options.keyUse.consumeClick()) {
                if (mc.hitResult instanceof BlockHitResult hit
                        && mc.level.getBlockEntity(hit.getBlockPos()) instanceof FlightControllerBlockEntity) {
                    LinkToolClientState.selectController(hit.getBlockPos());
                }
            }
            return;
        }

        BlockPos controllerPos = LinkToolClientState.controllerPos();
        if (controllerPos == null) return;

        while (mc.options.keyUse.consumeClick()) {
            if (!(mc.hitResult instanceof BlockHitResult hit)) continue;
            if (hit.getBlockPos().equals(controllerPos)) continue;
            FlightMode mode = LinkToolClientState.mode() == LinkToolClientState.LinkMode.STABILISER
                    ? FlightMode.STABILIZE : FlightMode.CRUISE;
            FlightComputerNetwork.sendVectorLink(
                    controllerPos, hit.getBlockPos(), mode, LinkToolClientState.direction());
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !KeyBindings.LINK_FOCUS.isDown()
                || !isHoldingTool(mc)
                || LinkToolClientState.controllerPos() == null) return;

        int delta = event.getScrollDeltaY() > 0 ? -1 : event.getScrollDeltaY() < 0 ? 1 : 0;
        if (delta != 0) {
            LinkToolClientState.scroll(delta);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !KeyBindings.LINK_FOCUS.isDown()
                || !isHoldingTool(mc)
                || LinkToolClientState.controllerPos() == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int left = 18;
        int top = mc.getWindow().getGuiScaledHeight() - 142;
        int width = 440;
        int height = 116;

        g.fill(left, top, left + width, top + height, 0xCC0E141A);
        g.fill(left, top, left + width, top + 2, 0xFF55D6FF);
        g.drawString(mc.font, "HOLD [LINK FOCUS] TO FOCUS", left + 12, top + 10, 0xFFFFFFFF);
        g.drawString(mc.font,
                "MODE: " + (LinkToolClientState.mode() == LinkToolClientState.LinkMode.STABILISER
                        ? "STABILISER" : "AUTOPILOT"),
                left + 250, top + 10,
                LinkToolClientState.mode() == LinkToolClientState.LinkMode.STABILISER
                        ? 0xFF55FF55 : 0xFF55D6FF);

        VectorDirection selected = LinkToolClientState.direction();
        VectorDirection[] directions = VectorDirection.values();
        for (int i = 0; i < directions.length; i++) {
            VectorDirection direction = directions[i];
            int x = left + 12 + i * 68;
            boolean active = direction == selected;
            g.fill(x, top + 34, x + 58, top + 58, active ? 0xFF315B6A : 0xFF1C252B);
            g.drawCenteredString(mc.font, direction.shortName(), x + 29, top + 42,
                    active ? 0xFFFFFFFF : 0xFF9AA6AC);
        }

        String target = "AIM AT THRUSTER • [USE] LINK";
        if (mc.hitResult instanceof BlockHitResult hit) {
            target = "TARGET: " + hit.getBlockPos() + " • [USE] LINK";
        }
        g.drawString(mc.font, target, left + 12, top + 70, 0xFFBFC8CC);
        g.drawString(mc.font, "[SCROLL] VECTOR   ["
                + KeyBindings.LINK_MODE_TOGGLE.getTranslatedKeyMessage().getString().toUpperCase()
                + "] MODE", left + 12, top + 90, 0xFF777F84);
    }

    private static boolean isHoldingTool(Minecraft mc) {
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        return main.getItem() instanceof FlightLinkToolItem || off.getItem() instanceof FlightLinkToolItem;
    }
}
