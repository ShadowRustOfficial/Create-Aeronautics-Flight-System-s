package com.flightcomputer.client.map;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves Flight Computer positions into logical world coordinates.
 *
 * <p>The controller BlockEntity can remain at Sable's internal plot/storage coordinate while the
 * vessel is assembled or moving. For the Flight Computer itself, authoritative server telemetry
 * is therefore the primary position source. Sable Companion projection remains the fallback for
 * ordinary block/entity coordinates and keeps this class optional when Sable is absent.</p>
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

        // The Flight Computer's authoritative telemetry is already expressed in logical world
        // coordinates. Prefer it over the BlockEntity's raw storage/plot coordinate.
        if (level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(localPosition);
            if (blockEntity instanceof FlightControllerBlockEntity controller) {
                var telemetry = FlightComputerTelemetryClient.get(controller.getControllerId());
                if (telemetry != null) {
                    return new Vec3(telemetry.x(), telemetry.y(), telemetry.z());
                }
            }
        }

        return resolve(level, center(localPosition));
    }

    /** Precise coordinate overload for entities/players; never quantizes to BlockPos. */
    public Vec3 resolve(Level level, Vec3 position) {
        if (level == null || position == null) return null;
        if (!ensureInitialized()) return position;
        try {
            if (projectOutOfSubLevel != null) {
                Object projected = projectOutOfSubLevel.invoke(companion, level, position);
                if (projected instanceof Vec3 world) return world;
            }

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

    public boolean isSableAvailable() {
        ensureInitialized();
        return available;
    }

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
