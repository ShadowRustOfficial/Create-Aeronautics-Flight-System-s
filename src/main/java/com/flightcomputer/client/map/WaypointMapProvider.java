package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional Xaero waypoint reader. Reflection keeps Xaero's Minimap/World Map optional while
 * supporting both direct waypoint collections and the waypoint-set/container APIs used by
 * newer Xaero builds.
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
        nextRefreshTick = level.getGameTime() + 10L;
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
            for (Object waypointOrSet : iterable) flatten(level, waypointOrSet, next, 0);
            markers.clear();
            markers.putAll(next);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the last good snapshot. Optional integrations must never break the UI.
        }
    }

    private void flatten(ClientLevel level, Object value, Map<String, FlightMapMarker> out, int depth) {
        if (value == null || depth > 3) return;
        FlightMapMarker marker = decode(level, value);
        if (marker != null) {
            out.put(markerKey(value, marker), marker);
            return;
        }
        Object children = invokeOptional(value, "getWaypoints", "getWaypointList", "getList", "getPoints", "getEntries");
        Iterable<?> iterable = asIterable(children);
        if (iterable != null) for (Object child : iterable) flatten(level, child, out, depth + 1);
    }

    private Object findWaypointCollection(ClientLevel level) throws ReflectiveOperationException {
        for (Method method : managerClass.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() > 1) continue;
            String n = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(n.contains("waypoint") || n.contains("set"))) continue;
            if (!returnsCollectionLike(method.getReturnType())) continue;
            Object target = Modifier.isStatic(method.getModifiers()) ? null : manager;
            if (target == null && !Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterCount() == 0) return method.invoke(target);
            if (method.getParameterTypes()[0].isInstance(level)) return method.invoke(target, level);
        }
        return null;
    }

    private static boolean returnsCollectionLike(Class<?> type) {
        return Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isArray();
    }

    private Iterable<?> asIterable(Object result) {
        if (result instanceof Optional<?> optional) return optional.map(this::asIterable).orElse(null);
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
        if (!(x instanceof Number) || !(z instanceof Number)) {
            Object pos = invokeOptional(waypoint, "getPos", "getPosition", "getBlockPos", "position");
            if (pos instanceof BlockPos bp) {
                x = bp.getX() + 0.5D;
                y = bp.getY() + 0.5D;
                z = bp.getZ() + 0.5D;
            } else return null;
        }
        double wx = ((Number) x).doubleValue();
        double wy = y instanceof Number n ? n.doubleValue() : 0.0D;
        double wz = ((Number) z).doubleValue();

        Object dimension = invokeOptional(waypoint, "getDimension", "getDimensionId", "getDim", "getSubWorld");
        if (dimension != null && !sameDimension(level, dimension)) return null;

        Object name = invokeOptional(waypoint, "getName", "getNameString", "name", "getLabel");
        String label = name == null ? "Waypoint" : String.valueOf(name);
        if (label.isBlank()) label = "Waypoint";
        return new FlightMapMarker(FlightMapMarker.Type.WAYPOINT, label, wx, wy, wz);
    }

    private boolean sameDimension(ClientLevel level, Object dimension) {
        String value = String.valueOf(dimension);
        String current = level.dimension().location().toString();
        return value.equals(current) || value.equals(level.dimension().toString()) || value.endsWith(current);
    }

    private Object invokeOptional(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private String markerKey(Object waypoint, FlightMapMarker marker) {
        Object id = invokeOptional(waypoint, "getId", "getUUID", "getName", "getWaypointId");
        return String.valueOf(id == null ? marker.label() + "@" + marker.worldX() + ":" + marker.worldZ() : id);
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        for (String name : MANAGERS) {
            try {
                managerClass = Class.forName(name, false, getClass().getClassLoader());
                manager = findManagerInstance();
                available = managerClass != null && (manager != null || hasStaticWaypointMethod());
                if (available) return true;
            } catch (ClassNotFoundException | LinkageError ignored) { }
        }
        available = false;
        return false;
    }

    private boolean hasStaticWaypointMethod() {
        if (managerClass == null) return false;
        for (Method method : managerClass.getMethods()) {
            String n = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (Modifier.isStatic(method.getModifiers()) && n.contains("waypoint") && returnsCollectionLike(method.getReturnType())) return true;
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
        for (Field field : managerClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !managerClass.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) return value;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }
}
