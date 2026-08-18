package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Emits server-authoritative excessive-tilt warning audio from the controller block. */
@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager")
public abstract class FlightControllerTiltWarningMixin {
    private static final double WARNING_DEGREES = 30.0D;
    private static final double CLEAR_DEGREES = 25.0D;
    private static final int TILT_REPEAT_TICKS = 6;
    private static final int GENERAL_WARNING_REPEAT_TICKS = 50;
    private static final Map<UUID, Integer> TILT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> GENERAL_WARNING_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Boolean> WARNINGS = new HashMap<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightcomputer$tiltWarning(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        UUID id = controller.getControllerId();
        int tiltCooldown = Math.max(0, TILT_COOLDOWNS.getOrDefault(id, 0) - 1);
        int generalWarningCooldown = Math.max(0, GENERAL_WARNING_COOLDOWNS.getOrDefault(id, 0) - 1);
        TILT_COOLDOWNS.put(id, tiltCooldown);
        GENERAL_WARNING_COOLDOWNS.put(id, generalWarningCooldown);
        Double tilt = readTilt(controller);
        if (tilt == null) return;
        boolean warning = WARNINGS.getOrDefault(id, false);
        if (tilt >= WARNING_DEGREES) {
            WARNINGS.put(id, true);
            Level level = controller.getLevel();
            BlockPos pos = controller.getBlockPos();
            if (tiltCooldown <= 0) {
                level.playSound(null, pos, ModSounds.TILT_WARNING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                TILT_COOLDOWNS.put(id, TILT_REPEAT_TICKS);
            }
            if (!warning || generalWarningCooldown <= 0) {
                level.playSound(null, pos, ModSounds.WARNING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                GENERAL_WARNING_COOLDOWNS.put(id, GENERAL_WARNING_REPEAT_TICKS);
            }
        } else if (tilt <= CLEAR_DEGREES) {
            WARNINGS.put(id, false);
            TILT_COOLDOWNS.put(id, 0);
            GENERAL_WARNING_COOLDOWNS.put(id, 0);
        }
    }

    private static Double readTilt(FlightControllerBlockEntity controller) {
        try {
            Class<?> companionType = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false, FlightControllerTiltWarningMixin.class.getClassLoader());
            Object companion = companionType.getField("INSTANCE").get(null);
            if (companion == null) return null;
            Method containing = companion.getClass().getMethod("getContaining", net.minecraft.world.level.block.entity.BlockEntity.class);
            Object subLevel = containing.invoke(companion, controller);
            if (subLevel == null) return null;
            Object pose = subLevel.getClass().getMethod("logicalPose").invoke(subLevel);
            Object orientation = pose.getClass().getMethod("orientation").invoke(pose);
            if (!(orientation instanceof Quaterniondc q)) return null;
            Vector3d euler = new Vector3d();
            q.getEulerAnglesYXZ(euler);
            return Math.max(Math.abs(Math.toDegrees(euler.x)), Math.abs(Math.toDegrees(euler.z)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
