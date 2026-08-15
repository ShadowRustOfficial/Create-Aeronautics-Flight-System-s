package com.flightcomputer.mixin;

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
            BlockEntity blockEntity = player.level().getBlockEntity(payload.controllerPos());
            if (!(blockEntity instanceof com.flightcomputer.block.FlightControllerBlockEntity controller)) return;
            if (!Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())) return;

            FlightIdentityAccess identity = (FlightIdentityAccess)(Object)controller;

            if (name.equals("__SET_HOME__")) {
                identity.flightcomputer$setHome(player.getUUID(), new Vec3(payload.x(), payload.y(), payload.z()));
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
}