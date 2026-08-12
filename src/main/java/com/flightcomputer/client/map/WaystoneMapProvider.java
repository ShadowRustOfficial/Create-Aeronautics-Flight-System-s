package com.flightcomputer.client.map;

import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client presentation adapter for Waystones. The server owns the Waystones registry and sends a
 * dimension-scoped immutable snapshot. A local singleplayer API fallback remains for compatibility.
 */
public final class WaystoneMapProvider {
    private static final long RESCAN_TICKS = 20L;
    private static volatile List<FlightMapMarker> SERVER_SNAPSHOT = List.of();
    private static volatile String SERVER_DIMENSION = "";
    private static volatile boolean SERVER_SNAPSHOT_RECEIVED;

    private final Map<String, FlightMapMarker> markers = new LinkedHashMap<>();
    private boolean initialized;
    private boolean available;
    private Class<?> apiClass;
    private java.lang.reflect.Method getAllWaystones;
    private java.lang.reflect.Method getActivatedWaystones;
    private long nextRefreshTick;
    private boolean serverRequestSent;

    public static void acceptServerSnapshot(String dimension, List<FlightMapMarker> snapshot) {
        SERVER_DIMENSION = dimension == null ? "" : dimension;
        SERVER_SNAPSHOT = snapshot == null ? List.of() : List.copyOf(snapshot);
        SERVER_SNAPSHOT_RECEIVED = true;
    }

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return;
        if (level.getGameTime() < nextRefreshTick) return;
        nextRefreshTick = level.getGameTime() + RESCAN_TICKS;

        String dimension = level.dimension().location().toString();
        if (SERVER_SNAPSHOT_RECEIVED && SERVER_DIMENSION.equals(dimension)) {
            replace(SERVER_SNAPSHOT);
            available = true;
            return;
        }

        if (!serverRequestSent) {
            serverRequestSent = true;
            FlightComputerNetwork.requestWaystoneSnapshot();
        }

        // Singleplayer fallback. Multiplayer uses the authoritative packet above.
        if (minecraft.getSingleplayerServer() != null) refreshSingleplayer(minecraft, level);
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
        serverRequestSent = false;
    }

    private void replace(List<FlightMapMarker> next) {
        Map<String, FlightMapMarker> deduplicated = new LinkedHashMap<>();
        for (FlightMapMarker marker : next) deduplicated.put(marker.label() + "@" + marker.worldX() + ":" + marker.worldZ(), marker);
        markers.clear();
        markers.putAll(deduplicated);
    }

    private void refreshSingleplayer(Minecraft minecraft, ClientLevel level) {
        if (!ensureInitialized() || getAllWaystones == null || minecraft.getSingleplayerServer() == null) return;
        try {
            Object result = getAllWaystones.invoke(null, minecraft.getSingleplayerServer());
            List<FlightMapMarker> next = new ArrayList<>();
            for (Object waystone : asIterable(result)) {
                FlightMapMarker marker = decode(level, waystone);
                if (marker != null) next.add(marker);
            }
            replace(next);
            available = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private FlightMapMarker decode(ClientLevel level, Object waystone) {
        if (waystone == null) return null;
        Object valid = invokeNoArg(waystone, "isValid");
        if (valid instanceof Boolean b && !b) return null;
        Object dimension = invokeNoArg(waystone, "getDimension", "getDimensionId");
        if (dimension != null && !sameDimension(level, dimension)) return null;
        Object position = invokeNoArg(waystone, "getPos", "getPosition", "getBlockPos");
        if (!(position instanceof net.minecraft.core.BlockPos pos)) return null;
        Object name = invokeNoArg(waystone, "getEffectiveName", "getName", "getWaystoneName");
        String label = name == null ? "Waystone" : name instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(name);
        if (label.isBlank()) label = "Waystone";
        return new FlightMapMarker(FlightMapMarker.Type.WAYSTONE, label, pos.getX()+.5D, pos.getY()+.5D, pos.getZ()+.5D);
    }

    private boolean sameDimension(ClientLevel level, Object dimension) {
        String value = String.valueOf(dimension);
        String current = level.dimension().location().toString();
        return value.equals(current) || value.equals(level.dimension().toString()) || value.endsWith(current);
    }

    private Iterable<?> asIterable(Object result) {
        if (result instanceof Iterable<?> iterable) return iterable;
        if (result instanceof Map<?, ?> map) return map.values();
        if (result instanceof java.util.Optional<?> optional) return optional.map(this::asIterable).orElse(List.of());
        return List.of();
    }

    private Object invokeNoArg(Object target, String... names) {
        for (String name : names) {
            try {
                java.lang.reflect.Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        try {
            apiClass = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI", false, getClass().getClassLoader());
            getAllWaystones = findStatic("getAllWaystones", net.minecraft.server.MinecraftServer.class);
            getActivatedWaystones = findStatic("getActivatedWaystones", net.minecraft.world.entity.player.Player.class);
            available = getAllWaystones != null || getActivatedWaystones != null;
        } catch (ClassNotFoundException | LinkageError ignored) { available = false; }
        return available;
    }

    private java.lang.reflect.Method findStatic(String name, Class<?> parameter) {
        if (apiClass == null) return null;
        try {
            java.lang.reflect.Method method = apiClass.getMethod(name, parameter);
            return java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (ReflectiveOperationException ignored) { return null; }
    }
}
