package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.identity.FlightIdentityAccess;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles the Navigation Console's special identity/home/player target commands. */
@Mixin(FlightComputerNetwork.class)
public abstract class FlightComputerNetworkTargetMixin {
    @Inject(method = "handleSetTarget", at = @At("HEAD"), cancellable = true)
    private static void flightcomputer$specialTargets(FlightComputerNetwork.SetTargetPayload payload,
                                                      net.neoforged.neoforge.network.handling.IPayloadContext context,
                                                      CallbackInfo ci) {
        String name = payload.name() == null ? "" : payload.name();
        if (!name.startsWith("__")) return;

        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!near(player, payload.controllerPos(), 64.0D)) return;

            BlockEntity blockEntity = player.level().getBlockEntity(payload.controllerPos());
            if (!(blockEntity instanceof FlightControllerBlockEntity controller)) return;

            FlightIdentityAccess identity = (FlightIdentityAccess)(Object) controller;

            if (name.equals("__SET_NAME__")) {
                identity.flightcomputer$setSubLevelName(namePayload(payload));
                controller.setChanged();
                return;
            }

            if (name.equals("__SET_ID__")) {
                identity.flightcomputer$setFlightId(namePayload(payload));
                controller.setChanged();
                return;
            }

            if (!Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())) return;

            if (name.equals("__SET_HOME__")) {
                identity.flightcomputer$setHome(player.getUUID(), new Vec3(payload.x(), payload.y(), payload.z()));
                controller.setChanged();
                return;
            }

            if (name.equals("__HOME__")) {
                Vec3 home = identity.flightcomputer$getHome(player.getUUID());
                if (home == null) return;
                FlightControlRuntimeManager.setTarget(controller, home, "HOME: " + player.getGameProfile().getName());
                return;
            }

            if (name.startsWith("__PLAYER__:")) {
                String targetName = name.substring("__PLAYER__:".length()).trim();
                if (targetName.isEmpty()) return;
                ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
                if (target == null || !target.level().dimension().equals(player.level().dimension())) return;
                FlightControlRuntimeManager.setTarget(controller, target.position(), "PLAYER: " + target.getGameProfile().getName());
            }
        });
        ci.cancel();
    }

    private static String namePayload(FlightComputerNetwork.SetTargetPayload payload) {
        String value = payload.name() == null ? "" : payload.name();
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(separator + 1).trim() : "";
    }

    private static boolean near(ServerPlayer player, net.minecraft.core.BlockPos pos, double radius) {
        return player != null && pos != null
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radius * radius;
    }
}
