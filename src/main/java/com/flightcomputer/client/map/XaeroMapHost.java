package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import xaero.map.WorldMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
 * No Xaero terrain data is decoded, sampled, or reconstructed here.
 */
public final class XaeroMapHost {
    private Screen delegate;
    private Class<? extends Screen> delegateType;
    private String status = "Xaero map host not initialised.";
    private int viewportWidth;
    private int viewportHeight;

    public void tick(int width, int height) {
        ensureDelegate(width, height);
        if (delegate != null) {
            try {
                delegate.tick();
            } catch (RuntimeException exception) {
                fail("Xaero World Map tick failed", exception);
            }
        }
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
        } catch (RuntimeException exception) {
            // A broken/partially initialised Xaero screen must never take down the
            // Navigation Console. Drop the delegate and allow a clean retry later.
            fail("Xaero World Map render failed", exception);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        if (delegate == null) return false;
        try {
            return delegate.mouseClicked(mouseX - left, mouseY - top, button);
        } catch (RuntimeException exception) {
            fail("Xaero World Map mouse click failed", exception);
            return false;
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        if (delegate == null) return false;
        try {
            return delegate.mouseReleased(mouseX - left, mouseY - top, button);
        } catch (RuntimeException exception) {
            fail("Xaero World Map mouse release failed", exception);
            return false;
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY,
                                int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        if (delegate == null) return false;
        try {
            return delegate.mouseDragged(mouseX - left, mouseY - top, button, dragX, dragY);
        } catch (RuntimeException exception) {
            fail("Xaero World Map drag failed", exception);
            return false;
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 int left, int top, int width, int height) {
        if (!inside(mouseX, mouseY, left, top, width, height)) return false;
        ensureDelegate(width, height);
        if (delegate == null) return false;
        try {
            return delegate.mouseScrolled(mouseX - left, mouseY - top, scrollX, scrollY);
        } catch (RuntimeException exception) {
            fail("Xaero World Map scroll failed", exception);
            return false;
        }
    }

    public String diagnostics() {
        return status + (delegateType == null ? "" : "\nclass=" + delegateType.getName());
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
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            status = "Waiting for Minecraft client world/player.";
            return;
        }

        if (delegate != null && viewportWidth == width && viewportHeight == height) return;

        if (delegate == null) {
            delegateType = findWorldMapScreen();
            if (delegateType == null) {
                status = "Xaero World Map detected, but no concrete World Map Screen class was found.";
                return;
            }
            delegate = instantiate(delegateType, minecraft);
            if (delegate == null) {
                status = "Found Xaero World Map screen " + delegateType.getName()
                        + " but could not construct it with a supported live client context.";
                delegateType = null;
                return;
            }
            bindCurrentPlayer(delegate, minecraft.player);
        }

        viewportWidth = width;
        viewportHeight = height;
        try {
            delegate.init(minecraft, width, height);
            bindCurrentPlayer(delegate, minecraft.player);
            status = "Xaero native World Map screen active.";
        } catch (RuntimeException exception) {
            fail("Xaero World Map screen initialisation failed", exception);
        }
    }

    /**
     * Xaero's GuiMap has internal player state. The old implementation selected
     * the zero-argument constructor first, which creates a screen with a null
     * player and then crashes in GuiMap.render(). Prefer constructors that receive
     * the live Minecraft/player context and bind the player again after init().
     */
    private static Screen instantiate(Class<? extends Screen> type, Minecraft minecraft) {
        List<Constructor<?>> constructors = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            constructors.add(constructor);
        }

        constructors.sort(Comparator.comparingInt((Constructor<?> c) -> constructorScore(c, minecraft)).reversed()
                .thenComparingInt(Constructor::getParameterCount));

        for (Constructor<?> constructor : constructors) {
            Object[] arguments = buildArguments(constructor, minecraft);
            if (arguments == null) continue;
            try {
                constructor.setAccessible(true);
                Object instance = constructor.newInstance(arguments);
                if (instance instanceof Screen screen) return screen;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next compatible constructor. Xaero's exact constructor
                // signature is intentionally discovered rather than hardcoded.
            }
        }
        return null;
    }

    private static int constructorScore(Constructor<?> constructor, Minecraft minecraft) {
        int score = 0;
        for (Class<?> parameter : constructor.getParameterTypes()) {
            if (minecraft.player != null && parameter.isInstance(minecraft.player)) score += 100;
            else if (parameter.isInstance(minecraft)) score += 80;
            else if (Component.class.isAssignableFrom(parameter) || parameter.isInstance(Component.literal("x"))) score += 40;
            else if (parameter == boolean.class || parameter == Boolean.class) score += 1;
            else return -1000;
        }
        if (constructor.getParameterCount() == 0) score -= 500;
        return score;
    }

    private static Object[] buildArguments(Constructor<?> constructor, Minecraft minecraft) {
        Object[] arguments = new Object[constructor.getParameterCount()];
        Component title = Component.literal("Flight Computer Map");
        Player player = minecraft.player;

        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            Class<?> parameter = constructor.getParameterTypes()[i];
            if (player != null && parameter.isInstance(player)) {
                arguments[i] = player;
            } else if (parameter.isInstance(minecraft)) {
                arguments[i] = minecraft;
            } else if (parameter.isInstance(title)) {
                arguments[i] = title;
            } else if (parameter == boolean.class || parameter == Boolean.class) {
                arguments[i] = false;
            } else {
                return null;
            }
        }
        return arguments;
    }

    /**
     * Defensive compatibility step for Xaero releases whose GuiMap constructor
     * does not initialise its player field. This is only used when the field exists,
     * is currently null, and accepts the live Minecraft player.
     */
    private static void bindCurrentPlayer(Screen screen, Player player) {
        if (player == null) return;
        Class<?> type = screen.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("player");
                if (!Modifier.isStatic(field.getModifiers()) && field.getType().isInstance(player)) {
                    field.setAccessible(true);
                    if (field.get(screen) == null) field.set(screen, player);
                }
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }

    private void fail(String prefix, RuntimeException exception) {
        status = prefix + ": " + exception.getClass().getSimpleName()
                + " — " + String.valueOf(exception.getMessage());
        delegate = null;
        viewportWidth = 0;
        viewportHeight = 0;
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
            // The known candidates above remain as a compatibility path.
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

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
