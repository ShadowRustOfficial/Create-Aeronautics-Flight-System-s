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

/** Locates Xaero World Map data without a hard Xaero dependency. */
public final class XaeroWorldMapLocator {
    private static final Pattern LEVEL_ID = Pattern.compile("^\\s*id\\s*:\\s*(-?\\d+)\\s*$");

    private XaeroWorldMapLocator() {}

    public record MapInstance(Path root, Path dimensionDirectory, Path instanceDirectory,
                              String levelId, int regionFiles) {}

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
        int regionFiles = countRegionFiles(instance);
        if (regionFiles == 0) return Optional.empty();
        return Optional.of(new MapInstance(root, dimensionDirectory, instance, readLevelId(minecraft), regionFiles));
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
        }

        // Xaero's folder naming can differ from the Minecraft level name by replacing
        // spaces/special characters or by using a Singleplayer_ prefix. Find the actual
        // existing folder rather than requiring one exact spelling.
        if (minecraft.getCurrentServer() == null) {
            String normalized = normalizeWorldFolderName(singleplayerWorldName(minecraft));
            try (Stream<Path> children = Files.list(root)) {
                return children.filter(Files::isDirectory)
                        .filter(path -> normalizeWorldFolderName(path.getFileName().toString()).equals(normalized)
                                || normalizeWorldFolderName(path.getFileName().toString()).equals("singleplayer_" + normalized))
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

    private static Path findBestInstance(Path dimensionDirectory) {
        try (Stream<Path> children = Files.list(dimensionDirectory)) {
            List<Path> instances = children.filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(XaeroWorldMapLocator::lastModified).reversed())
                    .toList();

            // Singleplayer/converted maps are stored differently from multiplayer maps.
            // Prefer converted data when it exists, then the normal default map instance.
            Path converted = instances.stream()
                    .filter(path -> path.getFileName().toString().equals("cm$converted"))
                    .findFirst().orElse(null);
            if (converted != null && countRegionFiles(converted) > 0) return converted;

            Path defaultInstance = instances.stream()
                    .filter(path -> path.getFileName().toString().equals("mw$default"))
                    .findFirst().orElse(null);
            if (defaultInstance != null && countRegionFiles(defaultInstance) > 0) return defaultInstance;

            Path anyMap = instances.stream()
                    .filter(path -> path.getFileName().toString().startsWith("mw"))
                    .filter(path -> countRegionFiles(path) > 0)
                    .findFirst().orElse(null);
            if (anyMap != null) return anyMap;

            // Some Xaero singleplayer layouts keep converted/region data directly in
            // the dimension directory. Support that layout as well.
            return countRegionFiles(dimensionDirectory) > 0 ? dimensionDirectory : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int countRegionFiles(Path instance) {
        try (Stream<Path> files = Files.walk(instance, 4)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .count();
        } catch (IOException ignored) { return 0; }
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
        return value.toLowerCase(java.util.Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_.$]", "_");
    }
}
