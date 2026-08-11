package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Xaero waypoint reader. Reflection keeps Xaero's Minimap/World Map
 * completely optional; if its API changes or is absent, the provider simply
 * exposes no waypoints and the rest of the Flight Computer remains unaffected.
 */
public final class WaypointMapProvider {
    private static final String[] MANAGERS = {
            "xaero.common.minimap.waypoints.WaypointManager",
            "xaero.common.minimap.waypoints.WaypointManagerImpl"
    };

    private final Map<String, FlightMapMarker> markers = new LinkedHashMap<>();
    private boolean initialized;
    private boolean available;
    private Class<?> managerClass;
    private Object manager;
    private long nextRefreshTick;

    public void tick(ClientLevel level) {
        if (level == null || level.getGameTime() < nextRefreshTick) return;
        nextRefreshTick = level.getGameTime() + 20L;
        refresh(level);
    }

    public List<FlightMapMarker> markers() {
        return Collections.unmodifiableList(new ArrayList<>(markers.values()));
    }

    public boolean isAvailable() { return available; }

    private void refresh(ClientLevel level) {
        if (!ensureInitialized()) return;
        try {
            Object collection = findWaypointCollection(level);
            Iterable<?> iterable = asIterable(collection);
            if (iterable == null) return;
            Map<String, FlightMapMarker> next = new LinkedHashMap<>();
            for (Object waypoint : iterable) {
                FlightMapMarker marker = decode(level, waypoint);
                if (marker != null) next.put(markerKey(waypoint, marker), marker);
            }
            markers.clear();
            markers.putAll(next);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the last good snapshot. Optional integrations must never break the UI.
        }
    }

    private Object findWaypointCollection(ClientLevel level) throws ReflectiveOperationException {
        for (Method method : managerClass.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() > 1) continue;
            String n = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(n.contains("waypoint") || n.contains("set"))) continue;
            if (!Iterable.class.isAssignableFrom(method.getReturnType()) && !Map.class.isAssignableFrom(method.getReturnType()) && !method.getReturnType().isArray()) continue;
            Object target = Modifier.isStatic(method.getModifiers()) ? null : manager;
            if (method.getParameterCount() == 0) return method.invoke(target);
            if (method.getParameterTypes()[0].isInstance(level)) return method.invoke(target, level);
        }
        return null;
    }

    private Iterable<?> asIterable(Object result) {
        if (result instanceof Iterable<?> iterable) return iterable;
        if (result instanceof Map<?, ?> map) return map.values();
        if (result != null && result.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < Array.getLength(result); i++) values.add(Array.get(result, i));
            return values;
        }
        return null;
    }

    private FlightMapMarker decode(ClientLevel level, Object waypoint) {
        Object x = invokeOptional(waypoint, "getX", "x");
        Object y = invokeOptional(waypoint, "getY", "y");
        Object z = invokeOptional(waypoint, "getZ", "z");
        if (!(x instanceof Number nx) || !(z instanceof Number nz)) {
            Object pos = invokeOptional(waypoint, "getPos", "getPosition", "getBlockPos");
            if (pos instanceof BlockPos bp) {
                x = bp.getX() + 0.5D;
                y = bp.getY() + 0.5D;
                z = bp.getZ() + 0.5D;
            } else return null;
        }
        double wx = x instanceof Number n ? n.doubleValue() : 0.0D;
        double wy = y instanceof Number n ? n.doubleValue() : 0.0D;
        double wz = z instanceof Number n ? n.doubleValue() : 0.0D;

        Object dimension = invokeOptional(waypoint, "getDimension", "getDimensionId", "getDim");
        if (dimension != null && !String.valueOf(dimension).equals(level.dimension().location().toString()) && !String.valueOf(dimension).equals(level.dimension().toString())) return null;

        Object name = invokeOptional(waypoint, "getName", "getNameString", "name");
        String label = name == null ? "Waypoint" : String.valueOf(name);
        if (label.isBlank()) label = "Waypoint";
        return new FlightMapMarker(FlightMapMarker.Type.WAYPOINT, label, wx, wy, wz);
    }

    private Object invokeOptional(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private String markerKey(Object waypoint, FlightMapMarker marker) {
        Object id = invokeOptional(waypoint, "getId", "getUUID", "getName");
        return String.valueOf(id == null ? marker.label() + "@" + marker.worldX() + ":" + marker.worldZ() : id);
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        for (String name : MANAGERS) {
            try {
                managerClass = Class.forName(name, false, getClass().getClassLoader());
                manager = findManagerInstance();
                available = managerClass != null;
                return available;
            } catch (ClassNotFoundException | LinkageError ignored) { }
        }
        return false;
    }

    private Object findManagerInstance() {
        for (Method method : managerClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;
            String n = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(n.contains("instance") || n.equals("get") || n.contains("manager"))) continue;
            try {
                Object value = method.invoke(null);
                if (value != null && managerClass.isInstance(value)) return value;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }
}
