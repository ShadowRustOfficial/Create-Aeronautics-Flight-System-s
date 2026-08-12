package com.flightcomputer.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/** Runtime actuator registry. Links may be persisted as controller-local offsets or legacy absolute positions. */
public final class ThrusterRegistry {
    private static final int DISCOVERY_RADIUS = 24;
    private final Map<FlightMode, Map<VectorDirection, List<ThrusterLink>>> links = new EnumMap<>(FlightMode.class);
    private long lastRefreshTick = Long.MIN_VALUE;

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
        if (level == null || controllerPos == null || gameTime == lastRefreshTick) return;
        lastRefreshTick = gameTime;
        refreshBank(level, controllerPos, FlightMode.STABILIZE, stabiliserLinks);

        // Autopilot does not require a second physical set of thrusters. If a direction has not
        // explicitly been assigned to the CRUISE bank, inherit that direction from the existing
        // stabiliser bank. This preserves explicit CRUISE assignments while allowing a vessel
        // linked once through the Flight Link Tool to be controlled by both systems.
        Map<VectorDirection, BlockPos> effectiveAutopilotLinks = new EnumMap<>(VectorDirection.class);
        if (stabiliserLinks != null) effectiveAutopilotLinks.putAll(stabiliserLinks);
        if (autopilotLinks != null) effectiveAutopilotLinks.putAll(autopilotLinks);
        refreshBank(level, controllerPos, FlightMode.CRUISE, effectiveAutopilotLinks);
    }

    private void refreshBank(Level level, BlockPos controllerPos, FlightMode mode,
                             Map<VectorDirection, BlockPos> assignments) {
        Map<VectorDirection, List<ThrusterLink>> bank = links.get(mode);
        for (List<ThrusterLink> value : bank.values()) value.clear();
        if (assignments == null || assignments.isEmpty()) return;

        for (Map.Entry<VectorDirection, BlockPos> entry : assignments.entrySet())
            addExplicit(level, controllerPos, mode, entry.getKey(), entry.getValue(), bank);

        BlockPos min = controllerPos.offset(-DISCOVERY_RADIUS, -DISCOVERY_RADIUS, -DISCOVERY_RADIUS);
        BlockPos max = controllerPos.offset(DISCOVERY_RADIUS, DISCOVERY_RADIUS, DISCOVERY_RADIUS);
        for (BlockPos scanPos : BlockPos.betweenClosed(min, max)) {
            if (scanPos.equals(controllerPos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(scanPos);
            if (blockEntity == null) continue;
            double[] offset = mountOffset(controllerPos, scanPos);
            for (VectorDirection direction : VectorDirection.values()) {
                if (!assignments.containsKey(direction)) continue;
                PropulsionSource source = ReflectivePropulsionSource.tryCreate(blockEntity, direction, offset);
                if (!(source instanceof ReflectivePropulsionSource reflective)) continue;
                // The direction is the thruster's local vehicle direction. ThrustAllocator applies
                // the Sable vehicle rotation once, so we must not pre-rotate it here.
                if (reflective.getPhysicalDirection() != direction) continue;
                addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
            }
        }
    }

    private void addExplicit(Level level, BlockPos controllerPos, FlightMode mode,
                             VectorDirection direction, BlockPos storedTarget,
                             Map<VectorDirection, List<ThrusterLink>> bank) {
        if (storedTarget == null || direction == null) return;

        // The link tool currently sends/stores the actual clicked BlockPos, while older patches
        // described the value as a controller-local offset. Resolve both forms so existing saved
        // controllers keep working and newly linked thrusters are not double-offset into nowhere.
        BlockPos absoluteTarget = storedTarget;
        BlockEntity direct = level.getBlockEntity(absoluteTarget);
        PropulsionSource directSource = direct == null ? null
                : ReflectivePropulsionSource.tryCreate(direct, direction, mountOffset(controllerPos, absoluteTarget));
        if (directSource != null) {
            addIfUnique(bank.get(direction), new ThrusterLink(directSource, direction, mode));
            return;
        }

        BlockPos localTarget = controllerPos.offset(storedTarget);
        BlockEntity local = level.getBlockEntity(localTarget);
        if (local == null) return;
        PropulsionSource localSource = ReflectivePropulsionSource.tryCreate(local, direction, mountOffset(controllerPos, localTarget));
        if (localSource != null) addIfUnique(bank.get(direction), new ThrusterLink(localSource, direction, mode));
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
