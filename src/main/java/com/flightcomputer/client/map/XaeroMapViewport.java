package com.flightcomputer.client.map;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Field;

/** Reads the live Xaero GuiMap camera state without recreating Xaero's renderer. */
public final class XaeroMapViewport {
    private XaeroMapViewport() {}

    public record Snapshot(double cameraX, double cameraZ, double pixelsPerBlock,
                           int fullWidth, int fullHeight, String dimension) {
        public int worldToViewportX(double worldX, int viewportLeft, int viewportWidth) {
            double offsetX = (fullWidth - viewportWidth) / 2.0D;
            return viewportLeft + (int) Math.round(fullWidth / 2.0D
                    + (worldX - cameraX) * pixelsPerBlock - offsetX);
        }

        public int worldToViewportY(double worldZ, int viewportTop, int viewportHeight) {
            double offsetY = (fullHeight - viewportHeight) / 2.0D;
            return viewportTop + (int) Math.round(fullHeight / 2.0D
                    + (worldZ - cameraZ) * pixelsPerBlock - offsetY);
        }

        public double blocksPerPixel() {
            return pixelsPerBlock <= 0.0D ? Double.POSITIVE_INFINITY : 1.0D / pixelsPerBlock;
        }

        public boolean finite() {
            return Double.isFinite(cameraX) && Double.isFinite(cameraZ)
                    && Double.isFinite(pixelsPerBlock) && pixelsPerBlock > 0.0D;
        }
    }

    public static Snapshot read() {
        Screen screen = XaeroMapHost.getCapturedNativeScreen();
        if (screen == null) return null;

        double cameraX = readNumber(screen, "cameraX");
        double cameraZ = readNumber(screen, "cameraZ");
        double scale = readNumber(screen, "scale");
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraZ)
                || !Double.isFinite(scale) || scale <= 0.0D) return null;

        double guiScale = Math.max(1.0D, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        String dimension = readDimension(screen);
        return new Snapshot(cameraX, cameraZ, scale / guiScale,
                Math.max(1, screen.width), Math.max(1, screen.height), dimension);
    }

    private static double readNumber(Object instance, String name) {
        Object value = readField(instance, name);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static String readDimension(Object instance) {
        Object value = readField(instance, "lastViewedDimensionId");
        if (value == null) value = readField(instance, "lastNonNullViewedDimensionId");
        return value instanceof ResourceKey<?> key ? key.location().toString() : "unknown";
    }

    private static Object readField(Object instance, String name) {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy.
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return null;
            }
        }
        return null;
    }
}
