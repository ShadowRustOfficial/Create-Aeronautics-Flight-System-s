package com.flightcomputer.client.gui;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.item.CoolingUpgradeItem;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Dedicated cooling-upgrade inventory tab. Cooling inventory is never rendered on Thermal/Flight Control. */
public final class CoolingConsoleScreen extends Screen {
    private static final int PANEL = 0xE610141A;
    private static final int TEXT = 0xFFE6EEF2;
    private static final int MUTED = 0xFF9DAEB5;
    private static final int CYAN = 0xFF66D9FF;
    private static final int GREEN = 0xFF55FF55;

    private final BlockPos controllerPos;
    private FlightControllerBlockEntity controller;
    private int left;
    private int top;

    public CoolingConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Flight Computer - Cooling"));
        this.controllerPos = controllerPos;
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override protected void init() {
        controller = getController();
        left = Math.max(10, (width - 640) / 2);
        top = Math.max(20, (height - 340) / 2);
        addRenderableWidget(Button.builder(Component.literal("NAVIGATION"), b -> minecraft.setScreen(new NavigationConsoleScreen(controllerPos))).bounds(left, top, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("THERMAL"), b -> minecraft.setScreen(new ThermalConsoleScreen(controllerPos))).bounds(left + 150, top, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("COOLING"), b -> {}).bounds(left + 300, top, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose()).bounds(left + 490, top, 145, 22).build());
        for (int slot = 0; slot < 3; slot++) {
            final int bay = slot;
            addRenderableWidget(Button.builder(Component.literal("INSERT HELD"), b -> FlightComputerNetwork.sendCoolingSlot(controllerPos, bay, 0))
                    .bounds(left + 85 + slot * 155, top + 160, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("REMOVE"), b -> FlightComputerNetwork.sendCoolingSlot(controllerPos, bay, 1))
                    .bounds(left + 85 + slot * 155, top + 184, 120, 20).build());
        }
    }

    private FlightControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) return null;
        BlockEntity be = minecraft.level.getBlockEntity(controllerPos);
        return be instanceof FlightControllerBlockEntity fc ? fc : null;
    }

    @Override public void tick() {
        super.tick();
        controller = getController();
        if (controller == null) onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int right = left + 640, bottom = top + 340;
        g.fill(left - 8, top - 8, right + 8, bottom + 8, PANEL);
        g.drawString(font, "FLIGHT COMPUTER / COOLING MANAGEMENT", left, top + 34, TEXT);
        g.drawString(font, "DEDICATED COOLING UPGRADE BAY", left, top + 58, MUTED);
        for (int slot = 0; slot < 3; slot++) renderSlot(g, slot);
        int tier = controller == null ? 0 : controller.getCoolingTier().ordinal();
        g.drawString(font, "ACTIVE COOLING TIER", left + 20, top + 220, TEXT);
        g.drawString(font, tier == 0 ? "NONE" : "TIER " + tier, left + 250, top + 220, tier == 0 ? MUTED : GREEN);
        g.drawString(font, "Hold a Cooling Upgrade in your MAIN HAND, then click INSERT HELD.", left + 20, top + 250, CYAN);
        g.drawString(font, "REMOVE returns the installed upgrade to your inventory.", left + 20, top + 274, MUTED);
        g.drawString(font, "Only Basic / Improved / Advanced Cooling Upgrades are accepted.", left + 20, top + 298, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(left + 300, top + 20, left + 445, top + 22, CYAN);
    }

    private void renderSlot(GuiGraphics g, int slot) {
        int x = left + 100 + slot * 155, y = top + 82;
        g.fill(x, y, x + 110, y + 70, 0xFF0D1114);
        g.fill(x, y, x + 110, y + 2, CYAN);
        g.drawString(font, "BAY " + (slot + 1), x + 8, y + 10, TEXT);
        ItemStack stack = controller == null ? ItemStack.EMPTY : controller.getUpgradeHandler().getStackInSlot(slot);
        if (stack.isEmpty()) g.drawString(font, "EMPTY", x + 8, y + 34, MUTED);
        else { g.renderItem(stack, x + 44, y + 22); g.renderItemDecorations(font, stack, x + 44, y + 22); }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (controller != null) {
            for (int slot = 0; slot < 3; slot++) {
                int x = left + 100 + slot * 155, y = top + 82;
                if (mouseX >= x && mouseX <= x + 110 && mouseY >= y && mouseY <= y + 70) {
                    ItemStack hand = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
                    if (button == 1 || hand.isEmpty()) FlightComputerNetwork.sendCoolingSlot(controllerPos, slot, 1);
                    else if (hand.getItem() instanceof CoolingUpgradeItem) FlightComputerNetwork.sendCoolingSlot(controllerPos, slot, 0);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    @Override public boolean isPauseScreen() { return false; }
}
