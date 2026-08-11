package com.flightcomputer.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves a Flight Controller's local position into parent-world coordinates when
 * the controller is inside a Sable Sub-Level. Sable remains an optional runtime
 * integration: no compile-time Sable dependency is required.
 *
 * The Aeronautics/Simulated code uses Sable.HELPER.getContaining(level, Vec3),
 * then SubLevel.logicalPose().transformPosition(Vec3). The resolver mirrors
 * that exact local -> global path while keeping Sable optional at compile time.
 * Reflection handles are resolved once and reused; the hot path performs only
 * the containing lookup and pose transform.
 */
public final class FlightControllerWorldPositionResolver {
    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";

    private volatile boolean initialized;
    private volatile boolean available;
    private Object sableHelper;
    private Method getContaining;
    private Method logicalPose;
    private Method transformPosition;

    public Vec3 resolve(Level level, BlockPos localPosition) {
        if (level == null || localPosition == null) return null;
        Vec3 local = center(localPosition);
        if (!ensureInitialized()) return local;

        try {
            // IMPORTANT: Sable's getContaining API accepts Vec3, not BlockPos.
            Object subLevel = getContaining.invoke(sableHelper, level, local);
            if (subLevel == null) return local;

            Object pose = logicalPose.invoke(subLevel);
            if (pose == null) return local;

            Object result = transformPosition.invoke(pose, local);
            return result instanceof Vec3 worldPosition ? worldPosition : local;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return local;
        }
    }

    public boolean isSableAvailable() {
        ensureInitialized();
        return available;
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        synchronized (this) {
            if (initialized) return available;
            try {
                Class<?> sable = Class.forName(SABLE_CLASS, false, getClass().getClassLoader());
                Field helperField = sable.getField("HELPER");
                sableHelper = helperField.get(null);
                if (sableHelper == null) throw new IllegalStateException("Sable HELPER is null");

                // Matches SimMovementContext in Creators-of-Aeronautics/Simulated-Project:
                // Sable.HELPER.getContaining(level, position) where position is Vec3.
                getContaining = sableHelper.getClass().getMethod("getContaining", Level.class, Vec3.class);
                Class<?> subLevelClass = getContaining.getReturnType();
                logicalPose = subLevelClass.getMethod("logicalPose");
                Class<?> poseClass = logicalPose.getReturnType();
                transformPosition = poseClass.getMethod("transformPosition", Vec3.class);
                available = true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                available = false;
            } finally {
                initialized = true;
            }
        }
        return available;
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
}
