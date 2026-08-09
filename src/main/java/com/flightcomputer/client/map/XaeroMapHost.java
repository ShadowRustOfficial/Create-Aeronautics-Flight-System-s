package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

/**
 * Hosts the actual GuiMap instance created by Xaero inside the Flight Computer viewport.
 *
 * No Xaero screen is constructed here. Xaero creates its normal native GuiMap through
 * its own key/opening path; the bridge captures that live instance and the Flight Computer
 * renders the real screen into a scissored viewport. This keeps Xaero responsible for
 * terrain, map tiles, camera state, zoom, pan and dimension handling.
 */
public final class XaeroMapHost {
    private static volatile Screen nativeScreen;

    private Screen delegate;
    private boolean initialized;
    private int delegateWidth;
    private int delegateHeight;
    private String status = "Waiting for Xaero World Map native screen.";

    public static void captureNativeScreen(Screen screen) {
        if (screen == null) return;
        String name = screen.getClass().getName();
        if ("xaero.map.gui.GuiMap".equals(name)) {
            nativeScreen = screen;
        }
    }

    public static void clearNativeScreen() {
        nativeScreen = null;
    }

    public static Screen getCapturedNativeScreen() {
        return nativeScreen;
    }

    public void tick(int width, int height) {
        ensureDelegate(width, height);
        if (delegate != null) {
            try {
                delegate.tick();
            } catch (RuntimeException exception) {
                status = "Xaero native map tick failed: " + exception.getClass().getSimpleName();
            }
        }
    }

    public void render(GuiGraphics graphics, int left, int top, int width, int height,
                       int mouseX, int mouseY, float partialTick) {
        ensureDelegate(width, height);
        if (delegate == null) return;

        int fullWidth = Math.max(1, delegate.width);
        int fullHeight = Math.max(1, delegate.height);
        double offsetX = (fullWidth - width) / 2.0D;
        double offsetY = (fullHeight - height) / 2.0D;
        int delegateMouseX = (int) Math.round(mouseX - left + offsetX);
        int delegateMouseY = (int) Math.round(mouseY - top + offsetY);

        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushPose();
        graphics.pose().translate(left - offsetX, top - offsetY, 0.0D);
        try {
            delegate.render(graphics, delegateMouseX, delegateMouseY, partialTick);
        } catch (RuntimeException exception) {
            status = "Xaero native map render failed: " + exception.getClass().getSimpleName()
                    + " - " + safeMessage(exception);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseClicked(toDelegateX(mouseX, left, width),
                toDelegateY(mouseY, top, height), button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseReleased(toDelegateX(mouseX, left, width),
                toDelegateY(mouseY, top, height), button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseDragged(toDelegateX(mouseX, left, width),
                toDelegateY(mouseY, top, height), button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseScrolled(toDelegateX(mouseX, left, width),
                toDelegateY(mouseY, top, height), scrollX, scrollY);
    }

    public static boolean forwardMouseClicked(double mouseX, double mouseY, int button,
                                              int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseClicked(
                toDelegateXStatic(mouseX, left, width, screen.width),
                toDelegateYStatic(mouseY, top, height, screen.height), button);
    }

    public static boolean forwardMouseReleased(double mouseX, double mouseY, int button,
                                               int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseReleased(
                toDelegateXStatic(mouseX, left, width, screen.width),
                toDelegateYStatic(mouseY, top, height, screen.height), button);
    }

    public static boolean forwardMouseDragged(double mouseX, double mouseY, int button,
                                              double dragX, double dragY,
                                              int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseDragged(
                toDelegateXStatic(mouseX, left, width, screen.width),
                toDelegateYStatic(mouseY, top, height, screen.height), button, dragX, dragY);
    }

    public static boolean forwardMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                               int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseScrolled(
                toDelegateXStatic(mouseX, left, width, screen.width),
                toDelegateYStatic(mouseY, top, height, screen.height), scrollX, scrollY);
    }

    public String diagnostics() {
        StringBuilder result = new StringBuilder(status);
        result.append("\nbridge=").append(XaeroNativeMapBridge.status());
        result.append("\nnativeScreen=").append(nativeScreen == null ? "<none>" : nativeScreen.getClass().getName());
        result.append("\ninitialized=").append(initialized);

        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) {
            result.append("\nsession=<none>");
            return result.toString();
        }

        result.append("\nsessionUsable=").append(session.isUsable());
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) {
            result.append("\nmapProcessor=<none>");
            return result.toString();
        }

        result.append("\nworld=").append(safe(processor.getCurrentWorldId()));
        result.append("\ndimension=").append(safe(processor.getCurrentDimId()));
        result.append("\nmap=").append(safe(processor.getCurrentMWId()));
        return result.toString();
    }

    public boolean isActive() {
        return delegate != null && initialized;
    }

    /** Reinitialises the captured native screen without discarding the live Xaero instance. */
    public void clear() {
        initialized = false;
        delegateWidth = 0;
        delegateHeight = 0;
        status = "Reinitialising captured Xaero native map screen.";
    }

    private void ensureDelegate(int viewportWidth, int viewportHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            status = "Waiting for Minecraft client world.";
            return;
        }

        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session == null ? null : session.getMapProcessor();
        if (session == null || !session.isUsable() || processor == null) {
            status = "Waiting for Xaero World Map session/processor for the current world.";
            return;
        }

        Screen captured = nativeScreen;
        if (captured == null || !"xaero.map.gui.GuiMap".equals(captured.getClass().getName())) {
            status = "Xaero World Map detected, but no live GuiMap instance has been captured yet.";
            return;
        }

        if (delegate != captured) {
            delegate = captured;
            initialized = false;
        }

        int fullWidth = minecraft.getWindow().getGuiScaledWidth();
        int fullHeight = minecraft.getWindow().getGuiScaledHeight();
        if (!initialized || delegateWidth != fullWidth || delegateHeight != fullHeight) {
            try {
                delegate.init(minecraft, fullWidth, fullHeight);
                delegateWidth = fullWidth;
                delegateHeight = fullHeight;
                initialized = true;
                status = "Xaero native World Map screen active; Flight Computer is hosting its live renderer.";
            } catch (RuntimeException exception) {
                initialized = false;
                status = "Xaero native World Map initialisation failed: "
                        + exception.getClass().getSimpleName() + " - " + safeMessage(exception);
            }
        }
    }

    private double toDelegateX(double x, int left, int width) {
        return toDelegateXStatic(x, left, width, delegate == null ? width : delegate.width);
    }

    private double toDelegateY(double y, int top, int height) {
        return toDelegateYStatic(y, top, height, delegate == null ? height : delegate.height);
    }

    private static double toDelegateXStatic(double x, int left, int width, int fullWidth) {
        return x - left + (fullWidth - width) / 2.0D;
    }

    private static double toDelegateYStatic(double y, int top, int height, int fullHeight) {
        return y - top + (fullHeight - height) / 2.0D;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }
}
