package com.flightcomputer.client.xaerobridge.api;

import com.flightcomputer.client.xaerobridge.internal.OverlayRegistry;

import java.util.function.Consumer;

/**
 * Minimal NeoForge 1.21.1 port of the Xaero World Map Bridge overlay API.
 *
 * The bridge never replaces Xaero terrain. It provides a stable integration point
 * for Flight Computer overlays above Xaero's native renderer.
 */
public final class XaeroWorldMapBridge {
    private XaeroWorldMapBridge() {}

    public static void registerMapOverlay(String id, int order, Consumer<MapOverlayContext> overlay) {
        OverlayRegistry.registerMap(id, order, overlay);
    }

    public static void registerUiOverlay(String id, int order, Consumer<UiOverlayContext> overlay) {
        OverlayRegistry.registerUi(id, order, overlay);
    }

    public static void unregisterMapOverlay(String id) {
        OverlayRegistry.unregisterMap(id);
    }

    public static void unregisterUiOverlay(String id) {
        OverlayRegistry.unregisterUi(id);
    }

    public static boolean isMapOverlayAvailable() {
        return OverlayRegistry.isMapHookAvailable();
    }

    public static boolean isUiOverlayAvailable() {
        return OverlayRegistry.isUiHookAvailable();
    }
}
