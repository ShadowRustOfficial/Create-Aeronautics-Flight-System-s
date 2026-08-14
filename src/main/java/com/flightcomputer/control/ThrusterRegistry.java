package com.flightcomputer.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Runtime actuator registry. Links may be persisted as controller-local offsets or legacy absolute positions.
 *
 * <p>On Sable vessels the physical actuator scan is performed against the vessel's embedded plot rather
 * than the ordinary Minecraft level. This is important for large sub-levels: the controller and its
 * thrusters may be many blocks apart in the vessel coordinate space while still belonging to the same
 * physical vehicle.</p>
 */
public final class ThrusterRegistry {
    private static final int DISCOVERY_RADIUS = 24;
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

    public void refresh(Level level, BlockPos controllerPos,
                        Map<VectorDirection, BlockPos> stabiliserLinks,
                        Map<VectorDirection, BlockPos> autopilotLinks,
                        long gameTime) {
        refresh(level, controllerPos, stabiliserLinks, autopilotLinks, gameTime, null);
    }

    public void refresh(Level level, BlockPos controllerPos,
                        Map<VectorDirection, BlockPos> stabiliserLinks,
                        Map<VectorDirection, BlockPos> autopilotLinks,
                        long gameTime,
                        Object subLevel) {
        if (level == null || controllerPos == null || gameTime == lastRefreshTick && Objects.equals(subLevel, lastSubLevel)) return;
        lastRefreshTick = gameTime;
        lastSubLevel = subLevel;
        refreshBank(level, controllerPos, FlightMode.STABILIZE, stabiliserLinks, subLevel);

        Map<VectorDirection, BlockPos> effectiveAutopilotLinks = new EnumMap<>(VectorDirection.class);
        if (stabiliserLinks != null) effectiveAutopilotLinks.putAll(stabiliserLinks);
        if (autopilotLinks != null) effectiveAutopilotLinks.putAll(autopilotLinks);
        refreshBank(level, controllerPos, FlightMode.CRUISE, effectiveAutopilotLinks, subLevel);
    }

    private void refreshBank(Level level, BlockPos controllerPos, FlightMode mode,
                             Map<VectorDirection, BlockPos> assignments, Object subLevel) {
        Map<VectorDirection, List<ThrusterLink>> bank = links.get(mode);
        for (List<ThrusterLink> value : bank.values()) value.clear();

        boolean explicitLinks = assignments != null && !assignments.isEmpty();
        if (explicitLinks) {
            for (Map.Entry<VectorDirection, BlockPos> entry : assignments.entrySet())
                addExplicit(level, controllerPos, mode, entry.getKey(), entry.getValue(), bank, subLevel);
        }

        if (subLevel != null && scanSablePlot(level, controllerPos, mode, bank, subLevel)) return;
        scanOrdinaryLevel(level, controllerPos, mode, bank);
    }

    /**
     * Scan the authoritative Sable embedded plot when it is available. The accessor is a BlockGetter,
     * so the existing propulsion adapter can keep working entirely on block entities/block positions.
     */
    private boolean scanSablePlot(Level level, BlockPos controllerPos, FlightMode mode,
                                  Map<VectorDirection, List<ThrusterLink>> bank, Object subLevel) {
        try {
            Method getPlot = findNoArg(subLevel.getClass(), "getPlot");
            if (getPlot == null) return false;

            Object plot = getPlot.invoke(subLevel);
            if (plot == null) return false;
            ClassLoader loader = getClass().getClassLoader();
            Class<?> levelPlotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot", false, loader);
            Class<?> accessorClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor", false, loader);
            Constructor<?> ctor = accessorClass.getConstructor(levelPlotClass);
            Object accessor = ctor.newInstance(plot);
            if (!(accessor instanceof BlockGetter getter)) return false;

            BlockPos min = null;
            BlockPos max = null;
            for (String[] pair : new String[][]{
                    {"getMinPos", "getMaxPos"},
                    {"minBlock", "maxBlock"},
                    {"getMin", "getMax"}
            }) {
                Object minObj = invokeNoArg(plot, pair[0]);
                Object maxObj = invokeNoArg(plot, pair[1]);
                if (minObj instanceof BlockPos a && maxObj instanceof BlockPos b) {
                    min = a;
                    max = b;
                    break;
                }
            }

            if (min == null || max == null) {
                min = controllerPos.offset(-DISCOVERY_RADIUS, -DISCOVERY_RADIUS, -DISCOVERY_RADIUS);
                max = controllerPos.offset(DISCOVERY_RADIUS, DISCOVERY_RADIUS, DISCOVERY_RADIUS);
            }

            long volume = ((long) max.getX() - min.getX() + 1L)
                    * ((long) max.getY() - min.getY() + 1L)
                    * ((long) max.getZ() - min.getZ() + 1L);
            if (volume <= 0L || volume > 2_000_000L) return false;

            for (BlockPos scanPos : BlockPos.betweenClosed(min, max)) {
                BlockEntity blockEntity = getBlockEntity(getter, scanPos);
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
            if (blockEntity == null) continue;
            double[] offset = mountOffset(controllerPos, scanPos);
            discoverCompatible(blockEntity, offset, mode, bank);
        }
    }

    private static void discoverCompatible(BlockEntity blockEntity, double[] offset, FlightMode mode,
                                           Map<VectorDirection, List<ThrusterLink>> bank) {
        for (VectorDirection direction : VectorDirection.values()) {
            PropulsionSource source = ReflectivePropulsionSource.tryCreate(blockEntity, direction, offset);
            if (!(source instanceof ReflectivePropulsionSource reflective)) continue;
            if (reflective.getPhysicalDirection() != direction) continue;
            addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
        }
    }

    private void addExplicit(Level level, BlockPos controllerPos, FlightMode mode,
                             VectorDirection direction, BlockPos storedTarget,
                             Map<VectorDirection, List<ThrusterLink>> bank, Object subLevel) {
        if (storedTarget == null || direction == null) return;
        BlockPos absoluteTarget = storedTarget;
        BlockEntity direct = level.getBlockEntity(absoluteTarget);
        PropulsionSource directSource = direct == null ? null
                : ReflectivePropulsionSource.tryCreate(direct, direction, mountOffset(controllerPos, absoluteTarget));
        if (directSource != null) {
            addIfUnique(bank.get(direction), new ThrusterLink(directSource, direction, mode));
            return;
        }

        if (subLevel != null) {
            BlockEntity subLevelEntity = getSubLevelBlockEntity(subLevel, storedTarget);
            if (subLevelEntity != null) {
                PropulsionSource source = ReflectivePropulsionSource.tryCreate(subLevelEntity, direction,
                        new double[]{storedTarget.getX(), storedTarget.getY(), storedTarget.getZ()});
                if (source != null) {
                    addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
                    return;
                }
            }
        }

        BlockPos localTarget = controllerPos.offset(storedTarget);
        BlockEntity local = level.getBlockEntity(localTarget);
        PropulsionSource localSource = ReflectivePropulsionSource.tryCreate(local, direction, mountOffset(controllerPos, localTarget));
        if (localSource != null) addIfUnique(bank.get(direction), new ThrusterLink(localSource, direction, mode));
    }

    private static BlockEntity getSubLevelBlockEntity(Object subLevel, BlockPos pos) {
        try {
            Method getPlot = findNoArg(subLevel.getClass(), "getPlot");
            if (getPlot == null) return null;
            Object plot = getPlot.invoke(subLevel);
            if (plot == null) return null;
            ClassLoader loader = ThrusterRegistry.class.getClassLoader();
            Class<?> levelPlotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot", false, loader);
            Class<?> accessorClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor", false, loader);
            Constructor<?> ctor = accessorClass.getConstructor(levelPlotClass);
            Object accessor = ctor.newInstance(plot);
            return accessor instanceof BlockGetter getter ? getBlockEntity(getter, pos) : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static BlockEntity getBlockEntity(BlockGetter getter, BlockPos pos) {
        try {
            Method method = getter.getClass().getMethod("getBlockEntity", BlockPos.class);
            Object value = method.invoke(getter, pos);
            return value instanceof BlockEntity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
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

    public void link(ThrusterLink link) { if (link != null) addIfUnique(links.get(link.mode).get(link.direction), link); }
    public List<ThrusterLink> getLinks(FlightMode mode, VectorDirection direction) { return Collections.unmodifiableList(links.get(mode).get(direction)); }
    public List<ThrusterLink> getAllLinks(FlightMode mode) {
        LinkedHashMap<String, ThrusterLink> unique = new LinkedHashMap<>();
        for (VectorDirection direction : VectorDirection.values())
            for (ThrusterLink link : links.get(mode).get(direction)) unique.putIfAbsent(link.source.getId(), link);
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
        for (FlightMode mode : FlightMode.values())
            for (VectorDirection direction : VectorDirection.values())
                for (ThrusterLink link : links.get(mode).get(direction)) unique.putIfAbsent(link.source.getId(), link);
        return List.copyOf(unique.values());
    }
    public double getVectorAuthority(FlightMode mode, VectorDirection direction) {
        double total = 0;
        for (ThrusterLink link : links.get(mode).get(direction)) total += Math.max(0, link.source.getAvailableThrust());
        return total;
    }
    public int getVectorThrusterCount(FlightMode mode, VectorDirection direction) { return links.get(mode).get(direction).size(); }
    public double getAxisAuthority(FlightMode mode, ControlAxis axis) { double total=0;for(ThrusterLink link:getLinks(mode,axis))total+=Math.max(0,link.source.getAvailableThrust());return total; }
    public boolean hasAnyVector(FlightMode mode, VectorDirection direction) { return getVectorAuthority(mode,direction)>0; }
    public List<ControlAxis> getUnlinkedAxes(FlightMode mode) { List<ControlAxis> missing=new ArrayList<>();for(ControlAxis axis:ControlAxis.values())if(getAxisAuthority(mode,axis)<=0)missing.add(axis);return missing; }
}
