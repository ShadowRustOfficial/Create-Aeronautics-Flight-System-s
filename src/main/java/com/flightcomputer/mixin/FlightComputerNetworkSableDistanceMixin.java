package com.flightcomputer.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/** Optional Sable compatibility for Flight Computer packet distance checks. */
@Mixin(targets = "com.flightcomputer.network.FlightComputerNetwork")
public abstract class FlightComputerNetworkSableDistanceMixin {
    /**
     * The base network check remains authoritative unless Sable is actually present and can
     * project the controller position. This avoids replacing the entire security helper with an
     * @Overwrite, while still allowing packets addressed to moving sub-level controllers.
     */
    @Inject(method = "near", at = @At("HEAD"), cancellable = true)
    private static void flightcomputer$projectSableDistance(ServerPlayer player, BlockPos pos, double distance,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (player == null || pos == null) return;
        Vec3 target = project(player, Vec3.atCenterOf(pos));
        if (target != null) {
            cir.setReturnValue(player.position().distanceToSqr(target) <= distance * distance);
        }
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
