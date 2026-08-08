package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Locates Xaero World Map data without requiring Xaero at compile/runtime. */
public final class XaeroWorldMapLocator {
    private XaeroWorldMapLocator() {}

    public record MapInstance(Path root, Path dimensionDirectory, Path instanceDirectory,
                              String levelId, int regionFiles) {}

    public static Optional<MapInstance> locate(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return Optional.empty();

        Path root = findRoot(minecraft.gameDirectory.toPath());
        if (root == null) return Optional.empty();

        Path worldRoot = findWorldRoot(root, minecraft);
        if (worldRoot == null) return Optional.empty();

        Path dimensionDirectory = findDimensionDirectory(worldRoot, level);
        if (dimensionDirectory == null) return Optional.empty();

        Path instance = findBestInstance(dimensionDirectory);
        if (instance == null) return Optional.empty();

        int regionFiles = countRegionFiles(instance);
        if (regionFiles <= 0) return Optional.empty();

        return Optional.of(new MapInstance(
                root,
                dimensionDirectory,
                instance,
                readLevelId(minecraft),
                regionFiles));
    }

    private static Path findRoot(Path gameDirectory) {
        Path modern = gameDirectory.resolve("xaero").resolve("world-map");
        if (Files.isDirectory(modern)) return modern;

        Path legacy = gameDirectory.resolve("XaeroWorldMap");
        return Files.isDirectory(legacy) ? legacy : null;
    }

    private static Path findWorldRoot(Path root, Minecraft minecraft) {
        String preferred;
        if (minecraft.getCurrentServer() != null) {
            preferred = "Multiplayer_" + sanitizeServerAddress(minecraft.getCurrentServer().ip);
        } else {
            preferred = singleplayerWorldName(minecraft);
        }

        if (preferred != null && !preferred.isBlank()) {
            Path exact = root.resolve(preferred);
            if (Files.isDirectory(exact)) return exact;
        }

        String normalized = normalizeWorldFolderName(preferred);
        try (Stream<Path> children = Files.list(root)) {
            List<Path> candidates = children
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = normalizeWorldFolderName(path.getFileName().toString());
                        return name.equals(normalized)
                                || name.equals("singleplayer_" + normalized)
                                || name.equals("multiplayer_" + normalized);
                    })
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();
            if (!candidates.isEmpty()) return candidates.get(0);
        } catch (IOException ignored) { }

        // Last-resort singleplayer fallback: Xaero's current world folder is often
        // the newest directory when multiple "New World" instances exist.
        if (minecraft.getCurrentServer() == null) {
            try (Stream<Path> children = Files.list(root)) {
                return children.filter(Files::isDirectory)
                        .filter(path -> containsDimensionData(path, levelDimensionMarker(minecraft)))
                        .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                        .findFirst().orElse(null);
            } catch (IOException ignored) { }
        }
        return null;
    }

    private static Path findDimensionDirectory(Path worldRoot, ClientLevel level) {
        String dimensionId = level.dimension().location().toString();
        String preferredName = preferredDimensionDirectoryName(dimensionId);

        Path preferred = worldRoot.resolve(preferredName);
        if (Files.isDirectory(preferred) && matchesDimension(preferred, dimensionId)) return preferred;

        // Xaero documents null/DIM-1/DIM1 for the three vanilla dimensions and
        // dimension_config.txt as the authoritative mapping for other dimensions.
        try (Stream<Path> children = Files.list(worldRoot)) {
            List<Path> matches = children
                    .filter(Files::isDirectory)
                    .filter(path -> matchesDimension(path, dimensionId))
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();
            if (!matches.isEmpty()) return matches.get(0);
        } catch (IOException ignored) { }

        // Very old/partially-written Xaero layouts can lack dimension_config.txt.
        // Keep the documented names as a safe fallback rather than guessing from
        // Minecraft chunk data or loading any chunks.
        if ("minecraft:overworld".equals(dimensionId)) {
            Path nullDir = worldRoot.resolve("null");
            if (Files.isDirectory(nullDir)) return nullDir;
        } else if ("minecraft:the_nether".equals(dimensionId)) {
            Path nether = worldRoot.resolve("DIM-1");
            if (Files.isDirectory(nether)) return nether;
        } else if ("minecraft:the_end".equals(dimensionId)) {
            Path end = worldRoot.resolve("DIM1");
            if (Files.isDirectory(end)) return end;
        }
        return null;
    }

    private static Path findBestInstance(Path dimensionDirectory) {
        try (Stream<Path> children = Files.list(dimensionDirectory)) {
            List<Path> instances = children
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();

            Path defaultInstance = instances.stream()
                    .filter(path -> path.getFileName().toString().equals("mw$default"))
                    .filter(path -> countRegionFiles(path) > 0)
                    .findFirst().orElse(null);
            if (defaultInstance != null) return defaultInstance;

            Path any = instances.stream()
                    .filter(path -> countRegionFiles(path) > 0)
                    .findFirst().orElse(null);
            if (any != null) return any;

            return countRegionFiles(dimensionDirectory) > 0 ? dimensionDirectory : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int countRegionFiles(Path instance) {
        try (Stream<Path> files = Files.walk(instance, 4)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".xwmc") || name.endsWith(".xwmc.outdated");
                    })
                    .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static boolean matchesDimension(Path directory, String dimensionId) {
        Path config = directory.resolve("dimension_config.txt");
        if (!Files.isRegularFile(config)) return false;
        try {
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                if (line.startsWith("dimensionTypeId:")) {
                    return line.substring("dimensionTypeId:".length()).trim().equals(dimensionId);
                }
            }
        } catch (IOException ignored) { }
        return false;
    }

    private static boolean containsDimensionData(Path worldRoot, String dimensionMarker) {
        try (Stream<Path> files = Files.walk(worldRoot, 4)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("dimension_config.txt"))
                    .anyMatch(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8).contains(dimensionMarker);
                        } catch (IOException ignored) {
                            return false;
                        }
                    });
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String levelDimensionMarker(Minecraft minecraft) {
        return minecraft.getSingleplayerServer() == null
                ? "minecraft:overworld"
                : "minecraft:overworld";
    }

    private static String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private static String readLevelId(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        Path saveRoot = minecraft.gameDirectory.toPath()
                .resolve("saves")
                .resolve(singleplayerWorldName(minecraft));
        Path file = saveRoot.resolve("xaeromap.txt");
        if (!Files.isRegularFile(file)) return "unknown";
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("id:")) return trimmed.substring(3).trim();
                if (trimmed.startsWith("id=")) return trimmed.substring(3).trim();
            }
        } catch (IOException ignored) { }
        return "unknown";
    }

    private static String preferredDimensionDirectoryName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "null";
            case "minecraft:the_nether" -> "DIM-1";
            case "minecraft:the_end" -> "DIM1";
            default -> id.replace(':', '$').replace('/', '_').replace('\\', '_');
        };
    }

    private static String sanitizeServerAddress(String address) {
        return address.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private static String normalizeWorldFolderName(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replaceAll("[^a-z0-9_.$]", "_");
    }

    private static long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }
}
