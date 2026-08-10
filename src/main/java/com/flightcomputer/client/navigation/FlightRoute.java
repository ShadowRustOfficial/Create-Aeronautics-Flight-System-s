package com.flightcomputer.client.navigation;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A Flight Computer-owned route. It contains no Xaero GUI state. */
public final class FlightRoute {
    public record Node(String name, ResourceLocation dimension, double x, double y, double z) {}

    private final List<Node> nodes = new ArrayList<>();

    public void clear() { nodes.clear(); }

    public void add(Node node) {
        if (node != null) nodes.add(node);
    }

    public List<Node> nodes() { return Collections.unmodifiableList(nodes); }

    public boolean isEmpty() { return nodes.isEmpty(); }

    public Node next() { return isEmpty() ? null : nodes.get(0); }

    public double distance2D(double fromX, double fromZ) {
        if (next() == null) return 0.0D;
        double dx = next().x() - fromX;
        double dz = next().z() - fromZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double bearingDegrees(double fromX, double fromZ) {
        if (next() == null) return 0.0D;
        double dx = next().x() - fromX;
        double dz = next().z() - fromZ;
        double bearing = Math.toDegrees(Math.atan2(dx, dz));
        return bearing < 0.0D ? bearing + 360.0D : bearing;
    }
}
