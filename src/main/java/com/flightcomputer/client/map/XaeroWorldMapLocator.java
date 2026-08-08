package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Locates Xaero's World Map data without linking the Flight Computer to Xaero's
 * internal classes. Xaero keeps its map instances outside the vanilla world save
 * under xaero/world-map; the per-world xaeromap.txt file is only a level-id hint.
 *
 * This class deliberately does not decode Xaero's private region binary format yet.
 * It provides the stable profile/instance discovery layer so the renderer can use
 * pre-existing map data without forcing chunks to load, while retaining the native
 * terrain sampler as a fallback when Xaero is absent.
 */
public final class XaeroWorldMapLocator {
    private static final Pattern LEVEL_ID = Pattern.compile("^\\s*id\\s*:\\s*(-?\\d+)\\s*$");

    private XaeroWorldMapLocator() {}

    public record MapInstance(Path root, Path dimensionDirectory, Path instanceDirectory,
                              String levelId, int regionFiles) {}

    public static Optional<MapInstance> locate(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return Optional.empty();

        Path root = findRoot(minecraft.gameDirectory);
        if (root == null) return Optional.empty();

        Path serverRoot = findServerRoot(root, minecraft, level);
        if (serverRoot == null) return Optional.empty();

        String dimension = dimensionDirectoryName(level);
        Path dimensionDirectory = serverRoot.resolve(dimension);
        if (!Files.isDirectory(dimensionDirectory)) return Optional.empty();

        Path instance = findBestInstance(dimensionDirectory);
        if (instance == null) return Optional.empty();

        int regionFiles = countRegionFiles(instance);
        if (regionFiles == 0) return Optional.empty();

        String levelId = readLevelId(level);
        return Optional.of(new MapInstance(root, dimensionDirectory, instance, levelId, regionFiles));
    }

    private static Path findRoot(Path gameDirectory) {
        Path modern = gameDirectory.resolve("xaero").resolve("world-map");
        if (Files.isDirectory(modern)) return modern;

        Path legacy = gameDirectory.resolve("XaeroWorldMap");
        return Files.isDirectory(legacy) ? legacy : null;
    }

    private static Path findServerRoot(Path root, Minecraft minecraft, ClientLevel level) {
        String preferred;
        if (minecraft.getCurrentServer() != null) {
            preferred = "Multiplayer_" + sanitizeServerAddress(minecraft.getCurrentServer().ip);
        } else {
            String worldName = level.getLevelData().getLevelName();
            preferred = worldName == null || worldName.isBlank() ? null : worldName;
        }

        if (preferred != null) {
            Path candidate = root.resolve(preferred);
            if (Files.isDirectory(candidate)) return candidate;
        }

        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals("server_profiles"))
                    .findFirst().orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String dimensionDirectoryName(ClientLevel level) {
        String id = level.dimension().location().toString();
        return switch (id) {
            case "minecraft:overworld" -> "null";
            case "minecraft:the_nether" -> "DIM-1";
            case "minecraft:the_end" -> "DIM1";
            default -> sanitizeDimension(id);
        };
    }

    private static Path findBestInstance(Path dimensionDirectory) {
        try (Stream<Path> children = Files.list(dimensionDirectory)) {
            List<Path> instances = children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("mw$"))
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();
            return instances.isEmpty() ? null : instances.get(0);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int countRegionFiles(Path instance) {
        try (Stream<Path> files = Files.walk(instance, 2)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String readLevelId(ClientLevel level) {
        // xaeromap.txt lives in a world save, so this is intentionally best-effort.
        // Dedicated servers can provide the same ID later through the controller network.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) return "unknown";

        Path saveRoot = minecraft.gameDirectory.resolve("saves")
                .resolve(level.getLevelData().getLevelName());
        Path file = saveRoot.resolve("xaeromap.txt");
        if (!Files.isRegularFile(file)) return "unknown";

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher matcher = LEVEL_ID.matcher(line);
                if (matcher.matches()) return matcher.group(1);
            }
        } catch (IOException ignored) {
            // Map discovery must never break the controller UI.
        }
        return "unknown";
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static String sanitizeServerAddress(String address) {
        return address.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private static String sanitizeDimension(String id) {
        return id.replace(':', '$').replace('/', '_').replace('\\', '_');
    }
}
