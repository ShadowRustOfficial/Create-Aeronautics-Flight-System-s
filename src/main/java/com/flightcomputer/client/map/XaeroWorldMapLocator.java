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

/** Locates Xaero World Map data using the actual client instance/cache layout. */
public final class XaeroWorldMapLocator {
    private static final Pattern LEVEL_ID = Pattern.compile("^\\s*id\\s*:\\s*(-?\\d+)\\s*$");
    private static final Pattern CACHE_DIRECTORY = Pattern.compile("cache(?:_\\d+)?");

    private XaeroWorldMapLocator() {}

    public record MapInstance(Path root, Path dimensionDirectory, Path instanceDirectory,
                              String levelId, int regionFiles, int outdatedFiles) {}

    public static Optional<MapInstance> locate(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null) return Optional.empty();
        Path root = findRoot(minecraft.gameDirectory.toPath());
        if (root == null) return Optional.empty();

        Path serverRoot = findServerRoot(root, minecraft);
        if (serverRoot == null) return Optional.empty();

        Path dimensionDirectory = resolveDimensionDirectory(serverRoot, level);
        if (dimensionDirectory == null) return Optional.empty();

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
        if (minecraft.getCurrentServer() != null) {
            String preferred = "Multiplayer_" + sanitizeServerAddress(minecraft.getCurrentServer().ip);
            Path candidate = root.resolve(preferred);
            return Files.isDirectory(candidate) ? candidate : null;
        }

        String worldName = singleplayerWorldName(minecraft);
        String normalized = normalizeWorldFolderName(worldName);
        Path[] directCandidates = {
                root.resolve(worldName),
                root.resolve("Singleplayer_" + normalized),
                root.resolve("Singleplayer_" + worldName)
        };
        for (Path candidate : directCandidates) if (Files.isDirectory(candidate)) return candidate;

        // Xaero's world directory name can differ from Minecraft's level display name.
        // If the exact name is absent, prefer a directory that actually contains Overworld
        // terrain cache data, using most-recent modification only as a deterministic fallback.
        try (Stream<Path> children = Files.list(root)) {
            List<Path> candidates = children.filter(Files::isDirectory)
                    .filter(path -> hasTerrainData(path.resolve("null")))
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();
            for (Path candidate : candidates) {
                String folder = normalizeWorldFolderName(candidate.getFileName().toString());
                if (folder.equals(normalized) || folder.contains(normalized) || normalized.contains(folder)) return candidate;
            }
            return candidates.isEmpty() ? null : candidates.get(0);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private static Path resolveDimensionDirectory(Path serverRoot, ClientLevel level) {
        String dimensionId = level.dimension().location().toString();
        String preferred = dimensionDirectoryName(level);
        Path direct = serverRoot.resolve(preferred);
        if (Files.isDirectory(direct)) return direct;

        // Custom dimensions may use a sanitized Xaero directory name. Inspect the real
        // dimension_config.txt when available instead of guessing from a display name.
        try (Stream<Path> children = Files.list(serverRoot)) {
            for (Path candidate : (Iterable<Path>) children.filter(Files::isDirectory)::iterator) {
                Path config = candidate.resolve("dimension_config.txt");
                if (!Files.isRegularFile(config)) continue;
                try {
                    String text = Files.readString(config, StandardCharsets.UTF_8);
                    if (text.contains("dimensionTypeId:" + dimensionId) || text.contains(dimensionId)) return candidate;
                } catch (IOException ignored) { }
            }
        } catch (IOException ignored) { }
        return null;
    }

    private static String dimensionDirectoryName(ClientLevel level) {
        return switch (level.dimension().location().toString()) {
            case "minecraft:overworld" -> "null";
            case "minecraft:the_nether" -> "DIM-1";
            case "minecraft:the_end" -> "DIM1";
            default -> sanitizeDimension(level.dimension().location().toString());
        };
    }

    private static Path findBestInstance(Path dimensionDirectory) {
        try (Stream<Path> children = Files.list(dimensionDirectory)) {
            List<Path> instances = children.filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();

            Path converted = instances.stream().filter(path -> path.getFileName().toString().equals("cm$converted"))
                    .filter(XaeroWorldMapLocator::hasTerrainData).findFirst().orElse(null);
            if (converted != null) return converted;
            Path defaultInstance = instances.stream().filter(path -> path.getFileName().toString().equals("mw$default"))
                    .filter(XaeroWorldMapLocator::hasTerrainData).findFirst().orElse(null);
            if (defaultInstance != null) return defaultInstance;
            Path anyMap = instances.stream().filter(path -> path.getFileName().toString().startsWith("mw"))
                    .filter(XaeroWorldMapLocator::hasTerrainData).findFirst().orElse(null);
            if (anyMap != null) return anyMap;
            return hasTerrainData(dimensionDirectory) ? dimensionDirectory : null;
        } catch (IOException ignored) { return null; }
    }

    private static boolean hasTerrainData(Path instance) {
        return countTerrainFiles(instance, false) > 0 || countTerrainFiles(instance, true) > 0;
    }

    private static int countTerrainFiles(Path instance, boolean outdated) {
        if (!Files.isDirectory(instance)) return 0;
        try (Stream<Path> files = Files.walk(instance, 6)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> !containsCavesDirectory(instance, path))
                    .filter(path -> isCachePath(instance, path))
                    .filter(path -> outdated ? path.getFileName().toString().endsWith(".xwmc.outdated")
                            : path.getFileName().toString().endsWith(".xwmc"))
                    .count();
        } catch (IOException ignored) { return 0; }
    }

    private static boolean isCachePath(Path instance, Path file) {
        Path relative = instance.relativize(file);
        if (relative.getNameCount() < 2) return false;
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        return CACHE_DIRECTORY.matcher(first).matches();
    }

    private static boolean containsCavesDirectory(Path instance, Path file) {
        for (Path part : instance.relativize(file)) if (part.toString().equalsIgnoreCase("caves")) return true;
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
        return value.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_")
                .replaceAll("[^a-z0-9_.$]", "_");
    }
}
