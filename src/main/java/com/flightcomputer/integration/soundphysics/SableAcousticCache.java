package com.flightcomputer.integration.soundphysics;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main-thread Sable acoustic snapshot cache.
 *
 * It deliberately performs all Sable/level access during the client tick and exposes only
 * prepared acoustic results to the optional Sound Physics mixin. This avoids touching
 * Minecraft's client world or Sable plot data from Sound Physics' processing path.
 */
public final class SableAcousticCache {
    private static final int MAX_SOURCES = 12;
    private static final long SOURCE_TTL_TICKS = 4L;
    private static final double SOURCE_MATCH_DISTANCE_SQ = 1.0D;
    private static final double MAX_EXTRA_OCCLUSION = 4.0D;

    private static final Map<Integer, SourceRegistration> SOURCES = new LinkedHashMap<>();
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static long tick;

    private static volatile boolean sableInit;
    private static volatile Object sableHelper;
    private static volatile Method containingPosition;
    private static volatile Method containingEntity;
    private static volatile Method logicalPose;
    private static volatile Method getPlot;
    private static volatile Constructor<?> embeddedAccessorConstructor;
    private static volatile Method transformInverse;

    private SableAcousticCache() { }

    public static void registerSource(Vec3 worldPosition) {
        if (worldPosition == null || !finite(worldPosition)) return;
        int key = quantizedKey(worldPosition);
        SourceRegistration existing = SOURCES.get(key);
        if (existing != null) {
            existing.position = worldPosition;
            existing.lastSeenTick = tick;
            return;
        }
        if (SOURCES.size() >= MAX_SOURCES) {
            Iterator<Map.Entry<Integer, SourceRegistration>> it = SOURCES.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        SOURCES.put(key, new SourceRegistration(worldPosition, tick));
    }

    /** Main-thread only. Refreshes the acoustic result cache. */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            SOURCES.clear();
            snapshot = Snapshot.EMPTY;
            return;
        }
        tick++;
        initSableApi();
        pruneSources();
        if (!isSableReady()) {
            snapshot = Snapshot.EMPTY;
            return;
        }

        Vec3 listener = minecraft.player.getEyePosition();
        List<Entry> entries = new ArrayList<>();
        for (SourceRegistration registration : SOURCES.values()) {
            try {
                double occlusion = computeOcclusion(minecraft, registration.position, listener);
                entries.add(new Entry(registration.position,
                        Math.min(MAX_EXTRA_OCCLUSION, Math.max(0.0D, occlusion))));
            } catch (Throwable ignored) {
                // Acoustic compatibility is optional. Never allow an audio query to affect gameplay.
            }
        }
        snapshot = new Snapshot(List.copyOf(entries), listener);
    }

    /** Audio-thread safe lookup. Returns zero when no prepared Sable result is nearby. */
    public static double cachedOcclusion(Vec3 soundPosition, Vec3 listenerPosition) {
        Snapshot current = snapshot;
        if (current.entries.isEmpty() || soundPosition == null || listenerPosition == null) return 0.0D;
        if (current.listener.distanceToSqr(listenerPosition) > 9.0D) return 0.0D;

        double bestDistance = Double.MAX_VALUE;
        double bestOcclusion = 0.0D;
        for (Entry entry : current.entries) {
            double distance = entry.source.distanceToSqr(soundPosition);
            if (distance < SOURCE_MATCH_DISTANCE_SQ && distance < bestDistance) {
                bestDistance = distance;
                bestOcclusion = entry.occlusion;
            }
        }
        return bestOcclusion;
    }

    public static boolean isPreparedForSable() {
        return snapshot != Snapshot.EMPTY;
    }

    private static void pruneSources() {
        SOURCES.values().removeIf(source -> tick - source.lastSeenTick > SOURCE_TTL_TICKS);
    }

    private static double computeOcclusion(Minecraft minecraft, Vec3 sourceWorld, Vec3 listenerWorld) throws Exception {
        Object sourceSubLevel = containingPosition.invoke(sableHelper, minecraft.level, sourceWorld);
        Object listenerSubLevel = containingEntity.invoke(sableHelper, minecraft.player);

        Map<UUID, Object> spaces = new LinkedHashMap<>();
        addSpace(spaces, sourceSubLevel);
        addSpace(spaces, listenerSubLevel);

        double total = 0.0D;
        for (Object subLevel : spaces.values()) {
            Object pose = logicalPose.invoke(subLevel);
            Vec3 localFrom = (Vec3) transformInverse.invoke(pose, sourceWorld);
            Vec3 localTo = (Vec3) transformInverse.invoke(pose, listenerWorld);
            Object plot = getPlot.invoke(subLevel);
            Object accessor = embeddedAccessorConstructor.newInstance(plot);
            BlockGetter getter = (BlockGetter) accessor;
            total += rayOcclusion(getter, localFrom, localTo);
            if (total >= MAX_EXTRA_OCCLUSION) return MAX_EXTRA_OCCLUSION;
        }
        return total;
    }

    private static void addSpace(Map<UUID, Object> spaces, Object subLevel) {
        if (subLevel == null) return;
        try {
            Method idMethod = subLevel.getClass().getMethod("getUniqueId");
            UUID id = (UUID) idMethod.invoke(subLevel);
            if (id != null) spaces.putIfAbsent(id, subLevel);
        } catch (Throwable ignored) { }
    }

    private static double rayOcclusion(BlockGetter getter, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 1.0E-9D) return 0.0D;
        Vec3 start = from;
        double total = 0.0D;
        BlockPos last = null;
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit;
            try {
                hit = getter.clip(new ClipContext(start, to,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        (Entity) null));
            } catch (Throwable ignored) {
                break;
            }
            if (hit.getType() != HitResult.Type.BLOCK) break;
            BlockPos pos = hit.getBlockPos();
            if (pos.equals(last)) {
                Vec3 direction = to.subtract(start).normalize();
                if (!finite(direction)) break;
                start = hit.getLocation().add(direction.scale(0.01D));
                continue;
            }
            last = pos;
            boolean full = getter.getBlockState(pos).isCollisionShapeFullBlock(getter, pos);
            total += full ? 0.75D : 0.25D;
            if (total >= MAX_EXTRA_OCCLUSION) break;
            Vec3 direction = to.subtract(hit.getLocation()).normalize();
            if (!finite(direction)) break;
            start = hit.getLocation().add(direction.scale(0.01D));
            if (start.distanceToSqr(to) < 1.0E-6D) break;
        }
        return total;
    }

    private static void initSableApi() {
        if (sableInit) return;
        synchronized (SableAcousticCache.class) {
            if (sableInit) return;
            try {
                if (!net.neoforged.fml.ModList.get().isLoaded("sable")) {
                    sableInit = true;
                    return;
                }

                ClassLoader loader = SableAcousticCache.class.getClassLoader();
                Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", false, loader);
                Field helperField = sable.getField("HELPER");
                sableHelper = helperField.get(null);
                Class<?> helper = sableHelper.getClass();
                containingPosition = helper.getMethod("getContaining", net.minecraft.world.level.Level.class, net.minecraft.core.Position.class);
                containingEntity = helper.getMethod("getContaining", net.minecraft.world.entity.Entity.class);

                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", false, loader);
                logicalPose = subLevelClass.getMethod("logicalPose");
                getPlot = subLevelClass.getMethod("getPlot");

                Class<?> levelPlotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot", false, loader);
                Class<?> accessorClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor", false, loader);
                embeddedAccessorConstructor = accessorClass.getConstructor(levelPlotClass);

                Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d", false, loader);
                transformInverse = poseClass.getMethod("transformPositionInverse", Vec3.class);
            } catch (Throwable ignored) {
                sableHelper = null;
                containingPosition = null;
                containingEntity = null;
                logicalPose = null;
                getPlot = null;
                embeddedAccessorConstructor = null;
                transformInverse = null;
            } finally {
                sableInit = true;
            }
        }
    }

    private static boolean isSableReady() {
        return sableHelper != null && containingPosition != null && containingEntity != null
                && logicalPose != null && getPlot != null && embeddedAccessorConstructor != null
                && transformInverse != null;
    }

    private static int quantizedKey(Vec3 p) {
        int x = (int) Math.floor(p.x * 2.0D);
        int y = (int) Math.floor(p.y * 2.0D);
        int z = (int) Math.floor(p.z * 2.0D);
        int hash = 31 * x + y;
        return 31 * hash + z;
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static final class SourceRegistration {
        private Vec3 position;
        private long lastSeenTick;

        private SourceRegistration(Vec3 position, long lastSeenTick) {
            this.position = position;
            this.lastSeenTick = lastSeenTick;
        }
    }

    private record Entry(Vec3 source, double occlusion) { }

    private record Snapshot(List<Entry> entries, Vec3 listener) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), Vec3.ZERO);
    }
}
