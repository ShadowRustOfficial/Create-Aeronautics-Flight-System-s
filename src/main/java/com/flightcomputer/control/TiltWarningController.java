package com.flightcomputer.control;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side excessive-tilt warning. Uses Sable's authoritative logical pose. */
public final class TiltWarningController {
    private static final double WARNING_ANGLE = Math.toRadians(30.0D);
    private static final double CLEAR_ANGLE = Math.toRadians(25.0D);
    /** TILT WARNING is re-issued once per second while the condition remains active. */
    private static final int TILT_WARNING_REPEAT_TICKS = 20;
    /** WARNING.ogg is the slower periodic warning layer requested for excessive tilt. */
    private static final int WARNING_REPEAT_TICKS = 50;
    private static final Map<UUID, Integer> TILT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> WARNING_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Boolean> WARNING_ACTIVE = new HashMap<>();

    private TiltWarningController() { }

    public static void tick(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;

        UUID id = controller.getControllerId();
        int tiltCooldown = Math.max(0, TILT_COOLDOWNS.getOrDefault(id, 0) - 1);
        int warningCooldown = Math.max(0, WARNING_COOLDOWNS.getOrDefault(id, 0) - 1);
        TILT_COOLDOWNS.put(id, tiltCooldown);
        WARNING_COOLDOWNS.put(id, warningCooldown);

        double[] attitude = readAttitude(controller.getLevel(), controller.getBlockPos(), controller);
        if (attitude == null) return;

        double tilt = Math.max(Math.abs(attitude[0]), Math.abs(attitude[1]));
        boolean active = WARNING_ACTIVE.getOrDefault(id, false);

        if (tilt >= WARNING_ANGLE) {
            WARNING_ACTIVE.put(id, true);
            Level level = controller.getLevel();
            BlockPos pos = controller.getBlockPos();
            if (tiltCooldown <= 0) {
                level.playSound(null, pos, ModSounds.TILT_WARNING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                TILT_COOLDOWNS.put(id, TILT_WARNING_REPEAT_TICKS);
            }
            if (warningCooldown <= 0) {
                level.playSound(null, pos, ModSounds.WARNING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                WARNING_COOLDOWNS.put(id, WARNING_REPEAT_TICKS);
            }
        } else if (active && tilt <= CLEAR_ANGLE) {
            WARNING_ACTIVE.put(id, false);
            TILT_COOLDOWNS.remove(id);
            WARNING_COOLDOWNS.remove(id);
        }
    }

    private static double[] readAttitude(Level level, BlockPos controllerPos, FlightControllerBlockEntity controller) {
        try {
            Class<?> companionType = Class.forName(
                    "dev.ryanhcode.sable.companion.SableCompanion",
                    false,
                    TiltWarningController.class.getClassLoader()
            );
            Object companion = companionType.getField("INSTANCE").get(null);
            if (companion == null) return null;

            Object subLevel = null;
            Method containingBlockEntity = findMethod(companion.getClass(), "getContaining", net.minecraft.world.level.block.entity.BlockEntity.class);
            if (containingBlockEntity != null) subLevel = containingBlockEntity.invoke(companion, controller);

            if (subLevel == null) {
                Method tracking = findMethod(companion.getClass(), "getTrackingSubLevel", Entity.class);
                if (tracking != null) {
                    subLevel = tracking.invoke(companion, controller.getLevel().getNearestPlayer(
                            controllerPos.getX() + 0.5D,
                            controllerPos.getY() + 0.5D,
                            controllerPos.getZ() + 0.5D,
                            128.0D,
                            false
                    ));
                }
            }

            if (subLevel == null) {
                Method containingPosition = findMethod(companion.getClass(), "getContaining", Level.class, Vec3.class);
                if (containingPosition != null) subLevel = containingPosition.invoke(companion, level, Vec3.atCenterOf(controllerPos));
            }

            if (subLevel == null) return null;
            Method logicalPose = findMethod(subLevel.getClass(), "logicalPose");
            if (logicalPose == null) return null;
            Object pose = logicalPose.invoke(subLevel);
            Method orientation = findMethod(pose.getClass(), "orientation");
            if (orientation == null) return null;
            Object value = orientation.invoke(pose);
            if (!(value instanceof Quaterniondc q)) return null;

            double w = q.w(), x = q.x(), y = q.y(), z = q.z();
            double pitch = Math.asin(Math.max(-1.0D, Math.min(1.0D, 2.0D * (w * x - y * z))));
            double roll = Math.atan2(2.0D * (w * z + x * y), 1.0D - 2.0D * (x * x + z * z));
            if (!Double.isFinite(pitch) || !Double.isFinite(roll)) return null;
            return new double[]{pitch, roll};
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
