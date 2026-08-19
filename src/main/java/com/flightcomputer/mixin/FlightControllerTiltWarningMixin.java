package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side excessive-tilt audio emitted from the Flight Controller's physical position. */
@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager")
public abstract class FlightControllerTiltWarningMixin {
    private static final double WARNING_DEGREES = 30.0D;
    private static final double CLEAR_DEGREES = 25.0D;
    /** TILT WARNING is replayed at the end of its normal short cue interval while tilt remains excessive. */
    private static final int TILT_WARNING_REPEAT_TICKS = 20;
    /** WARNING.ogg is the slower secondary warning layer: every 2.5 seconds while excessive tilt persists. */
    private static final int WARNING_REPEAT_TICKS = 50;
    private static final Map<UUID, Integer> TILT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> WARNING_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Boolean> WARNINGS = new HashMap<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private static void flightcomputer$tiltWarning(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;

        UUID id = controller.getControllerId();
        int tiltCooldown = Math.max(0, TILT_COOLDOWNS.getOrDefault(id, 0) - 1);
        int warningCooldown = Math.max(0, WARNING_COOLDOWNS.getOrDefault(id, 0) - 1);
        TILT_COOLDOWNS.put(id, tiltCooldown);
        WARNING_COOLDOWNS.put(id, warningCooldown);

        Double tilt = readTilt(controller);
        if (tilt == null) return;

        boolean warning = WARNINGS.getOrDefault(id, false);
        if (tilt >= WARNING_DEGREES) {
            WARNINGS.put(id, true);
            Level level = controller.getLevel();
            BlockPos pos = controller.getBlockPos();
            if (!warning || tiltCooldown <= 0) {
                level.playSound(null, pos, ModSounds.TILT_WARNING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                TILT_COOLDOWNS.put(id, TILT_WARNING_REPEAT_TICKS);
            }
            if (warningCooldown <= 0) {
                level.playSound(null, pos, ModSounds.WARNING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                WARNING_COOLDOWNS.put(id, WARNING_REPEAT_TICKS);
            }
        } else if (tilt <= CLEAR_DEGREES) {
            WARNINGS.put(id, false);
            TILT_COOLDOWNS.remove(id);
            WARNING_COOLDOWNS.remove(id);
        }
    }

    private static Double readTilt(FlightControllerBlockEntity controller) {
        try {
            Class<?> companionType = Class.forName(
                    "dev.ryanhcode.sable.companion.SableCompanion",
                    false,
                    FlightControllerTiltWarningMixin.class.getClassLoader()
            );
            Object companion = companionType.getField("INSTANCE").get(null);
            if (companion == null) return null;

            Method containing = companion.getClass().getMethod("getContaining", net.minecraft.world.level.block.entity.BlockEntity.class);
            Object subLevel = containing.invoke(companion, controller);
            if (subLevel == null) return null;

            Object pose = subLevel.getClass().getMethod("logicalPose").invoke(subLevel);
            Object orientation = pose.getClass().getMethod("orientation").invoke(pose);
            if (!(orientation instanceof Quaterniondc q)) return null;

            double w = q.w(), x = q.x(), y = q.y(), z = q.z();
            double pitch = Math.asin(Math.max(-1.0D, Math.min(1.0D, 2.0D * (w * x - y * z))));
            double roll = Math.atan2(2.0D * (w * z + x * y), 1.0D - 2.0D * (x * x + y * y));
            return Math.max(Math.abs(Math.toDegrees(pitch)), Math.abs(Math.toDegrees(roll)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
