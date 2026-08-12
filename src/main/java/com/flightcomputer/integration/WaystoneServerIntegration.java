package com.flightcomputer.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-only reflection adapter for Waystones. The API owns the registry; the flight computer
 * only snapshots immutable navigation coordinates for the client map.
 */
public final class WaystoneServerIntegration {
    private static final String API = "net.blay09.mods.waystones.api.WaystonesAPI";
    private WaystoneServerIntegration() { }

    public record Entry(String name, double x, double y, double z) { }

    public static List<Entry> snapshot(ServerPlayer player) {
        if (player == null || player.server == null || !(player.level() instanceof ServerLevel level)) return List.of();
        try {
            Class<?> api = Class.forName(API, false, WaystoneServerIntegration.class.getClassLoader());
            Object result = invokeWaystoneCollection(api, level, player.server);
            if (!(result instanceof Iterable<?> iterable) && !(result instanceof java.util.Map<?, ?>)) return List.of();

            String currentDimension = level.dimension().location().toString();
            List<Entry> entries = new ArrayList<>();
            Iterable<?> values = result instanceof java.util.Map<?, ?> map ? map.values() : (Iterable<?>) result;
            for (Object waystone : values) {
                if (waystone == null) continue;
                Object valid = invokeNoArg(waystone, "isValid");
                if (valid instanceof Boolean b && !b) continue;

                Object dimension = invokeNoArg(waystone, "getDimension", "getDimensionId");
                String dimensionId = dimension == null ? "" : String.valueOf(dimension);
                if (!dimensionMatches(dimensionId, currentDimension)) continue;

                Object position = invokeNoArg(waystone, "getPos", "getPosition", "getBlockPos");
                if (!(position instanceof BlockPos pos)) continue;
                Object name = invokeNoArg(waystone, "getEffectiveName", "getName", "getWaystoneName");
                String label = name == null ? "Waystone" : name instanceof net.minecraft.network.chat.Component c
                        ? c.getString() : String.valueOf(name);
                if (label.isBlank()) label = "Waystone";
                entries.add(new Entry(label, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D));
            }
            return entries;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return List.of();
        }
    }

    private static Object invokeWaystoneCollection(Class<?> api, ServerLevel level, MinecraftServer server) throws ReflectiveOperationException {
        // Current Waystones API: registry is queried for the world/dimension.
        Method current = findStatic(api, "getWaystones", ServerLevel.class);
        if (current != null) return current.invoke(null, level);
        Method levelApi = findStatic(api, "getWaystones", net.minecraft.world.level.Level.class);
        if (levelApi != null) return levelApi.invoke(null, level);

        // Compatibility fallback for older API builds used by earlier development versions.
        Method legacy = findStatic(api, "getAllWaystones", MinecraftServer.class);
        if (legacy != null) return legacy.invoke(null, server);
        return List.of();
    }

    private static Method findStatic(Class<?> type, String name, Class<?> parameter) {
        try {
            Method method = type.getMethod(name, parameter);
            return Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean dimensionMatches(String value, String current) {
        return value.equals(current) || value.endsWith(current) || value.contains(current);
    }

    private static Object invokeNoArg(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }
}
