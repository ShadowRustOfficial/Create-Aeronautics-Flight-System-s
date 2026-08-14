package com.flightcomputer.control;

import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/** Runtime actuator registry. */
public final class ThrusterRegistry {
    private static final int DISCOVERY_RADIUS = 24;
    private static final long MAX_SABLE_SCAN_VOLUME = 8_000_000L;
    private final Map<FlightMode, Map<VectorDirection, List<ThrusterLink>>> links = new EnumMap<>(FlightMode.class);
    private long lastRefreshTick = Long.MIN_VALUE;
    private Object lastSubLevel;

    public ThrusterRegistry() {
        for (FlightMode mode : FlightMode.values()) {
            Map<VectorDirection, List<ThrusterLink>> vectors = new EnumMap<>(VectorDirection.class);
            for (VectorDirection direction : VectorDirection.values()) vectors.put(direction, new ArrayList<>());
            links.put(mode, vectors);
        }
    }

    public void refresh(Level level, BlockPos controllerPos, Map<VectorDirection, BlockPos> stabiliserLinks,
                        Map<VectorDirection, BlockPos> autopilotLinks, long gameTime) {
        refresh(level, controllerPos, stabiliserLinks, autopilotLinks, gameTime, null);
    }

    public void refresh(Level level, BlockPos controllerPos, Map<VectorDirection, BlockPos> stabiliserLinks,
                        Map<VectorDirection, BlockPos> autopilotLinks, long gameTime, Object subLevel) {
        if (level == null || controllerPos == null
                || (gameTime == lastRefreshTick && Objects.equals(subLevel, lastSubLevel))) return;

        if (subLevel == null) subLevel = resolveSubLevel(level, controllerPos);
        lastRefreshTick = gameTime;
        lastSubLevel = subLevel;

        refreshBank(level, controllerPos, FlightMode.STABILIZE, stabiliserLinks, subLevel);
        Map<VectorDirection, BlockPos> effective = new EnumMap<>(VectorDirection.class);
        if (stabiliserLinks != null) effective.putAll(stabiliserLinks);
        if (autopilotLinks != null) effective.putAll(autopilotLinks);
        refreshBank(level, controllerPos, FlightMode.CRUISE, effective, subLevel);
    }

    private void refreshBank(Level level, BlockPos controllerPos, FlightMode mode,
                             Map<VectorDirection, BlockPos> assignments, Object subLevel) {
        Map<VectorDirection, List<ThrusterLink>> bank = links.get(mode);
        for (List<ThrusterLink> value : bank.values()) value.clear();

        if (assignments != null && !assignments.isEmpty()) {
            for (Map.Entry<VectorDirection, BlockPos> entry : assignments.entrySet()) {
                addExplicit(level, controllerPos, mode, entry.getKey(), entry.getValue(), bank, subLevel);
            }
        }

        // A Sable vessel stores its blocks in the plot grid. The entire local plot bounding box
        // must be searched; a normal-world radius scan cannot see thrusters on a large vessel.
        if (subLevel != null && scanSablePlot(controllerPos, mode, bank, subLevel)) return;
        scanOrdinaryLevel(level, controllerPos, mode, bank);
    }

    private boolean scanSablePlot(BlockPos controllerPos, FlightMode mode,
                                   Map<VectorDirection, List<ThrusterLink>> bank, Object subLevel) {
        try {
            Object plot = invokeNoArg(subLevel, "getPlot");
            if (plot == null) return false;

            Object accessor = invokeNoArg(plot, "getEmbeddedLevelAccessor");
            if (!(accessor instanceof LevelAccessor levelAccessor)) return false;

            Object bounds = invokeNoArg(plot, "getBoundingBox");
            Integer minX = invokeInt(bounds, "minX"), minY = invokeInt(bounds, "minY"), minZ = invokeInt(bounds, "minZ");
            Integer maxX = invokeInt(bounds, "maxX"), maxY = invokeInt(bounds, "maxY"), maxZ = invokeInt(bounds, "maxZ");
            if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) return false;
            if (minX > maxX || minY > maxY || minZ > maxZ) return true;

            long volume = ((long) maxX - minX + 1L) * ((long) maxY - minY + 1L) * ((long) maxZ - minZ + 1L);
            if (volume <= 0L || volume > MAX_SABLE_SCAN_VOLUME) return false;

            for (BlockPos scanPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                BlockEntity blockEntity = levelAccessor.getBlockEntity(scanPos);
                if (blockEntity == null) continue;

                double[] offset = mountOffset(controllerPos, scanPos);
                discoverCompatible(blockEntity, offset, mode, bank);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void scanOrdinaryLevel(Level level, BlockPos controllerPos, FlightMode mode,
                                   Map<VectorDirection, List<ThrusterLink>> bank) {
        BlockPos min = controllerPos.offset(-DISCOVERY_RADIUS, -DISCOVERY_RADIUS, -DISCOVERY_RADIUS);
        BlockPos max = controllerPos.offset(DISCOVERY_RADIUS, DISCOVERY_RADIUS, DISCOVERY_RADIUS);
        for (BlockPos scanPos : BlockPos.betweenClosed(min, max)) {
            if (scanPos.equals(controllerPos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(scanPos);
            if (blockEntity != null) discoverCompatible(blockEntity, mountOffset(controllerPos, scanPos), mode, bank);
        }
    }

    private static void discoverCompatible(BlockEntity blockEntity, double[] offset, FlightMode mode,
                                           Map<VectorDirection, List<ThrusterLink>> bank) {
        for (VectorDirection direction : VectorDirection.values()) {
            PropulsionSource source = ReflectivePropulsionSource.tryCreate(blockEntity, direction, offset);
            if (!(source instanceof ReflectivePropulsionSource reflective)) continue;

            VectorDirection physicalDirection = reflective.getPhysicalDirection();
            if (physicalDirection == null || physicalDirection != direction) continue;
            addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
        }
    }

    private void addExplicit(Level level, BlockPos controllerPos, FlightMode mode, VectorDirection direction,
                             BlockPos storedTarget, Map<VectorDirection, List<ThrusterLink>> bank, Object subLevel) {
        if (storedTarget == null || direction == null) return;

        BlockEntity direct = level.getBlockEntity(storedTarget);
        PropulsionSource directSource = direct == null ? null
                : ReflectivePropulsionSource.tryCreate(direct, direction, mountOffset(controllerPos, storedTarget));
        if (directSource != null) {
            addIfUnique(bank.get(direction), new ThrusterLink(directSource, direction, mode));
            return;
        }

        if (subLevel != null) {
            BlockEntity subLevelEntity = getSubLevelBlockEntity(subLevel, storedTarget);
            if (subLevelEntity != null) {
                PropulsionSource source = ReflectivePropulsionSource.tryCreate(subLevelEntity, direction,
                        mountOffset(controllerPos, storedTarget));
                if (source != null) {
                    addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
                    return;
                }
            }
        }

        BlockPos localTarget = controllerPos.offset(storedTarget);
        BlockEntity local = level.getBlockEntity(localTarget);
        PropulsionSource localSource = ReflectivePropulsionSource.tryCreate(local, direction,
                mountOffset(controllerPos, localTarget));
        if (localSource != null) addIfUnique(bank.get(direction), new ThrusterLink(localSource, direction, mode));
    }

    private static Object resolveSubLevel(Level level, BlockPos controllerPos) {
        try {
            BlockEntity be = level.getBlockEntity(controllerPos);
            if (!(be instanceof FlightControllerBlockEntity controller)) return null;

            Class<?> sable = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false,
                    ThrusterRegistry.class.getClassLoader());
            Object instance = sable.getField("INSTANCE").get(null);
            if (instance == null) return null;

            try {
                return instance.getClass().getMethod("getContaining", BlockEntity.class).invoke(instance, controller);
            } catch (NoSuchMethodException ignored) { }

            try {
                return instance.getClass().getMethod("getContaining", Level.class, Vec3.class)
                        .invoke(instance, level, Vec3.atCenterOf(controllerPos));
            } catch (NoSuchMethodException ignored) { }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return null;
    }

    private static BlockEntity getSubLevelBlockEntity(Object subLevel, BlockPos pos) {
        try {
            Object plot = invokeNoArg(subLevel, "getPlot");
            Object accessor = invokeNoArg(plot, "getEmbeddedLevelAccessor");
            if (accessor instanceof LevelAccessor levelAccessor) return levelAccessor.getBlockEntity(pos);
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static Method findNoArg(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            return method.getParameterCount() == 0 ? method : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        Method method = findNoArg(target.getClass(), name);
        if (method == null) return null;
        try { return method.invoke(target); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Integer invokeInt(Object target, String name) {
        Object value = invokeNoArg(target, name);
        return value instanceof Number n ? n.intValue() : null;
    }

    private static double[] mountOffset(BlockPos controllerPos, BlockPos target) {
        return new double[]{
                target.getX() + 0.5D - (controllerPos.getX() + 0.5D),
                target.getY() + 0.5D - (controllerPos.getY() + 0.5D),
                target.getZ() + 0.5D - (controllerPos.getZ() + 0.5D)
        };
    }

    private static void addIfUnique(List<ThrusterLink> list, ThrusterLink link) {
        if (list == null || link == null || link.source == null) return;
        String id = link.source.getId();
        for (ThrusterLink existing : list) if (existing.source.getId().equals(id)) return;
        list.add(link);
    }

    public void link(ThrusterLink link) {
        if (link != null) addIfUnique(links.get(link.mode).get(link.direction), link);
    }

    public List<ThrusterLink> getLinks(FlightMode mode, VectorDirection direction) {
        return Collections.unmodifiableList(links.get(mode).get(direction));
    }

    public List<ThrusterLink> getAllLinks(FlightMode mode) {
        LinkedHashMap<String, ThrusterLink> unique = new LinkedHashMap<>();
        for (VectorDirection direction : VectorDirection.values()) {
            for (ThrusterLink link : links.get(mode).get(direction)) unique.putIfAbsent(link.source.getId(), link);
        }
        return List.copyOf(unique.values());
    }

    public List<ThrusterLink> getLinks(FlightMode mode, ControlAxis axis) {
        List<ThrusterLink> result = new ArrayList<>();
        for (VectorDirection direction : VectorDirection.values()) {
            if (axis == ControlAxis.VERTICAL && direction != VectorDirection.UP && direction != VectorDirection.DOWN) continue;
            if (axis == ControlAxis.LONGITUDINAL && direction != VectorDirection.NORTH && direction != VectorDirection.SOUTH) continue;
            if (axis == ControlAxis.LATERAL && direction != VectorDirection.EAST && direction != VectorDirection.WEST) continue;
            if (axis.isRotational() || axis.isTranslational()) result.addAll(links.get(mode).get(direction));
        }
        return Collections.unmodifiableList(result);
    }

    public List<ThrusterLink> getAllLinks() {
        LinkedHashMap<String, ThrusterLink> unique = new LinkedHashMap<>();
        for (FlightMode mode : FlightMode.values()) {
            for (VectorDirection direction : VectorDirection.values()) {
                for (ThrusterLink link : links.get(mode).get(direction)) unique.putIfAbsent(link.source.getId(), link);
            }
        }
        return List.copyOf(unique.values());
    }

    public double getVectorAuthority(FlightMode mode, VectorDirection direction) {
        double total = 0;
        for (ThrusterLink link : links.get(mode).get(direction)) total += Math.max(0, link.source.getAvailableThrust());
        return total;
    }

    public int getVectorThrusterCount(FlightMode mode, VectorDirection direction) {
        return links.get(mode).get(direction).size();
    }

    public double getAxisAuthority(FlightMode mode, ControlAxis axis) {
        double total = 0;
        for (ThrusterLink link : getLinks(mode, axis)) total += Math.max(0, link.source.getAvailableThrust());
        return total;
    }

    public boolean hasAnyVector(FlightMode mode, VectorDirection direction) {
        return getVectorAuthority(mode, direction) > 0;
    }

    public List<ControlAxis> getUnlinkedAxes(FlightMode mode) {
        List<ControlAxis> missing = new ArrayList<>();
        for (ControlAxis axis : ControlAxis.values()) if (getAxisAuthority(mode, axis) <= 0) missing.add(axis);
        return missing;
    }
}
