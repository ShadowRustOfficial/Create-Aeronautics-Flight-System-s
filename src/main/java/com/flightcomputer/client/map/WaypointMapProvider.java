package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional Xaero waypoint integration.
 *
 * Xaero's persistent format is waypoint:name:initials:x:y:z:... . The previous adapter
 * required too many fields and read the initials field as X, so valid waypoints were discarded
 * or decoded to the wrong coordinates. This parser accepts the stable coordinate tuple and
 * tolerates extra fields added by newer Xaero releases.
 */
public final class WaypointMapProvider {
    private static final long RESCAN_TICKS = 10L;
    private final List<FlightMapMarker> markers = new ArrayList<>();
    private long nextRefreshTick;
    private Path lastFile;
    private long lastModified = Long.MIN_VALUE;

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null || minecraft.player == null || minecraft.gameDirectory == null) return;
        if (minecraft.level != level) return;

        long now = level.getGameTime();
        if (now < nextRefreshTick) return;
        nextRefreshTick = now + RESCAN_TICKS;

        Path file = locateWaypointFile(minecraft, level);
        if (file == null) {
            markers.clear();
            lastFile = null;
            lastModified = Long.MIN_VALUE;
            return;
        }

        long modified = lastModified(file);
        if (file.equals(lastFile) && modified == lastModified) return;
        lastFile = file;
        lastModified = modified;
        load(file);
    }

    public List<FlightMapMarker> markers() { return List.copyOf(markers); }
    public boolean isAvailable() { return lastFile != null; }

    public void clear() {
        markers.clear();
        nextRefreshTick = 0L;
        lastFile = null;
        lastModified = Long.MIN_VALUE;
    }

    private void load(Path file) {
        List<FlightMapMarker> next = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || !line.regionMatches(true, 0, "waypoint:", 0, 9)) continue;
                String[] fields = line.split(":", -1);
                if (fields.length < 6) continue;

                Integer x = null, y = null, z = null;
                for (int i = 2; i + 2 < fields.length; i++) {
                    Integer px = parseInt(fields[i]);
                    Integer py = parseInt(fields[i + 1]);
                    Integer pz = parseInt(fields[i + 2]);
                    if (px != null && py != null && pz != null) {
                        x = px; y = py; z = pz;
                        break;
                    }
                }
                if (x == null || y == null || z == null) continue;

                String name = fields[1].replace("\\:", ":").replace("\\\\", "\\");
                next.add(new FlightMapMarker(FlightMapMarker.Type.WAYPOINT,
                        name.isBlank() ? "Waypoint" : name,
                        x + 0.5D, y + 0.5D, z + 0.5D));
            }
        } catch (IOException ignored) {
            // Keep the last valid snapshot on transient filesystem failures.
            return;
        }
        markers.clear();
        markers.addAll(next);
    }

    private Path locateWaypointFile(Minecraft minecraft, ClientLevel level) {
        String world = minecraft.getCurrentServer() != null
                ? "Multiplayer_" + sanitizeServerAddress(minecraft.getCurrentServer().ip)
                : singleplayerWorldName(minecraft);
        String dimension = dimensionFolder(level);
        if (dimension == null) return null;

        List<Path> roots = List.of(
                minecraft.gameDirectory.toPath().resolve("xaero").resolve("minimap"),
                minecraft.gameDirectory.toPath().resolve("XaeroWaypoints")
        );

        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            Path direct = root.resolve(world).resolve(dimension).resolve("waypoints.txt");
            if (Files.isRegularFile(direct)) return direct;

            String normalized = normalize(world);
            try (var children = Files.list(root)) {
                Path match = children.filter(Files::isDirectory)
                        .filter(path -> normalize(path.getFileName().toString()).equals(normalized))
                        .map(path -> path.resolve(dimension).resolve("waypoints.txt"))
                        .filter(Files::isRegularFile)
                        .findFirst().orElse(null);
                if (match != null) return match;
            } catch (IOException ignored) { }
        }
        return null;
    }

    private String dimensionFolder(ClientLevel level) {
        return switch (level.dimension().location().toString()) {
            case "minecraft:overworld" -> "dim%0";
            case "minecraft:the_nether" -> "dim%-1";
            case "minecraft:the_end" -> "dim%1";
            default -> level.dimension().location().toString().replace(':', '_').replace('/', '_');
        };
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

    private Integer parseInt(String value) {
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String normalize(String value) { return value.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_'); }
    private String sanitizeServerAddress(String address) { return address.replace(':', '_').replace('/', '_').replace('\\', '_'); }
}
