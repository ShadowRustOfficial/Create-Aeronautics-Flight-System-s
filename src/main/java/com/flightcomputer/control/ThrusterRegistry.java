package com.flightcomputer.control;

import java.util.*;

public final class ThrusterRegistry {
    private final Map<FlightMode, Map<ControlAxis, List<ThrusterLink>>> links = new EnumMap<>(FlightMode.class);

    public ThrusterRegistry() {
        for (FlightMode mode : FlightMode.values()) {
            Map<ControlAxis, List<ThrusterLink>> axisMap = new EnumMap<>(ControlAxis.class);
            for (ControlAxis axis : ControlAxis.values()) axisMap.put(axis, new ArrayList<>());
            links.put(mode, axisMap);
        }
    }

    public void link(ThrusterLink link) {
        List<ThrusterLink> list = links.get(link.mode).get(link.axis);
        list.removeIf(existing -> existing.source.getId().equals(link.source.getId()));
        list.add(link);
    }

    public void unlinkSource(String sourceId) {
        for (Map<ControlAxis, List<ThrusterLink>> axisMap : links.values()) {
            for (List<ThrusterLink> list : axisMap.values()) {
                list.removeIf(l -> l.source.getId().equals(sourceId));
            }
        }
    }

    public List<ThrusterLink> getLinks(FlightMode mode, ControlAxis axis) {
        return Collections.unmodifiableList(links.get(mode).get(axis));
    }

    public List<ThrusterLink> getLinks(FlightMode mode, VectorDirection direction) {
        List<ThrusterLink> result = new ArrayList<>();
        for (ControlAxis axis : ControlAxis.values()) {
            for (ThrusterLink link : links.get(mode).get(axis)) {
                if (link.direction == direction) result.add(link);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public double getAxisAuthority(FlightMode mode, ControlAxis axis) {
        double total = 0;
        for (ThrusterLink link : getLinks(mode, axis)) {
            total += Math.max(0, link.source.getMaxThrust());
        }
        return total;
    }

    public double getVectorAuthority(FlightMode mode, VectorDirection direction) {
        double total = 0;
        for (ThrusterLink link : getLinks(mode, direction)) {
            total += Math.max(0, link.source.getMaxThrust());
        }
        return total;
    }

    public boolean hasAnyVector(FlightMode mode, VectorDirection direction) {
        return getVectorAuthority(mode, direction) > 0;
    }

    public boolean isFullyLinked(FlightMode mode) {
        for (ControlAxis axis : ControlAxis.values()) {
            if (getAxisAuthority(mode, axis) <= 0) return false;
        }
        return true;
    }

    public List<ControlAxis> getUnlinkedAxes(FlightMode mode) {
        List<ControlAxis> missing = new ArrayList<>();
        for (ControlAxis axis : ControlAxis.values()) {
            if (getAxisAuthority(mode, axis) <= 0) missing.add(axis);
        }
        return missing;
    }
}
