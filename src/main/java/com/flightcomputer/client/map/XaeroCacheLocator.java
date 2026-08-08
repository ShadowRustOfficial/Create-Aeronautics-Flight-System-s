package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves Xaero World Map's on-disk data relative to Minecraft's actual game
 * directory. The user's launcher/instance path is never hardcoded.
 *
 * Authoritative 1.21.1 instance layout observed for this integration:
 * <gameDirectory>/xaero/world-map/<world>/<xaero-dimension>/
 *     cache/
 *     cache_1/
 *     caves/
 *
 * For the observed Overworld profile, Xaero stores the dimension directory as
 * "null" while Minecraft's authoritative dimension id is
 * minecraft:overworld. We therefore use the Minecraft dimension id for
 * identity and only use the Xaero directory name as storage metadata.
 */
public final class XaeroCacheLocator {
    private static final Path XAERO_ROOT = Path.of("xaero", "world-map");
    private static final String OVERWORLD_DIMENSION = "minecraft:overworld";
    private static final String OBSERVED_OVERWORLD_STORAGE = "null";

    private XaeroCacheLocator() {}

    public static Snapshot resolve(Minecraft minecraft, ClientLevel level) {
        if (minecraft == null || level == null) return Snapshot.missing("Minecraft level unavailable.");

        Path root = minecraft.gameDirectory.toPath().resolve(XAERO_ROOT);
        if (!Files.isDirectory(root)) {
            return Snapshot.missing("Xaero World Map root not found: " + root);
        }

        Path worldDirectory = resolveWorldDirectory(minecraft, root);
        if (worldDirectory == null) {
            return Snapshot.missing("No active Xaero world directory found under: " + root);
        }

        String dimensionId = dimensionId(level);
        Path dimensionDirectory = resolveDimensionDirectory(worldDirectory, dimensionId);
        if (dimensionDirectory == null) {
            return new Snapshot(root, worldDirectory, null, dimensionId,
                    false, false, false, 0, 0, "No Xaero dimension directory found.");
        }

        Path cache = dimensionDirectory.resolve("cache");
        Path cache1 = dimensionDirectory.resolve("cache_1");
        Path caves = dimensionDirectory.resolve("caves");
        int xwmcCount = countFiles(cache) + countFiles(cache1);
        int caveDirectories = countDirectories(caves);

        return new Snapshot(root, worldDirectory, dimensionDirectory, dimensionId,
                Files.isDirectory(cache), Files.isDirectory(cache1), Files.isDirectory(caves),
                xwmcCount, caveDirectories, "OK");
    }

    private static Path resolveWorldDirectory(Minecraft minecraft, Path root) {
        String expected = null;
        if (minecraft.getSingleplayerServer() != null) {
            expected = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else if (minecraft.getCurrentServer() != null) {
            expected = minecraft.getCurrentServer().ip;
        }

        if (expected != null && !expected.isBlank()) {
            String normalizedExpected = normalize(expected);
            try (Stream<Path> children = Files.list(root)) {
                Optional<Path> exact = children
                        .filter(Files::isDirectory)
                        .filter(path -> normalize(path.getFileName().toString()).equals(normalizedExpected))
                        .findFirst();
                if (exact.isPresent()) return exact.get();
            } catch (IOException ignored) {
                // Fall through to the safe newest-directory selection below.
            }
        }

        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .max(Comparator.comparingLong(XaeroCacheLocator::lastModified));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Path resolveDimensionDirectory(Path worldDirectory, String dimensionId) {
        // First prefer an exact storage directory if Xaero exposes one matching the
        // Minecraft dimension id (including namespace separators converted to '_').
        String normalizedDimension = normalize(dimensionId);
        try (Stream<Path> children = Files.list(worldDirectory)) {
            Optional<Path> exact = children
                    .filter(Files::isDirectory)
                    .filter(path -> normalize(path.getFileName().toString()).equals(normalizedDimension))
                    .findFirst();
            if (exact.isPresent()) return exact.get();
        } catch (IOException ignored) {
            return null;
        }

        // The supplied authoritative instance shows minecraft:overworld stored by
        // Xaero under "null". Keep this mapping deliberately scoped to the observed
        // Overworld representation; other dimensions are discovered rather than guessed.
        if (OVERWORLD_DIMENSION.equals(dimensionId)) {
            Path observed = worldDirectory.resolve(OBSERVED_OVERWORLD_STORAGE);
            if (Files.isDirectory(observed)) return observed;
        }

        // Search dimension_config.txt for the authoritative Minecraft dimension id.
        // This lets future/other Xaero storage names resolve without hardcoding them.
        try (Stream<Path> children = Files.list(worldDirectory)) {
            for (Path candidate : (Iterable<Path>) children.filter(Files::isDirectory)::iterator) {
                Path config = candidate.resolve("dimension_config.txt");
                if (!Files.isRegularFile(config)) continue;
                try {
                    String text = Files.readString(config, StandardCharsets.UTF_8);
                    if (text.contains(dimensionId)) return candidate;
                } catch (IOException ignored) {
                    // Try the next candidate.
                }
            }
        } catch (IOException ignored) {
            // No readable dimension metadata.
        }

        return null;
    }

    private static String dimensionId(ClientLevel level) {
        ResourceLocation location = level.dimension().location();
        return location == null ? "unknown" : location.toString();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(':', '_')
                .replace('/', '_')
                .replace('\\', '_')
                .trim();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static int countFiles(Path directory) {
        if (!Files.isDirectory(directory)) return 0;
        try (Stream<Path> files = Files.walk(directory)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xwmc"))
                    .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static int countDirectories(Path directory) {
        if (!Files.isDirectory(directory)) return 0;
        try (Stream<Path> files = Files.list(directory)) {
            return (int) files.filter(Files::isDirectory).count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    public record Snapshot(
            Path root,
            Path worldDirectory,
            Path dimensionDirectory,
            String minecraftDimensionId,
            boolean cacheFound,
            boolean cache1Found,
            boolean cavesFound,
            int xwmcFileCount,
            int caveDirectoryCount,
            String status) {

        public static Snapshot missing(String status) {
            return new Snapshot(null, null, null, "unknown",
                    false, false, false, 0, 0, status);
        }

        public boolean found() {
            return dimensionDirectory != null;
        }

        public String rootDisplay() {
            return root == null ? "<missing>" : root.toString();
        }

        public String worldDisplay() {
            return worldDirectory == null ? "<missing>" : worldDirectory.getFileName().toString();
        }

        public String dimensionDisplay() {
            return dimensionDirectory == null ? "<missing>" : dimensionDirectory.getFileName().toString();
        }
    }
}
