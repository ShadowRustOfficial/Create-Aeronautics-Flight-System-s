package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.VehicleState;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Corrects the attitude presented to the stabiliser using Sable's actual Y-X-Z vessel pose.
 * The runtime manager historically extracted the quaternion with a generic formula that does
 * not match the Y-X-Z composition used by the flight controller, causing pitch/roll to cross-talk
 * badly as the vessel tilted. This keeps the existing runtime/control architecture intact and
 * only corrects the state immediately before the controller consumes it.
 */
@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager$Runtime")
public abstract class FlightControllerAttitudeCorrectionMixin {
    @Shadow private VehicleState snapshot;

    @Inject(method = "update", at = @At("TAIL"))
    private void flightcomputer$correctAttitude(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if (controller == null || snapshot == null || controller.getLevel() == null) return;
        try {
            Class<?> companionType = Class.forName(
                    "dev.ryanhcode.sable.companion.SableCompanion",
                    false,
                    FlightControllerAttitudeCorrectionMixin.class.getClassLoader());
            Object companion = companionType.getField("INSTANCE").get(null);
            if (companion == null) return;
            Object subLevel = companion.getClass()
                    .getMethod("getContaining", net.minecraft.world.level.block.entity.BlockEntity.class)
                    .invoke(companion, controller);
            if (subLevel == null) return;
            Object pose = subLevel.getClass().getMethod("logicalPose").invoke(subLevel);
            Object orientation = pose.getClass().getMethod("orientation").invoke(pose);
            if (!(orientation instanceof Quaterniondc q)) return;

            Vector3d euler = new Vector3d();
            q.getEulerAnglesYXZ(euler);
            snapshot.pitch = euler.x;
            snapshot.yaw = euler.y;
            snapshot.roll = euler.z;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Keep the runtime manager's last valid state if Sable is unavailable.
        }
    }
}
