package com.flightcomputer.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * Per-controller runtime actuator snapshot.
 *
 * <p>A vector link is now a bank seed rather than a one-thruster limit. Once a user
 * links one real thruster for a vector, compatible thrusters in the same controller
 * area are discovered from their physical facing and added to that vector. Every
 * discovered source keeps its own mount offset, allowing the allocator to balance
 * force and torque instead of treating a whole front as one actuator.</p>
 */
public final class ThrusterRegistry {
    /** Search radius for additional physical thrusters belonging to a linked bank. */
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
        refreshBank(level, controllerPos, FlightMode.CRUISE, autopilotLinks);
    }

    private void refreshBank(Level level, BlockPos controllerPos, FlightMode mode, Map<VectorDirection, BlockPos> assignments) {
        Map<VectorDirection, List<ThrusterLink>> bank = links.get(mode);
        for (List<ThrusterLink> value : bank.values()) value.clear();
        if (assignments == null) return;

        // Explicitly linked blocks are always accepted. This keeps the Link Tool
        // authoritative and preserves support for propulsion implementations that
        // do not expose a physical facing API.
        for (Map.Entry<VectorDirection, BlockPos> entry : assignments.entrySet()) {
            addExplicit(level, controllerPos, mode, entry.getKey(), entry.getValue(), bank);
        }

        // A single selected thruster now seeds a whole directional bank. Additional
        // compatible blocks are accepted only when their own physical facing matches
        // the selected vector, preventing unrelated nearby propulsion from being used.
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
                if (reflective.getPhysicalDirection() != direction) continue;
                addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
            }
        }
    }

    private void addExplicit(Level level, BlockPos controllerPos, FlightMode mode, VectorDirection direction,
                             BlockPos target, Map<VectorDirection, List<ThrusterLink>> bank) {
        if (target == null || direction == null) return;
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity == null) return;
        PropulsionSource source = ReflectivePropulsionSource.tryCreate(blockEntity, direction, mountOffset(controllerPos, target));
        if (source != null) addIfUnique(bank.get(direction), new ThrusterLink(source, direction, mode));
    }

    private static double[] mountOffset(BlockPos controllerPos, BlockPos target) {
        return new double[]{
                target.getX() + 0.5D - (controllerPos.getX() + 0.5D),
                target.getY() + 0.5D - (controllerPos.getY() + 0.5D),
                target.getZ() + 0.5D - (controllerPos.getZ() + 0.5D)
        };
    }

    private static void addIfUnique(List<ThrusterLink> list, ThrusterLink link) {
        String id = link.source.getId();
        for (ThrusterLink existing : list) if (existing.source.getId().equals(id)) return;
        list.add(link);
    }

    public void link(ThrusterLink link) {
        List<ThrusterLink> list = links.get(link.mode).get(link.direction);
        addIfUnique(list, link);
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

    public int getVectorThrusterCount(FlightMode mode, VectorDirection direction) {
        return links.get(mode).get(direction).size();
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
