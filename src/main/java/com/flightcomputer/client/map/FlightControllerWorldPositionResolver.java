package com.flightcomputer.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves Sable sub-level plot/storage coordinates into logical world coordinates.
 *
 * <p>Sable stores positions inside its plot grid at deliberately extreme coordinates.
 * We must never use those raw coordinates for the navigation map. The public
 * Sable Companion API provides the supported projection path, so this class uses
 * that API reflectively and keeps the mod optional when Sable is absent.</p>
 */
public final class FlightControllerWorldPositionResolver {
    private static final String COMPANION_CLASS = "dev.ryanhcode.sable.companion.SableCompanion";

    private volatile boolean initialized;
    private volatile boolean available;
    private Object companion;
    private Method getContaining;
    private Method projectOutOfSubLevel;
    private Method isInPlotGrid;

    public Vec3 resolve(Level level, BlockPos localPosition) {
        if (level == null || localPosition == null) return null;
        return resolve(level, center(localPosition));
    }

    /** Precise coordinate overload for entities/players; never quantizes to BlockPos. */
    public Vec3 resolve(Level level, Vec3 position) {
        if (level == null || position == null) return null;
        if (!ensureInitialized()) return position;
        try {
            // This is the supported Sable Companion projection. It handles both
            // ordinary world coordinates and positions inside a sub-level plot.
            if (projectOutOfSubLevel != null) {
                Object projected = projectOutOfSubLevel.invoke(companion, level, position);
                if (projected instanceof Vec3 world) return world;
            }

            // Fallback to the documented containing-sub-level + logical-pose path.
            if (getContaining != null) {
                Object subLevel = getContaining.invoke(companion, level, position);
                if (subLevel != null) {
                    Method logicalPose = subLevel.getClass().getMethod("logicalPose");
                    Object pose = logicalPose.invoke(subLevel);
                    if (pose != null) {
                        Method transform = pose.getClass().getMethod("transformPosition", Vec3.class);
                        Object projected = transform.invoke(pose, position);
                        if (projected instanceof Vec3 world) return world;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Preserve ordinary-world compatibility if Sable's runtime API is unavailable.
        }
        return position;
    }

    /** Returns true when the Sable Companion runtime is available. */
    public boolean isSableAvailable() {
        ensureInitialized();
        return available;
    }

    /** Returns whether a raw position lies in Sable's plot grid. */
    public boolean isPlotCoordinate(Level level, Vec3 position) {
        if (level == null || position == null || !ensureInitialized() || isInPlotGrid == null) return false;
        try {
            Object result = isInPlotGrid.invoke(companion, level, position);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        synchronized (this) {
            if (initialized) return available;
            try {
                Class<?> companionType = Class.forName(COMPANION_CLASS, false, getClass().getClassLoader());
                Field instanceField = companionType.getField("INSTANCE");
                companion = instanceField.get(null);
                if (companion == null) throw new IllegalStateException("SableCompanion.INSTANCE is null");

                getContaining = findMethod(companionType, "getContaining", Level.class, Vec3.class);
                projectOutOfSubLevel = findMethod(companionType, "projectOutOfSubLevel", Level.class, Vec3.class);
                isInPlotGrid = findMethod(companionType, "isInPlotGrid", Level.class, Vec3.class);
                available = getContaining != null || projectOutOfSubLevel != null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                available = false;
            } finally {
                initialized = true;
            }
        }
        return available;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
}
