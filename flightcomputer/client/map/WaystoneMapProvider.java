package com.flightcomputer.client.map;

import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Client presentation adapter for server-authoritative Waystone snapshots. */
public final class WaystoneMapProvider {
    private static final long RESCAN_TICKS = 20L;
    private static volatile List<FlightMapMarker> SERVER_SNAPSHOT = List.of();
    private static volatile String SERVER_DIMENSION = "";
    private static volatile boolean SERVER_SNAPSHOT_RECEIVED;

    private final Map<String, FlightMapMarker> markers = new LinkedHashMap<>();
    private boolean initialized;
    private boolean available;
    private Class<?> apiClass;
    private java.lang.reflect.Method getWaystones;
    private java.lang.reflect.Method getAllWaystones;
    private long nextRefreshTick;
    private boolean serverRequestSent;
    private String lastDimension = "";

    public static void acceptServerSnapshot(String dimension, List<FlightMapMarker> snapshot) {
        SERVER_DIMENSION = dimension == null ? "" : dimension;
        SERVER_SNAPSHOT = snapshot == null ? List.of() : List.copyOf(snapshot);
        SERVER_SNAPSHOT_RECEIVED = true;
    }

    public static void clearServerSnapshot() {
        SERVER_DIMENSION = "";
        SERVER_SNAPSHOT = List.of();
        SERVER_SNAPSHOT_RECEIVED = false;
    }

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return;
        String dimension = level.dimension().location().toString();
        boolean dimensionChanged = !dimension.equals(lastDimension);
        if (dimensionChanged) {
            lastDimension = dimension;
            serverRequestSent = false;
            markers.clear();
            available = false;
            clearServerSnapshot();
        }

        long now = level.getGameTime();
        if (!dimensionChanged && now < nextRefreshTick) return;
        nextRefreshTick = now + RESCAN_TICKS;

        if (SERVER_SNAPSHOT_RECEIVED && SERVER_DIMENSION.equals(dimension)) {
            replace(SERVER_SNAPSHOT);
            available = true;
            return;
        }

        if (!serverRequestSent) {
            serverRequestSent = true;
            FlightComputerNetwork.requestWaystoneSnapshot();
        }

        // Singleplayer fallback. Multiplayer uses the authoritative server packet.
        if (minecraft.getSingleplayerServer() != null) refreshSingleplayer(minecraft, level);
    }

    public void requestRefresh(ClientLevel level) {
        serverRequestSent = false;
        nextRefreshTick = 0L;
        if (level != null) tick(level);
    }

    public List<FlightMapMarker> markers() { return Collections.unmodifiableList(new ArrayList<>(markers.values())); }
    public boolean isAvailable() { return available; }

    public void clear() {
        markers.clear(); initialized = false; available = false; apiClass = null; getWaystones = null; getAllWaystones = null;
        nextRefreshTick = 0L; serverRequestSent = false; lastDimension = "";
    }

    private void replace(List<FlightMapMarker> next) {
        Map<String, FlightMapMarker> deduplicated = new LinkedHashMap<>();
        for (FlightMapMarker marker : next) deduplicated.put(marker.label()+"@"+marker.worldX()+":"+marker.worldY()+":"+marker.worldZ(), marker);
        markers.clear();
        markers.putAll(deduplicated);
    }

    private void refreshSingleplayer(Minecraft minecraft, ClientLevel level) {
        if (!ensureInitialized() || minecraft.getSingleplayerServer() == null) return;
        try {
            ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(level.dimension());
            if (serverLevel == null) return;
            Object result = getWaystones != null
                    ? getWaystones.invoke(null, serverLevel)
                    : getAllWaystones == null ? List.of() : getAllWaystones.invoke(null, minecraft.getSingleplayerServer());
            List<FlightMapMarker> next = new ArrayList<>();
            for (Object waystone : asIterable(result)) { FlightMapMarker marker = decode(level, waystone); if (marker != null) next.add(marker); }
            replace(next); available = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private FlightMapMarker decode(ClientLevel level, Object waystone) {
        if (waystone == null) return null;
        Object valid = invokeNoArg(waystone, "isValid"); if (valid instanceof Boolean b && !b) return null;
        Object dimension = invokeNoArg(waystone, "getDimension", "getDimensionId"); if (dimension != null && !sameDimension(level, dimension)) return null;
        Object position = invokeNoArg(waystone, "getPos", "getPosition", "getBlockPos"); if (!(position instanceof net.minecraft.core.BlockPos pos)) return null;
        Object name = invokeNoArg(waystone, "getEffectiveName", "getName", "getWaystoneName");
        String label = name == null ? "Waystone" : name instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(name);
        if (label.isBlank()) label = "Waystone";
        return new FlightMapMarker(FlightMapMarker.Type.WAYSTONE, label, pos.getX()+.5D, pos.getY()+.5D, pos.getZ()+.5D);
    }

    private boolean sameDimension(ClientLevel level, Object dimension) {
        String value = String.valueOf(dimension), current = level.dimension().location().toString();
        return value.equals(current) || value.equals(level.dimension().toString()) || value.endsWith(current) || value.contains(current);
    }

    private Iterable<?> asIterable(Object result) {
        if (result instanceof Iterable<?> iterable) return iterable;
        if (result instanceof Map<?, ?> map) return map.values();
        if (result instanceof Stream<?> stream) return stream.toList();
        if (result instanceof java.util.Optional<?> optional && optional.isPresent()) return asIterable(optional.get());
        return List.of();
    }

    private Object invokeNoArg(Object target, String... names) {
        for (String name : names) try { java.lang.reflect.Method method=target.getClass().getMethod(name); if(method.getParameterCount()==0)return method.invoke(target); }
        catch (ReflectiveOperationException|RuntimeException ignored) { }
        return null;
    }

    private boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        try {
            apiClass=Class.forName("net.blay09.mods.waystones.api.WaystonesAPI",false,getClass().getClassLoader());
            getWaystones=findStatic("getWaystones",ServerLevel.class);
            if (getWaystones == null) getWaystones=findStatic("getWaystones",net.minecraft.world.level.Level.class);
            getAllWaystones=findStatic("getAllWaystones",net.minecraft.server.MinecraftServer.class);
            available=getWaystones!=null || getAllWaystones!=null;
        } catch (ClassNotFoundException|LinkageError ignored) { available=false; }
        return available;
    }

    private java.lang.reflect.Method findStatic(String name,Class<?> parameter) {
        if(apiClass==null)return null;
        try { java.lang.reflect.Method method=apiClass.getMethod(name,parameter); return java.lang.reflect.Modifier.isStatic(method.getModifiers())?method:null; }
        catch(ReflectiveOperationException ignored){return null;}
    }
}
