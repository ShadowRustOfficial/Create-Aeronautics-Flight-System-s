package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional client-side Waystones integration. It deliberately uses reflection so
 * Waystones is not a compile-time dependency of Flight Computer.
 *
 * <p>The integration reads Waystones' own registry rather than loading chunks.
 * Markers are refreshed on the client thread and the renderer only consumes the
 * last completed snapshot.</p>
 */
public final class WaystoneMapProvider {
    private static final String WAYSTONES_MANAGER = "net.blay09.mods.waystones.api.WaystoneManager";
    private static final String WAYSTONES_MOD_ID = "waystones";

    private final Map<String, FlightMapMarker> markers = new LinkedHashMap<>();
    private boolean initialized;
    private boolean available;
    private Method listMethod;
    private Object listOwner;
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

    public boolean isAvailable() {
        return available;
    }

    public void clear() {
        markers.clear();
    }

    private void refresh(ClientLevel level) {
        if (!ensureInitialized()) return;
        try {
            Object result = invokeList(level);
            if (!(result instanceof Iterable<?> iterable)) return;

            Map<String, FlightMapMarker> next = new LinkedHashMap<>();
            for (Object waystone : iterable) {
                FlightMapMarker marker = decode(level, waystone);
                if (marker != null) next.put(markerKey(waystone, marker), marker);
            }
            markers.clear();
            markers.putAll(next);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the last good snapshot. A transient API/version mismatch must not
            // blank an otherwise working map or impact the render thread.
        }
    }

    private Object invokeList(ClientLevel level) throws ReflectiveOperationException {
        Class<?>[] params = listMethod.getParameterTypes();
        if (params.length == 0) return listMethod.invoke(listOwner);
        if (params.length == 1 && params[0].isInstance(level)) return listMethod.invoke(listOwner, level);
        if (params.length == 1 && params[0].isAssignableFrom(Level.class)) return listMethod.invoke(listOwner, level);
        return null;
    }

    private FlightMapMarker decode(ClientLevel level, Object waystone) {
        if (waystone == null) return null;

        Object dimension = invokeOptional(waystone, "getDimension", "getDimensionId", "getLevel", "getLevelKey");
        if (!sameDimension(level, dimension)) return null;

        Object position = invokeOptional(waystone, "getPos", "getPosition", "getBlockPos", "getLocation", "getGlobalPos");
        Vec3 world = asVec3(position);
        if (world == null) return null;

        Object name = invokeOptional(waystone, "getName", "getWaystoneName", "name");
        String label = name == null ? "Waystone" : String.valueOf(name);
        if (label.isBlank()) label = "Waystone";

        return new FlightMapMarker(FlightMapMarker.Type.WAYSTONE, label, world.x, world.y, world.z);
    }

    private boolean sameDimension(ClientLevel level, Object dimension) {
        if (dimension == null) return true;
        ResourceKey<Level> current = level.dimension();
        ResourceLocation currentId = current.location();
        if (dimension instanceof ResourceKey<?> key) return current.equals(key);
        if (dimension instanceof ResourceLocation id) return currentId.equals(id);
        String value = String.valueOf(dimension);
        return value.equals(currentId.toString()) || value.equals(current.toString());
    }

    private Vec3 asVec3(Object value) {
        if (value instanceof Vec3 vec) return vec;
        if (value instanceof BlockPos pos) return Vec3.atCenterOf(pos);
        if (value instanceof net.minecraft.core.GlobalPos global) {
            return global.dimension().equals(currentDimensionKey) ? Vec3.atCenterOf(global.pos()) : null;
        }
        if (value == null) return null;
        Object pos = invokeOptional(value, "pos", "getPos", "position", "getPosition");
        if (pos != value) return asVec3(pos);
        return null;
    }

    private ResourceKey<Level> currentDimensionKey;

    private Object invokeOptional(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next known API spelling.
            }
        }
        return null;
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        try {
            Class<?> manager = Class.forName(WAYSTONES_MANAGER, false, getClass().getClassLoader());
            for (Method method : manager.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                String name = method.getName();
                if (!(name.equals("getAllWaystones") || name.equals("getWaystones") || name.equals("getAll"))) continue;
                if (method.getParameterCount() > 1) continue;
                listMethod = method;
                listOwner = null;
                available = true;
                return true;
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Waystones is optional.
        }
        available = false;
        return false;
    }

    private String markerKey(Object waystone, FlightMapMarker marker) {
        Object id = invokeOptional(waystone, "getUid", "getUUID", "getId", "getWaystoneUid");
        return id == null ? marker.label() + "@" + marker.worldX() + ":" + marker.worldZ() : String.valueOf(id);
    }
}
