package com.flightcomputer.client.xaerobridge.internal;

import com.flightcomputer.client.xaerobridge.api.MapOverlayContext;
import com.flightcomputer.client.xaerobridge.api.UiOverlayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class OverlayRegistry {
    private static final Map<String, Entry<MapOverlayContext>> MAP = new ConcurrentHashMap<>();
    private static final Map<String, Entry<UiOverlayContext>> UI = new ConcurrentHashMap<>();
    private static volatile boolean mapHookAvailable;
    private static volatile boolean uiHookAvailable;

    private OverlayRegistry() {}

    public static void registerMap(String id, int order, Consumer<MapOverlayContext> overlay) {
        if (id == null || overlay == null) throw new IllegalArgumentException("Overlay id and callback are required");
        MAP.put(id, new Entry<>(order, overlay));
    }

    public static void registerUi(String id, int order, Consumer<UiOverlayContext> overlay) {
        if (id == null || overlay == null) throw new IllegalArgumentException("Overlay id and callback are required");
        UI.put(id, new Entry<>(order, overlay));
    }

    public static void unregisterMap(String id) { MAP.remove(id); }
    public static void unregisterUi(String id) { UI.remove(id); }

    public static boolean hasMapOverlays() { return !MAP.isEmpty(); }
    public static boolean hasUiOverlays() { return !UI.isEmpty(); }

    public static void markMapHookAvailable() { mapHookAvailable = true; }
    public static void markUiHookAvailable() { uiHookAvailable = true; }
    public static boolean isMapHookAvailable() { return mapHookAvailable; }
    public static boolean isUiHookAvailable() { return uiHookAvailable; }

    public static void renderMap(MapOverlayContext context) {
        mapHookAvailable = true;
        snapshot(MAP).forEach(entry -> safeRender(entry.callback, context));
    }

    public static void renderUi(UiOverlayContext context) {
        uiHookAvailable = true;
        snapshot(UI).forEach(entry -> safeRender(entry.callback, context));
    }

    private static <T> ArrayList<Entry<T>> snapshot(Map<String, Entry<T>> source) {
        ArrayList<Entry<T>> entries = new ArrayList<>(source.values());
        entries.sort(Comparator.comparingInt(Entry::order));
        return entries;
    }

    private static <T> void safeRender(Consumer<T> callback, T context) {
        try {
            callback.accept(context);
        } catch (RuntimeException exception) {
            BridgeLog.warn("Xaero overlay callback failed: " + exception.getClass().getSimpleName());
        }
    }

    private record Entry<T>(int order, Consumer<T> callback) {}
}
