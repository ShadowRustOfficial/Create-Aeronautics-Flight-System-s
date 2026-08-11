package com.flightcomputer.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Method;

/** Makes every Flight Computer packet distance check operate in Sable world space. */
@Mixin(targets = "com.flightcomputer.network.FlightComputerNetwork")
public abstract class FlightComputerNetworkSableDistanceMixin {
    /**
     * @author Flight Computer
     * @reason The original helper compared a player's world position to Sable's extreme plot-grid
     * coordinates. That rejects valid moving-craft packets. Sable's projection is the authoritative
     * conversion between plot-local and logical world space.
     */
    @Overwrite
    private static boolean near(ServerPlayer player, BlockPos pos, double distance) {
        if (player == null || pos == null) return false;
        Vec3 target = project(player, Vec3.atCenterOf(pos));
        return player.position().distanceToSqr(target) <= distance * distance;
    }

    private static Vec3 project(ServerPlayer player, Vec3 local) {
        try {
            Class<?> companion = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false,
                    FlightComputerNetworkSableDistanceMixin.class.getClassLoader());
            Object instance = companion.getField("INSTANCE").get(null);
            Method method = instance.getClass().getMethod("projectOutOfSubLevel",
                    net.minecraft.world.level.Level.class, Vec3.class);
            Object result = method.invoke(instance, player.level(), local);
            return result instanceof Vec3 vec ? vec : local;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return local;
        }
    }
}
