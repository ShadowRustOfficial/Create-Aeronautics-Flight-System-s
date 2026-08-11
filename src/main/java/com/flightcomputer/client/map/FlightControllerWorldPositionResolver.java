package com.flightcomputer.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves local Sub-Level coordinates into parent-world coordinates.
 * Sable remains an optional runtime integration with no compile-time dependency.
 */
public final class FlightControllerWorldPositionResolver {
    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";
    private static final String SABLE_HELPER_CLASS = "dev.ryanhcode.sable.api.SubLevelHelper";

    private volatile boolean initialized;
    private volatile boolean available;
    private Object sableHelper;
    private Method getContainingVec3;
    private Method getContainingBlockPos;
    private Method logicalPose;
    private Method transformPosition;

    public Vec3 resolve(Level level, BlockPos localPosition) {
        if (level == null || localPosition == null) return null;
        BlockEntity blockEntity = level.getBlockEntity(localPosition);
        if (blockEntity != null) return resolve(level, blockEntity);
        return resolve(level, center(localPosition));
    }

    /** Precise coordinate overload for entities/players; does not quantize to a BlockPos. */
    public Vec3 resolve(Level level, Vec3 localPosition) {
        if (level == null || localPosition == null) return null;
        if (!ensureInitialized()) return localPosition;
        try {
            Object subLevel = getContainingVec3 == null ? null : getContainingVec3.invoke(sableHelper, level, localPosition);
            if (subLevel == null) return localPosition;
            Object pose = logicalPose.invoke(subLevel);
            if (pose == null) return localPosition;
            Object result = transformPosition.invoke(pose, localPosition);
            return result instanceof Vec3 worldPosition ? worldPosition : localPosition;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return localPosition;
        }
    }

    public Vec3 resolve(Level level, BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        return resolve(level, pos, blockEntity, state);
    }

    private Vec3 resolve(Level level, BlockPos localPosition, BlockEntity blockEntity, BlockState state) {
        if (level == null || localPosition == null) return null;
        Vec3 local = center(localPosition);
        if (!ensureInitialized()) return local;

        try {
            Object subLevel = null;
            if (getContainingVec3 != null) subLevel = getContainingVec3.invoke(sableHelper, level, local);
            if (subLevel == null && getContainingBlockPos != null) subLevel = getContainingBlockPos.invoke(sableHelper, level, localPosition);
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

                Class<?> helperType = Class.forName(SABLE_HELPER_CLASS, false, getClass().getClassLoader());
                getContainingVec3 = findMethod(helperType, "getContaining", Level.class, Vec3.class);
                getContainingBlockPos = findMethod(helperType, "getContaining", Level.class, BlockPos.class);
                if (getContainingVec3 == null && getContainingBlockPos == null) {
                    throw new NoSuchMethodException("Sable SubLevelHelper.getContaining overloads not found");
                }

                Class<?> subLevelClass = getContainingVec3 != null ? getContainingVec3.getReturnType() : getContainingBlockPos.getReturnType();
                logicalPose = subLevelClass.getMethod("logicalPose");
                transformPosition = logicalPose.getReturnType().getMethod("transformPosition", Vec3.class);
                available = true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                available = false;
            } finally {
                initialized = true;
            }
        }
        return available;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try { return type.getMethod(name, parameters); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
}
