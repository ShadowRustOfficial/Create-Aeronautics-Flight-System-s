package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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

/** Optional client-side Waystones integration with multiple API-shape fallbacks. */
public final class WaystoneMapProvider {
    private static final String WAYSTONES_MANAGER = "net.blay09.mods.waystones.api.WaystoneManager";

    private final Map<String, FlightMapMarker> markers = new LinkedHashMap<>();
    private boolean initialized;
    private boolean available;
    private Class<?> managerClass;
    private Object manager;
    private Method listMethod;
    private long nextRefreshTick;

    public void tick(ClientLevel level) {
        if (level == null) return;
        if (level.getGameTime() < nextRefreshTick) return;
        nextRefreshTick = level.getGameTime() + 20L;
        refresh(level);
    }

    public List<FlightMapMarker> markers() {
        return Collections.unmodifiableList(new ArrayList<>(markers.values()));
    }

    public boolean isAvailable() { return available; }
    public void clear() { markers.clear(); }

    private void refresh(ClientLevel level) {
        if (!ensureInitialized()) return;
        try {
            Object result = invokeList(level);
            Iterable<?> iterable = asIterable(result);
            if (iterable == null) return;
            Map<String, FlightMapMarker> next = new LinkedHashMap<>();
            for (Object value : iterable) flatten(level, value, next, 0);
            markers.clear();
            markers.putAll(next);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the last good snapshot if Waystones is still synchronising its registry.
        }
    }

    private void flatten(ClientLevel level, Object value, Map<String, FlightMapMarker> out, int depth) {
        if (value == null || depth > 3) return;
        FlightMapMarker marker = decode(level, value);
        if (marker != null) {
            out.put(markerKey(value, marker), marker);
            return;
        }
        Object children = invokeOptional(value, "getWaystones", "getWaystoneList", "getList", "getEntries", "values");
        Iterable<?> iterable = asIterable(children);
        if (iterable != null) for (Object child : iterable) flatten(level, child, out, depth + 1);
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

    private Object invokeList(ClientLevel level) throws ReflectiveOperationException {
        if (listMethod == null) return null;
        Object target = Modifier.isStatic(listMethod.getModifiers()) ? null : manager;
        Class<?>[] params = listMethod.getParameterTypes();
        if (params.length == 0) return listMethod.invoke(target);
        if (params.length == 1 && params[0].isInstance(level)) return listMethod.invoke(target, level);
        return null;
    }

    private FlightMapMarker decode(ClientLevel level, Object waystone) {
        if (waystone == null) return null;
        Object position = invokeOptional(waystone, "getPos", "getPosition", "getBlockPos", "getLocation", "getGlobalPos", "position");
        if (position instanceof Optional<?> optional) position = optional.orElse(null);
        Vec3 world = asVec3(level, position);
        if (world == null) {
            Object x = invokeOptional(waystone, "getX", "x");
            Object y = invokeOptional(waystone, "getY", "y");
            Object z = invokeOptional(waystone, "getZ", "z");
            if (x instanceof Number nx && z instanceof Number nz) {
                world = new Vec3(nx.doubleValue(), y instanceof Number ny ? ny.doubleValue() : 0.0D, nz.doubleValue());
            }
        }
        if (world == null) return null;

        Object dimension = invokeOptional(waystone, "getDimension", "getDimensionId", "getLevel", "getLevelKey", "getDim");
        if (dimension instanceof Optional<?> optional) dimension = optional.orElse(null);
        if (dimension != null && !sameDimension(level, dimension)) return null;

        Object name = invokeOptional(waystone, "getName", "getWaystoneName", "name", "getLabel");
        String label = name == null ? "Waystone" : String.valueOf(name);
        if (label.isBlank()) label = "Waystone";
        return new FlightMapMarker(FlightMapMarker.Type.WAYSTONE, label, world.x, world.y, world.z);
    }

    private boolean sameDimension(ClientLevel level, Object dimension) {
        ResourceKey<Level> current = level.dimension();
        ResourceLocation currentId = current.location();
        if (dimension instanceof ResourceKey<?> key) return current.equals(key);
        if (dimension instanceof ResourceLocation id) return currentId.equals(id);
        String value = String.valueOf(dimension);
        return value.equals(currentId.toString()) || value.equals(current.toString()) || value.endsWith(currentId.toString());
    }

    private Vec3 asVec3(ClientLevel level, Object value) {
        if (value instanceof Vec3 vec) return vec;
        if (value instanceof BlockPos pos) return Vec3.atCenterOf(pos);
        if (value instanceof GlobalPos global) return level.dimension().equals(global.dimension()) ? Vec3.atCenterOf(global.pos()) : null;
        if (value == null) return null;
        Object pos = invokeOptional(value, "pos", "getPos", "position", "getPosition");
        if (pos instanceof Optional<?> optional) pos = optional.orElse(null);
        if (pos != value) return asVec3(level, pos);
        return null;
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

    private String markerKey(Object waystone, FlightMapMarker marker) {
        Object id = invokeOptional(waystone, "getUid", "getUUID", "getId", "getWaystoneUid");
        return id == null ? marker.label() + "@" + marker.worldX() + ":" + marker.worldZ() : String.valueOf(id);
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        try {
            managerClass = Class.forName(WAYSTONES_MANAGER, false, getClass().getClassLoader());
            manager = findManagerInstance();
            for (Method method : managerClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && manager == null) continue;
                if (method.getParameterCount() > 1) continue;
                String name = method.getName();
                if (!(name.equals("getAllWaystones") || name.equals("getWaystones") || name.equals("getAll") || name.equals("getWaystonesByType"))) continue;
                if (!returnsCollectionLike(method.getReturnType())) continue;
                if (name.equals("getWaystonesByType") && method.getParameterCount() != 1) continue;
                listMethod = method;
                available = true;
                return true;
            }
        } catch (ClassNotFoundException | LinkageError ignored) { }
        available = false;
        return false;
    }

    private static boolean returnsCollectionLike(Class<?> type) {
        return Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isArray() || Optional.class.isAssignableFrom(type);
    }

    private Object findManagerInstance() {
        if (managerClass == null) return null;
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
