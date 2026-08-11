package com.flightcomputer.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves local Sub-Level coordinates into parent-world coordinates.
 * Sable remains an optional runtime integration with no compile-time dependency.
 *
 * <p>The authoritative Sable path is ActiveSableCompanion.getContainingClient(...),
 * not SubLevelHelper.getContaining(...). A BlockEntity is therefore resolved through
 * the exact client Sub-Level containing it, then its logical pose transforms the
 * BlockEntity's local/storage coordinate into world space.</p>
 */
public final class FlightControllerWorldPositionResolver {
    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";
    private static final String ACTIVE_COMPANION_CLASS = "dev.ryanhcode.sable.ActiveSableCompanion";
    private static final String CLIENT_SUB_LEVEL_ACCESS_CLASS = "dev.ryanhcode.sable.companion.ClientSubLevelAccess";

    private volatile boolean initialized;
    private volatile boolean available;
    private Object sableHelper;
    private Method getContainingClientBlockEntity;
    private Method getContainingClientVector;
    private Method logicalPose;
    private Method transformPositionVec3;

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
            Object subLevel = findContainingClient(localPosition);
            if (subLevel == null) return localPosition;
            return transform(subLevel, localPosition);
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
            // This is the authoritative Sable lookup for a placed BlockEntity.
            // It resolves the Sub-Level containing the actual controller rather than
            // interpreting the Sub-Level's storage BlockPos as a parent-world position.
            Object subLevel = blockEntity == null ? null
                    : getContainingClientBlockEntity.invoke(sableHelper, blockEntity);

            // If there is no BlockEntity available, retain the precise-coordinate path.
            if (subLevel == null && blockEntity == null) {
                subLevel = findContainingClient(local);
            }
            if (subLevel == null) return local;

            return transform(subLevel, local);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return local;
        }
    }

    public boolean isSableAvailable() {
        ensureInitialized();
        return available;
    }

    private Object findContainingClient(Vec3 position) throws ReflectiveOperationException {
        if (getContainingClientVector == null) return null;
        Vector3d point = new Vector3d(position.x, position.y, position.z);
        return getContainingClientVector.invoke(sableHelper, (Vector3dc) point);
    }

    private Vec3 transform(Object subLevel, Vec3 local) throws ReflectiveOperationException {
        Object pose = logicalPose.invoke(subLevel);
        if (pose == null) return local;
        Object result = transformPositionVec3.invoke(pose, local);
        return result instanceof Vec3 worldPosition ? worldPosition : local;
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

                Class<?> companionType = Class.forName(ACTIVE_COMPANION_CLASS, false, getClass().getClassLoader());
                if (!companionType.isInstance(sableHelper)) {
                    throw new IllegalStateException("Sable HELPER is not an ActiveSableCompanion");
                }

                getContainingClientBlockEntity = companionType.getMethod("getContainingClient", BlockEntity.class);
                getContainingClientVector = companionType.getMethod("getContainingClient", Vector3dc.class);

                Class<?> accessType = Class.forName(CLIENT_SUB_LEVEL_ACCESS_CLASS, false, getClass().getClassLoader());
                logicalPose = accessType.getMethod("logicalPose");
                Class<?> poseType = logicalPose.getReturnType();
                transformPositionVec3 = poseType.getMethod("transformPosition", Vec3.class);

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
