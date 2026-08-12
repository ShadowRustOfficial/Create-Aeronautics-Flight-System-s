package com.flightcomputer.item;

import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.ReflectivePropulsionSource;
import com.flightcomputer.control.VectorDirection;
import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlightLinkToolItem extends Item {
    private static final Map<UUID, LinkSession> SESSIONS = new ConcurrentHashMap<>();
    public FlightLinkToolItem(Properties properties) { super(properties); }
    public static void setSelection(UUID playerId, int modeId, int directionId) {
        if (playerId == null) return;
        LinkSession old = SESSIONS.get(playerId);
        BlockPos controller = old == null ? null : old.controller;
        FlightMode mode = FlightMode.values()[Math.floorMod(modeId, FlightMode.values().length)];
        VectorDirection direction = VectorDirection.values()[Math.floorMod(directionId, VectorDirection.values().length)];
        SESSIONS.put(playerId, new LinkSession(controller, mode, direction));
    }
    public static LinkSession session(UUID playerId) { return SESSIONS.getOrDefault(playerId, new LinkSession(null, FlightMode.STABILIZE, VectorDirection.NORTH)); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel(); Player player = context.getPlayer(); if (player == null) return InteractionResult.PASS;
        BlockPos clicked = context.getClickedPos(); LinkSession session = session(player.getUUID());
        if (!level.isClientSide && level.getBlockEntity(clicked) instanceof FlightControllerBlockEntity controller) {
            SESSIONS.put(player.getUUID(), new LinkSession(clicked.immutable(), session.mode, session.direction));
            player.displayClientMessage(Component.literal("LINK TOOL ARMED - " + displayMode(session.mode) + " / " + session.direction.shortName()), true);
            return InteractionResult.SUCCESS;
        }
        if (session.controller == null) { if (!level.isClientSide) player.displayClientMessage(Component.literal("Right-click a Flight Controller first."), true); return InteractionResult.sidedSuccess(level.isClientSide); }
        if (clicked.equals(session.controller)) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockEntity target = level.getBlockEntity(clicked); if (target == null) return InteractionResult.sidedSuccess(level.isClientSide);
        double[] offset = {clicked.getX()+.5D-(session.controller.getX()+.5D), clicked.getY()+.5D-(session.controller.getY()+.5D), clicked.getZ()+.5D-(session.controller.getZ()+.5D)};
        var propulsion = ReflectivePropulsionSource.tryCreate(target, session.direction, offset);
        if (!(propulsion instanceof ReflectivePropulsionSource reflective)) { if (!level.isClientSide) player.displayClientMessage(Component.literal("Selected block is not a compatible thruster."), true); return InteractionResult.sidedSuccess(level.isClientSide); }
        VectorDirection physical = reflective.getPhysicalDirection();
        if (physical != null && physical != session.direction) { if (!level.isClientSide) player.displayClientMessage(Component.literal("Thruster faces " + physical.shortName() + " - select " + physical.shortName() + " for this bank."), true); return InteractionResult.sidedSuccess(level.isClientSide); }
        if (!level.isClientSide && level.getBlockEntity(session.controller) instanceof FlightControllerBlockEntity controller) {
            if (player.distanceToSqr(session.controller.getX()+.5D, session.controller.getY()+.5D, session.controller.getZ()+.5D) > 64.0D*64.0D) { player.displayClientMessage(Component.literal("Too far from Flight Controller."), true); return InteractionResult.FAIL; }
            controller.bindVector(session.mode, session.direction, clicked);
            player.displayClientMessage(Component.literal("BANK SEEDED " + clicked.toShortString() + " -> " + displayMode(session.mode) + " / " + session.direction.shortName() + " - matching thrusters will be auto-discovered."), true);
        }
        return InteractionResult.SUCCESS;
    }
    private static String displayMode(FlightMode mode) { return mode == FlightMode.STABILIZE ? "STABILISER" : "AUTOPILOT"; }
    public record LinkSession(BlockPos controller, FlightMode mode, VectorDirection direction) { }
}
