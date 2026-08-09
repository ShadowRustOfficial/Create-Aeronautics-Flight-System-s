package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Hosts Xaero's own World Map screen inside the Flight Computer map viewport.
 *
 * Flight Computer does not recreate Xaero terrain. Xaero remains responsible for
 * selecting the active map, loading its map tiles, camera state and rendering the
 * terrain. This class only supplies Xaero's live session/processor to the native
 * screen and forwards the screen lifecycle/input calls into the viewport.
 */
public final class XaeroMapHost {
    private Screen delegate;
    private Class<? extends Screen> delegateType;
    private String status = "Xaero map host not initialised.";
    private int viewportWidth;
    private int viewportHeight;

    public void tick(int width, int height) {
        ensureDelegate(width, height);
        if (delegate != null) delegate.tick();
    }

    public void render(GuiGraphics graphics, int left, int top, int width, int height,
                       int mouseX, int mouseY, float partialTick) {
        ensureDelegate(width, height);
        if (delegate == null) return;

        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0D);
        try {
            delegate.render(graphics, mouseX - left, mouseY - top, partialTick);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseClicked(mouseX - left, mouseY - top, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseReleased(mouseX - left, mouseY - top, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseDragged(mouseX - left, mouseY - top, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        return delegate != null && delegate.mouseScrolled(mouseX - left, mouseY - top, scrollX, scrollY);
    }

    public String diagnostics() {
        StringBuilder result = new StringBuilder(status);
        if (delegateType != null) result.append("\nclass=").append(delegateType.getName());

        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session != null) {
            result.append("\nsessionUsable=").append(session.isUsable());
            MapProcessor processor = session.getMapProcessor();
            if (processor != null) {
                result.append("\nworld=").append(safe(processor.getCurrentWorldId()));
                result.append("\ndimension=").append(safe(processor.getCurrentDimId()));
                result.append("\nmap=").append(safe(processor.getCurrentMWId()));
            }
        }
        return result.toString();
    }

    public boolean isActive() {
        return delegate != null;
    }

    public void clear() {
        delegate = null;
        delegateType = null;
        viewportWidth = 0;
        viewportHeight = 0;
        status = "Xaero map host cleared.";
    }

    private void ensureDelegate(int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            status = "Waiting for Minecraft client world.";
            return;
        }

        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session == null ? null : session.getMapProcessor();
        if (session == null || !session.isUsable() || processor == null || processor.getWorld() != minecraft.level) {
            status = "Waiting for Xaero World Map session/processor for the current world.";
            return;
        }

        if (delegate != null && viewportWidth == width && viewportHeight == height) return;

        if (delegate == null) {
            delegateType = findWorldMapScreen();
            if (delegateType == null) {
                status = "Xaero World Map detected, but no concrete World Map Screen class was found.";
                return;
            }
            delegate = instantiate(delegateType, session, processor);
            if (delegate == null) {
                status = "Found Xaero World Map screen " + delegateType.getName()
                        + " but could not construct it with the live Xaero WorldMapSession/MapProcessor.";
                delegateType = null;
                return;
            }
        }

        viewportWidth = width;
        viewportHeight = height;
        try {
            delegate.init(minecraft, width, height);
            status = "Xaero native World Map screen active.";
        } catch (RuntimeException exception) {
            status = "Xaero World Map screen initialisation failed: " + exception.getClass().getSimpleName()
                    + " - " + safeMessage(exception);
            delegate = null;
        }
    }

    private static Screen instantiate(Class<? extends Screen> type, WorldMapSession session,
                                      MapProcessor processor) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Object> context = List.of(minecraft, session, processor,
                minecraft.level, Component.literal("Flight Computer Map"));

        // Prefer explicit static factories if this Xaero build exposes one.
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !Screen.class.isAssignableFrom(method.getReturnType())) continue;
            String lowerName = method.getName().toLowerCase();
            if (!lowerName.contains("map") && !lowerName.contains("screen")) continue;
            Object[] args = resolveArguments(method.getParameterTypes(), context);
            if (args == null) continue;
            try {
                method.setAccessible(true);
                Object value = method.invoke(null, args);
                if (value instanceof Screen screen) return screen;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next compatible factory/constructor.
            }
        }

        List<Constructor<?>> constructors = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) constructors.add(constructor);
        constructors.sort(Comparator.comparingInt(Constructor::getParameterCount));

        for (Constructor<?> constructor : constructors) {
            Object[] args = resolveArguments(constructor.getParameterTypes(), context);
            if (args == null) continue;
            try {
                constructor.setAccessible(true);
                Object value = constructor.newInstance(args);
                if (value instanceof Screen screen) return screen;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Xaero point releases can change the exact constructor signature.
            }
        }
        return null;
    }

    private static Object[] resolveArguments(Class<?>[] parameterTypes, List<Object> context) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameter = parameterTypes[i];
            Object value = context.stream()
                    .filter(candidate -> candidate != null && parameter.isInstance(candidate))
                    .findFirst().orElse(null);
            if (value != null) {
                args[i] = value;
                continue;
            }

            if (parameter == boolean.class || parameter == Boolean.class) {
                args[i] = false;
            } else if (parameter == byte.class || parameter == Byte.class) {
                args[i] = (byte) 0;
            } else if (parameter == short.class || parameter == Short.class) {
                args[i] = (short) 0;
            } else if (parameter == int.class || parameter == Integer.class) {
                args[i] = 0;
            } else if (parameter == long.class || parameter == Long.class) {
                args[i] = 0L;
            } else if (parameter == float.class || parameter == Float.class) {
                args[i] = 0.0F;
            } else if (parameter == double.class || parameter == Double.class) {
                args[i] = 0.0D;
            } else if (parameter == char.class || parameter == Character.class) {
                args[i] = '\0';
            } else if (parameter == String.class) {
                args[i] = "";
            } else if (!parameter.isPrimitive()) {
                // Only nullable arguments are allowed to fall through to null.
                args[i] = null;
            } else {
                return null;
            }
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Screen> findWorldMapScreen() {
        // Xaero 1.21.1 uses GuiMap as the concrete fullscreen map screen. ScreenBase is
        // an abstract/base rendering class and must never be selected as the delegate.
        List<String> names = new ArrayList<>();
        names.add("xaero.map.gui.GuiMap");
        names.add("xaero.map.gui.WorldMapScreen");
        names.add("xaero.map.gui.GuiWorldMap");
        names.add("xaero.map.gui.GuiWorldMapScreen");

        try {
            URL source = WorldMap.class.getProtectionDomain().getCodeSource().getLocation();
            Path sourcePath = Path.of(URI.create(source.toString()));
            if (Files.isDirectory(sourcePath)) {
                Path guiRoot = sourcePath.resolve("xaero/map/gui");
                if (Files.isDirectory(guiRoot)) {
                    try (var stream = Files.walk(guiRoot)) {
                        stream.filter(path -> path.toString().endsWith(".class"))
                                .forEach(path -> names.add(toClassName(sourcePath, path)));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(sourcePath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith("xaero/map/gui/") && name.endsWith(".class")) {
                            names.add(name.substring(0, name.length() - 6).replace('/', '.'));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Known candidates remain as the compatibility path.
        }

        ClassLoader loader = WorldMap.class.getClassLoader();
        List<Class<? extends Screen>> candidates = new ArrayList<>();
        for (String name : names) {
            if (!name.startsWith("xaero.map.gui.") || !name.toLowerCase().contains("map")) continue;
            String lower = name.toLowerCase();
            if (lower.contains("screenbase") || lower.contains("settings") || lower.contains("options")
                    || lower.contains("select") || lower.contains("confirm") || lower.contains("help")) continue;
            try {
                Class<?> type = Class.forName(name, false, loader);
                if (Screen.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                    candidates.add((Class<? extends Screen>) type);
                }
            } catch (LinkageError | ClassNotFoundException ignored) {
                // Continue scanning other Xaero GUI classes.
            }
        }

        return candidates.stream()
                .distinct()
                .sorted(Comparator.comparingInt(XaeroMapHost::screenPriority))
                .findFirst().orElse(null);
    }

    private static int screenPriority(Class<? extends Screen> type) {
        String name = type.getSimpleName().toLowerCase();
        if (name.equals("guimap")) return 0;
        if (name.equals("worldmapscreen")) return 1;
        if (name.equals("guiworldmap")) return 2;
        if (name.contains("worldmap")) return 3;
        return 10;
    }

    private static String toClassName(Path root, Path classFile) {
        String relative = root.relativize(classFile).toString();
        relative = relative.substring(0, relative.length() - 6);
        return relative.replace('/', '.').replace('\\', '.');
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
