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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Locates Xaero World Map data without a hard Xaero dependency. */
public final class XaeroWorldMapLocator {
    private static final Pattern LEVEL_ID = Pattern.compile("^\\s*id\\s*:\\s*(-?\\d+)\\s*$");
    private static final Pattern CACHE_DIRECTORY = Pattern.compile("cache(?:_\\d+)?");

    private XaeroWorldMapLocator() {}

    /**
     * The instance directory is the Xaero world/dimension map root. Modern singleplayer
     * layouts commonly store terrain under cache/1/*.xwmc and conversion leftovers under
     * cache_1/*.xwmc or *.xwmc.outdated.
     */
    public record MapInstance(Path root, Path dimensionDirectory, Path instanceDirectory,
                              String levelId, int regionFiles, int outdatedFiles) {}

    public static Optional<MapInstance> locate(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return Optional.empty();

        Path root = findRoot(minecraft.gameDirectory.toPath());
        if (root == null) return Optional.empty();

        Path serverRoot = findServerRoot(root, minecraft);
        if (serverRoot == null) return Optional.empty();

        Path dimensionDirectory = serverRoot.resolve(dimensionDirectoryName(level));
        if (!Files.isDirectory(dimensionDirectory)) return Optional.empty();

        Path instance = findBestInstance(dimensionDirectory);
        if (instance == null) return Optional.empty();

        int regionFiles = countTerrainFiles(instance, false);
        int outdatedFiles = countTerrainFiles(instance, true);
        if (regionFiles == 0 && outdatedFiles == 0) return Optional.empty();

        return Optional.of(new MapInstance(root, dimensionDirectory, instance,
                readLevelId(minecraft), regionFiles, outdatedFiles));
    }

    private static Path findRoot(Path gameDirectory) {
        Path modern = gameDirectory.resolve("xaero").resolve("world-map");
        if (Files.isDirectory(modern)) return modern;
        Path legacy = gameDirectory.resolve("XaeroWorldMap");
        return Files.isDirectory(legacy) ? legacy : null;
    }

    private static Path findServerRoot(Path root, Minecraft minecraft) {
        String preferred;
        if (minecraft.getCurrentServer() != null) {
            preferred = "Multiplayer_" + sanitizeServerAddress(minecraft.getCurrentServer().ip);
        } else {
            preferred = singleplayerWorldName(minecraft);
        }

        if (preferred != null && !preferred.isBlank()) {
            Path candidate = root.resolve(preferred);
            if (Files.isDirectory(candidate)) return candidate;

            if (minecraft.getCurrentServer() == null) {
                Path prefixed = root.resolve("Singleplayer_" + normalizeWorldFolderName(preferred));
                if (Files.isDirectory(prefixed)) return prefixed;
            }
        }

        if (minecraft.getCurrentServer() == null) {
            String normalized = normalizeWorldFolderName(singleplayerWorldName(minecraft));
            try (Stream<Path> children = Files.list(root)) {
                return children.filter(Files::isDirectory)
                        .filter(path -> {
                            String folder = normalizeWorldFolderName(path.getFileName().toString());
                            return folder.equals(normalized)
                                    || folder.equals("singleplayer_" + normalized);
                        })
                        .findFirst().orElse(null);
            } catch (IOException ignored) {
                return null;
            }
        }

        return null;
    }

    private static String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    /** Maps a Minecraft dimension key to Xaero's directory naming convention. */
    private static String dimensionDirectoryName(ClientLevel level) {
        String dimensionId = level.dimension().location().toString();
        return switch (dimensionId) {
            case "minecraft:overworld" -> "null";
            case "minecraft:the_nether" -> "DIM-1";
            case "minecraft:the_end" -> "DIM1";
            default -> sanitizeDimension(dimensionId);
        };
    }

    private static Path findBestInstance(Path dimensionDirectory) {
        try (Stream<Path> children = Files.list(dimensionDirectory)) {
            List<Path> instances = children.filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();

            Path converted = instances.stream()
                    .filter(path -> path.getFileName().toString().equals("cm$converted"))
                    .filter(XaeroWorldMapLocator::hasTerrainData)
                    .findFirst().orElse(null);
            if (converted != null) return converted;

            Path defaultInstance = instances.stream()
                    .filter(path -> path.getFileName().toString().equals("mw$default"))
                    .filter(XaeroWorldMapLocator::hasTerrainData)
                    .findFirst().orElse(null);
            if (defaultInstance != null) return defaultInstance;

            Path anyMap = instances.stream()
                    .filter(path -> path.getFileName().toString().startsWith("mw"))
                    .filter(XaeroWorldMapLocator::hasTerrainData)
                    .findFirst().orElse(null);
            if (anyMap != null) return anyMap;

            // Modern singleplayer layouts put cache/, cache_1/, etc. directly in the
            // dimension directory. Do not descend into caves/; those are separate maps.
            return hasTerrainData(dimensionDirectory) ? dimensionDirectory : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean hasTerrainData(Path instance) {
        return countTerrainFiles(instance, false) > 0 || countTerrainFiles(instance, true) > 0;
    }

    private static int countTerrainFiles(Path instance, boolean outdated) {
        try (Stream<Path> files = Files.walk(instance, 4)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> !containsCavesDirectory(instance, path))
                    .filter(path -> isCachePath(instance, path))
                    .filter(path -> outdated
                            ? path.getFileName().toString().endsWith(".xwmc.outdated")
                            : path.getFileName().toString().endsWith(".xwmc"))
                    .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static boolean isCachePath(Path instance, Path file) {
        Path relative = instance.relativize(file);
        if (relative.getNameCount() < 2) return false;
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        return CACHE_DIRECTORY.matcher(first).matches();
    }

    private static boolean containsCavesDirectory(Path instance, Path file) {
        Path relative = instance.relativize(file);
        for (Path part : relative) {
            if (part.toString().equalsIgnoreCase("caves")) return true;
        }
        return false;
    }

    private static String readLevelId(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        Path saveRoot = minecraft.gameDirectory.toPath().resolve("saves").resolve(singleplayerWorldName(minecraft));
        Path file = saveRoot.resolve("xaeromap.txt");
        if (!Files.isRegularFile(file)) return "unknown";
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher matcher = LEVEL_ID.matcher(line);
                if (matcher.matches()) return matcher.group(1);
            }
        } catch (IOException ignored) { }
        return "unknown";
    }

    private static long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    private static String sanitizeServerAddress(String address) {
        return address.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private static String sanitizeDimension(String id) {
        return id.replace(':', '$').replace('/', '_').replace('\\', '_');
    }

    private static String normalizeWorldFolderName(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_.$]", "_");
    }
}
