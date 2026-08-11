package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Waystones integration using the published Waystones API by reflection.
 *
 * Server-owned Waystone data is never invented on the client. In singleplayer we snapshot the
 * complete server registry on the server thread. In multiplayer the client-visible activated list
 * is used, which is the data Waystones deliberately exposes to that player. No Waystones state is
 * modified and the integration is a no-op when Waystones is absent.
 */
public final class WaystoneMapProvider {
    private static final String API = "net.blay09.mods.waystones.api.WaystonesAPI";
    private static final long RESCAN_TICKS = 20L;

    private final Map<String, FlightMapMarker> markers = new LinkedHashMap<>();
    private boolean initialized;
    private boolean available;
    private Class<?> apiClass;
    private Method getAllWaystones;
    private Method getActivatedWaystones;
    private long nextRefreshTick;
    private boolean serverRequestPending;

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return;
        if (level.getGameTime() < nextRefreshTick) return;
        nextRefreshTick = level.getGameTime() + RESCAN_TICKS;
        refresh(minecraft, level);
    }

    public List<FlightMapMarker> markers() {
        return Collections.unmodifiableList(new ArrayList<>(markers.values()));
    }

    public boolean isAvailable() { return available; }

    public void clear() {
        markers.clear();
        initialized = false;
        available = false;
        apiClass = null;
        getAllWaystones = null;
        getActivatedWaystones = null;
        nextRefreshTick = 0L;
        serverRequestPending = false;
    }

    private void refresh(Minecraft minecraft, ClientLevel level) {
        if (!ensureInitialized()) {
            markers.clear();
            return;
        }

        if (minecraft.getSingleplayerServer() != null) {
            snapshotSingleplayer(minecraft.getSingleplayerServer(), level);
            return;
        }

        // Multiplayer client-side list: this is intentionally the player's activated/publicly
        // visible Waystones rather than a guessed client reconstruction of the server registry.
        if (minecraft.player == null || getActivatedWaystones == null) return;
        try {
            Object result = getActivatedWaystones.invoke(null, minecraft.player);
            replaceWith(result, level);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the last good snapshot.
        }
    }

    private void snapshotSingleplayer(MinecraftServer server, ClientLevel level) {
        if (getAllWaystones == null || serverRequestPending) return;
        serverRequestPending = true;
        server.execute(() -> {
            try {
                Object result = getAllWaystones.invoke(null, server);
                replaceWith(result, level);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                markers.clear();
            } finally {
                serverRequestPending = false;
            }
        });
    }

    private void replaceWith(Object result, ClientLevel level) {
        Iterable<?> values = asIterable(result);
        if (values == null) return;
        Map<String, FlightMapMarker> next = new LinkedHashMap<>();
        for (Object value : values) {
            FlightMapMarker marker = decode(level, value);
            if (marker != null) next.put(markerKey(value, marker), marker);
        }
        markers.clear();
        markers.putAll(next);
    }

    private FlightMapMarker decode(ClientLevel level, Object waystone) {
        if (waystone == null) return null;
        Object valid = invokeNoArg(waystone, "isValid");
        if (valid instanceof Boolean b && !b) return null;

        Object dimension = invokeNoArg(waystone, "getDimension", "getDimensionId");
        if (dimension != null && !sameDimension(level, dimension)) return null;

        Object position = invokeNoArg(waystone, "getPos", "getPosition", "getBlockPos");
        if (!(position instanceof BlockPos pos)) return null;

        Object name = invokeNoArg(waystone, "getName", "getWaystoneName");
        String label = name == null ? "Waystone" : name instanceof net.minecraft.network.chat.Component c
                ? c.getString() : String.valueOf(name);
        if (label.isBlank()) label = "Waystone";
        return new FlightMapMarker(FlightMapMarker.Type.WAYSTONE, label,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private boolean sameDimension(ClientLevel level, Object dimension) {
        String value = String.valueOf(dimension);
        String current = level.dimension().location().toString();
        return value.equals(current) || value.equals(level.dimension().toString()) || value.endsWith(current);
    }

    private String markerKey(Object waystone, FlightMapMarker marker) {
        Object id = invokeNoArg(waystone, "getWaystoneUid", "getUid", "getUUID", "getId");
        return id == null ? marker.label() + "@" + marker.worldX() + ":" + marker.worldZ() : String.valueOf(id);
    }

    private Iterable<?> asIterable(Object result) {
        if (result instanceof java.util.Optional<?> optional) return optional.map(this::asIterable).orElse(null);
        if (result instanceof Iterable<?> iterable) return iterable;
        if (result instanceof Map<?, ?> map) return map.values();
        if (result != null && result.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < java.lang.reflect.Array.getLength(result); i++)
                values.add(java.lang.reflect.Array.get(result, i));
            return values;
        }
        return null;
    }

    private Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        try {
            apiClass = Class.forName(API, false, getClass().getClassLoader());
            getAllWaystones = findStatic("getAllWaystones", MinecraftServer.class);
            getActivatedWaystones = findStatic("getActivatedWaystones", Player.class);
            available = getAllWaystones != null || getActivatedWaystones != null;
        } catch (ClassNotFoundException | LinkageError ignored) {
            available = false;
        }
        return available;
    }

    private Method findStatic(String name, Class<?> parameter) {
        if (apiClass == null) return null;
        try {
            Method method = apiClass.getMethod(name, parameter);
            return java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
