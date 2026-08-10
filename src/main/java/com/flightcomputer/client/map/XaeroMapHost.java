package com.flightcomputer.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

/** Hosts the actual GuiMap instance created by Xaero inside the Flight Computer viewport. */
public final class XaeroMapHost {
    private static volatile Screen nativeScreen;

    private Screen delegate;
    private boolean initialized;
    private int delegateWidth;
    private int delegateHeight;
    private String status = "Waiting for Xaero World Map native screen.";

    public static void captureNativeScreen(Screen screen) {
        if (screen == null) return;
        if ("xaero.map.gui.GuiMap".equals(screen.getClass().getName())) nativeScreen = screen;
    }

    public static void clearNativeScreen() { nativeScreen = null; }
    public static Screen getCapturedNativeScreen() { return nativeScreen; }

    public void tick(int width, int height) {
        ensureDelegate(width, height);
        if (delegate != null) {
            try { delegate.tick(); }
            catch (RuntimeException exception) { status = "Xaero native map tick failed: " + exception.getClass().getSimpleName(); }
        }
    }

    public void render(GuiGraphics graphics, int left, int top, int width, int height,
                       int mouseX, int mouseY, float partialTick) {
        ensureDelegate(width, height);
        if (delegate == null) return;

        // The previous host rendered Xaero at the full Minecraft screen size and then
        // translated/cropped it into the Flight Computer. That made Xaero's own screen
        // coordinate system larger than the map viewport: panning could therefore move
        // native background/UI pixels into the console shell and expose the old border leak.
        //
        // The captured GuiMap is now initialised at the exact viewport dimensions. Xaero's
        // map camera and screen UI therefore operate in their own isolated 600x260 space,
        // while the Flight Computer UI remains outside that coordinate system.
        int delegateWidthNow = Math.max(1, delegate.width);
        int delegateHeightNow = Math.max(1, delegate.height);
        int delegateMouseX = mouseX - left;
        int delegateMouseY = mouseY - top;

        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0D);
        try {
            delegate.render(graphics, delegateMouseX, delegateMouseY, partialTick);
        } catch (RuntimeException exception) {
            status = "Xaero native map render failed: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
            resetRenderState();
        }

        // Keep these reads explicit so a malformed/partially initialised native screen
        // cannot result in invalid viewport assumptions during a resize.
        if (delegateWidthNow <= 0 || delegateHeightNow <= 0) {
            initialized = false;
        }
    }

    private static void resetRenderState() {
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseClicked(toDelegateX(mouseX, left), toDelegateY(mouseY, top), button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseReleased(toDelegateX(mouseX, left), toDelegateY(mouseY, top), button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseDragged(toDelegateX(mouseX, left), toDelegateY(mouseY, top), button, dragX, dragY);
    }

    /** Zoom is intentionally disabled in the Flight Computer. The map is fixed at 1x. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 int left, int top, int width, int height) {
        return false;
    }

    public static boolean forwardMouseClicked(double mouseX, double mouseY, int button,
                                              int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseClicked(mouseX - left, mouseY - top, button);
    }

    public static boolean forwardMouseReleased(double mouseX, double mouseY, int button,
                                               int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseReleased(mouseX - left, mouseY - top, button);
    }

    public static boolean forwardMouseDragged(double mouseX, double mouseY, int button,
                                              double dragX, double dragY, int left, int top, int width, int height) {
        Screen screen = nativeScreen;
        return screen != null && screen.mouseDragged(mouseX - left, mouseY - top, button, dragX, dragY);
    }

    /** Zoom input is deliberately consumed by the host layer and never reaches Xaero. */
    public static boolean forwardMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                               int left, int top, int width, int height) {
        return false;
    }

    public boolean centerOn(double worldX, double worldZ) {
        if (delegate == null || !initialized) return false;
        XaeroMapViewport.Snapshot view = XaeroMapViewport.read();
        if (view == null || !view.finite()) return false;
        double dragX = -(worldX - view.cameraX()) * view.pixelsPerBlock();
        double dragY = -(worldZ - view.cameraZ()) * view.pixelsPerBlock();
        if (!Double.isFinite(dragX) || !Double.isFinite(dragY)) return false;
        if (Math.abs(dragX) < 0.25D && Math.abs(dragY) < 0.25D) return true;
        double startX = delegate.width / 2.0D;
        double startY = delegate.height / 2.0D;
        double endX = startX + dragX;
        double endY = startY + dragY;
        try {
            delegate.mouseClicked(startX, startY, 0);
            delegate.mouseDragged(endX, endY, 0, dragX, dragY);
            delegate.mouseReleased(endX, endY, 0);
            return true;
        } catch (RuntimeException exception) {
            status = "Xaero recenter failed: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception);
            return false;
        }
    }

    public String diagnostics() {
        StringBuilder result = new StringBuilder(status);
        result.append("\nbridge=").append(XaeroNativeMapBridge.status());
        result.append("\nnativeScreen=").append(nativeScreen == null ? "<none>" : nativeScreen.getClass().getName());
        result.append("\ninitialized=").append(initialized);
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) { result.append("\nsession=<none>"); return result.toString(); }
        result.append("\nsessionUsable=").append(session.isUsable());
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) { result.append("\nmapProcessor=<none>"); return result.toString(); }
        result.append("\nworld=").append(safe(processor.getCurrentWorldId()));
        result.append("\ndimension=").append(safe(processor.getCurrentDimId()));
        result.append("\nmap=").append(safe(processor.getCurrentMWId()));
        return result.toString();
    }

    public boolean isActive() { return delegate != null && initialized; }

    public void clear() {
        initialized = false;
        delegate = null;
        delegateWidth = 0;
        delegateHeight = 0;
        status = "Reinitialising captured Xaero native map screen.";
    }

    private void ensureDelegate(int viewportWidth, int viewportHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) { status = "Waiting for Minecraft client world."; return; }
        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session == null ? null : session.getMapProcessor();
        if (session == null || !session.isUsable() || processor == null) {
            status = "Waiting for Xaero World Map session/processor for the current world."; return;
        }
        Screen captured = nativeScreen;
        if (captured == null || !"xaero.map.gui.GuiMap".equals(captured.getClass().getName())) {
            status = "Xaero World Map detected, but no live GuiMap instance has been captured yet."; return;
        }
        if (delegate != captured) { delegate = captured; initialized = false; }

        int targetWidth = Math.max(1, viewportWidth);
        int targetHeight = Math.max(1, viewportHeight);
        if (!initialized || delegateWidth != targetWidth || delegateHeight != targetHeight) {
            try {
                // Important: initialise the native Xaero screen against the Flight Computer
                // viewport, not the full Minecraft window. This keeps panning/camera/UI math
                // local to the map rectangle and prevents the native screen from leaking into
                // the surrounding Navigation Console.
                delegate.init(minecraft, targetWidth, targetHeight);
                delegateWidth = targetWidth;
                delegateHeight = targetHeight;
                initialized = true;
                status = "Xaero native World Map screen active in isolated Flight Computer viewport.";
            } catch (RuntimeException exception) {
                initialized = false;
                status = "Xaero native World Map initialisation failed: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception);
            }
        }
    }

    private double toDelegateX(double x, int left) { return x - left; }
    private double toDelegateY(double y, int top) { return y - top; }
    private static boolean inside(double x, double y, int left, int top, int width, int height) { return x >= left && x < left + width && y >= top && y < top + height; }
    private static String safeMessage(Throwable throwable) { String message = throwable.getMessage(); return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message; }
    private static String safe(String value) { return value == null || value.isBlank() ? "<none>" : value; }
}
