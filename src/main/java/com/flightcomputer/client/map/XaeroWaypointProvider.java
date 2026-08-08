package com.flightcomputer.client.map;

import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Reads Xaero's persistent waypoint file without depending on Xaero's classes. */
public final class XaeroWaypointProvider {
    private static final long RESCAN_TICKS = 20L;

    private long nextScan;
    private Path lastFile;
    private long lastModified = Long.MIN_VALUE;

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null || minecraft.player == null || minecraft.gameDirectory == null) return;
        if (minecraft.level != level) return;

        long now = minecraft.level.getGameTime();
        if (now < nextScan) return;
        nextScan = now + RESCAN_TICKS;

        Path file = locateWaypointFile(minecraft, level);
        if (file == null) {
            MarkerRegistry.clearCategory(MarkerCategory.XAERO_WAYPOINT);
            lastFile = null;
            lastModified = Long.MIN_VALUE;
            return;
        }

        long modified = lastModified(file);
        if (file.equals(lastFile) && modified == lastModified) return;
        lastFile = file;
        lastModified = modified;

        load(file, level);
    }

    public void clear() {
        nextScan = 0L;
        lastFile = null;
        lastModified = Long.MIN_VALUE;
        MarkerRegistry.clearCategory(MarkerCategory.XAERO_WAYPOINT);
    }

    private void load(Path file, ClientLevel level) {
        MarkerRegistry.clearCategory(MarkerCategory.XAERO_WAYPOINT);
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String dimensionId = level.dimension().location().toString();
            int index = 0;
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split(":", -1);
                if (fields.length < 14 || !fields[0].equalsIgnoreCase("waypoint")) continue;

                String name = fields[1].replace("\\:", ":");
                int x = parseInt(fields[2]);
                int y = parseInt(fields[3]);
                int z = parseInt(fields[4]);
                String id = "xaero:" + safeId(name) + ":" + x + ":" + y + ":" + z + ":" + index;
                MarkerRegistry.put(new MapMarker(id, name, MarkerCategory.XAERO_WAYPOINT,
                        x, y, z, dimensionId));
                index++;
            }
        } catch (IOException ignored) {
            // Keep the last known file association but leave the category empty on read failure.
        }
    }

    private Path locateWaypointFile(Minecraft minecraft, ClientLevel level) {
        Path root = minecraft.gameDirectory.toPath().resolve("xaero").resolve("minimap");
        if (!Files.isDirectory(root)) return null;

        String world = minecraft.getCurrentServer() != null
                ? "Multiplayer_" + sanitizeServerAddress(minecraft.getCurrentServer().ip)
                : singleplayerWorldName(minecraft);

        String dimension = switch (level.dimension().location().toString()) {
            case "minecraft:overworld" -> "dim%0";
            case "minecraft:the_nether" -> "dim%-1";
            case "minecraft:the_end" -> "dim%1";
            default -> null;
        };
        if (dimension == null) return null;

        Path direct = root.resolve(world).resolve(dimension).resolve("waypoints.txt");
        if (Files.isRegularFile(direct)) return direct;

        String normalized = normalize(world);
        try (var children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .filter(path -> normalize(path.getFileName().toString()).equals(normalized))
                    .map(path -> path.resolve(dimension).resolve("waypoints.txt"))
                    .filter(Files::isRegularFile)
                    .findFirst().orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return Long.MIN_VALUE; }
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String safeId(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
    }

    private String sanitizeServerAddress(String address) {
        return address.replace(':', '_').replace('/', '_').replace('\\', '_');
    }
}