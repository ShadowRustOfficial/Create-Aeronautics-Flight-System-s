package com.flightcomputer.mixin;

import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.identity.FlightIdentityAccess;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/** Optional Sable compatibility plus server-authoritative flight identity and target selection. */
@Mixin(targets = "com.flightcomputer.network.FlightComputerNetwork")
public abstract class FlightComputerNetworkSableDistanceMixin {
    @Inject(method = "near", at = @At("HEAD"), cancellable = true)
    private static void flightcomputer$projectSableDistance(ServerPlayer player, BlockPos pos, double distance,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (player == null || pos == null) return;
        Vec3 target = project(player, Vec3.atCenterOf(pos));
        if (target != null) cir.setReturnValue(player.position().distanceToSqr(target) <= distance * distance);
    }

    @Inject(method = "handleSetTarget", at = @At("HEAD"), cancellable = true)
    private static void flightcomputer$specialTargets(FlightComputerNetwork.SetTargetPayload payload,
                                                      IPayloadContext context, CallbackInfo ci) {
        String name = payload.name() == null ? "" : payload.name();
        if (!name.startsWith("__")) return;

        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Vec3 controllerWorld = project(player, Vec3.atCenterOf(payload.controllerPos()));
            if (controllerWorld == null) controllerWorld = Vec3.atCenterOf(payload.controllerPos());
            if (player.position().distanceToSqr(controllerWorld) > 64.0D * 64.0D) return;

            BlockEntity blockEntity = player.level().getBlockEntity(payload.controllerPos());
            if (!(blockEntity instanceof com.flightcomputer.block.FlightControllerBlockEntity controller)) return;
            if (!Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())) return;

            FlightIdentityAccess identity = (FlightIdentityAccess)(Object)controller;
            if (name.startsWith("__SET_NAME__:")) {
                identity.flightcomputer$setSubLevelName(name.substring("__SET_NAME__:".length()));
                return;
            }
            if (name.startsWith("__SET_ID__:")) {
                identity.flightcomputer$setFlightId(name.substring("__SET_ID__:".length()));
                return;
            }
            if (name.equals("__SET_HOME__")) {
                identity.flightcomputer$setHome(player.getUUID(), new Vec3(payload.x(), payload.y(), payload.z()));
                return;
            }
            if (name.equals("__HOME__")) {
                Vec3 home = identity.flightcomputer$getHome(player.getUUID());
                if (home != null) FlightControlRuntimeManager.setTarget(controller, home, "HOME: " + player.getGameProfile().name());
                return;
            }
            if (name.startsWith("__PLAYER__:")) {
                String targetName = name.substring("__PLAYER__:".length()).trim();
                if (targetName.isEmpty()) return;
                ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
                if (target == null || !target.level().dimension().equals(player.level().dimension())) return;
                FlightControlRuntimeManager.setTarget(controller, target.position(), "PLAYER: " + target.getGameProfile().name());
            }
        });
        ci.cancel();
    }

    /** Returns null when Sable compatibility is unavailable so vanilla Flight Computer checks run unchanged. */
    private static Vec3 project(ServerPlayer player, Vec3 local) {
        try {
            Class<?> companion = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false,
                    FlightComputerNetworkSableDistanceMixin.class.getClassLoader());
            Object instance = companion.getField("INSTANCE").get(null);
            Method method = instance.getClass().getMethod("projectOutOfSubLevel",
                    net.minecraft.world.level.Level.class, Vec3.class);
            Object result = method.invoke(instance, player.level(), local);
            return result instanceof Vec3 vec ? vec : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}