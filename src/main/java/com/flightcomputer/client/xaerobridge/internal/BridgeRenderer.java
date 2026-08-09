package com.flightcomputer.client.xaerobridge.internal;

import com.flightcomputer.client.xaerobridge.api.MapOverlayContext;
import com.flightcomputer.client.xaerobridge.api.UiOverlayContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Field;

/** NeoForge 1.21.1 render adapter. Xaero remains the base renderer. */
public final class BridgeRenderer {
    private BridgeRenderer() {}

    public static void renderTail(Screen screen, GuiGraphics graphics, int width, int height) {
        if (OverlayRegistry.hasMapOverlays()) {
            renderMap(screen, graphics, width, height);
        }
        if (OverlayRegistry.hasUiOverlays()) {
            OverlayRegistry.renderUi(new UiOverlayContext(graphics, width, height));
        }
    }

    private static void renderMap(Screen screen, GuiGraphics graphics, int width, int height) {
        double cameraX = readNumber(screen, "cameraX");
        double cameraZ = readNumber(screen, "cameraZ");
        double scale = readNumber(screen, "scale");
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraZ) || !Double.isFinite(scale) || scale <= 0.0D) {
            return;
        }

        double guiScale = Math.max(1.0D, Minecraft.getInstance().getWindow().getGuiScale());
        Object dimension = readField(screen, "lastViewedDimensionId");
        if (dimension == null) dimension = readField(screen, "lastNonNullViewedDimensionId");
        String dimensionId = dimension instanceof ResourceKey<?> key ? key.location().toString() : "unknown";

        OverlayRegistry.renderMap(new MapOverlayContext(
                graphics, width, height, cameraX, cameraZ, scale / guiScale, dimensionId));
    }

    private static double readNumber(Screen screen, String name) {
        Object value = readField(screen, name);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static Object readField(Object instance, String name) {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
