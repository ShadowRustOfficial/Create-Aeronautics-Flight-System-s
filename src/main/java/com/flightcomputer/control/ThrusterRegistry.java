package com.flightcomputer.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/** Per-controller runtime actuator snapshot. No global aircraft state is stored here. */
public final class ThrusterRegistry {
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
        refreshBank(level, controllerPos, FlightMode.CRUISE, autopilotLinks);
    }

    private void refreshBank(Level level, BlockPos controllerPos, FlightMode mode, Map<VectorDirection, BlockPos> assignments) {
        Map<VectorDirection, List<ThrusterLink>> bank = links.get(mode);
        for (List<ThrusterLink> value : bank.values()) value.clear();
        if (assignments == null) return;
        for (Map.Entry<VectorDirection, BlockPos> entry : assignments.entrySet()) {
            BlockPos target = entry.getValue();
            if (target == null) continue;
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity == null) continue;
            double[] offset = {
                    target.getX() + 0.5D - (controllerPos.getX() + 0.5D),
                    target.getY() + 0.5D - (controllerPos.getY() + 0.5D),
                    target.getZ() + 0.5D - (controllerPos.getZ() + 0.5D)
            };
            PropulsionSource source = ReflectivePropulsionSource.tryCreate(blockEntity, entry.getKey(), offset);
            if (source != null) bank.get(entry.getKey()).add(new ThrusterLink(source, entry.getKey(), mode));
        }
    }

    public void link(ThrusterLink link) {
        List<ThrusterLink> list = links.get(link.mode).get(link.direction);
        list.removeIf(existing -> existing.source.getId().equals(link.source.getId()));
        list.add(link);
    }

    public List<ThrusterLink> getLinks(FlightMode mode, VectorDirection direction) {
        return Collections.unmodifiableList(links.get(mode).get(direction));
    }

    /** Compatibility view for older controller code; physical allocation is vector-based. */
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
        double total = 0.0D;
        for (ThrusterLink link : links.get(mode).get(direction)) total += Math.max(0.0D, link.source.getAvailableThrust());
        return total;
    }

    public double getAxisAuthority(FlightMode mode, ControlAxis axis) {
        double total = 0.0D;
        for (ThrusterLink link : getLinks(mode, axis)) total += Math.max(0.0D, link.source.getAvailableThrust());
        return total;
    }

    public boolean hasAnyVector(FlightMode mode, VectorDirection direction) { return getVectorAuthority(mode, direction) > 0.0D; }

    public List<ControlAxis> getUnlinkedAxes(FlightMode mode) {
        List<ControlAxis> missing = new ArrayList<>();
        for (ControlAxis axis : ControlAxis.values()) if (getAxisAuthority(mode, axis) <= 0.0D) missing.add(axis);
        return missing;
    }
}
