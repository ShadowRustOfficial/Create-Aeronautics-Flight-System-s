package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Optional Xaero waypoint integration.
 *
 * We intentionally read Xaero's own persistent waypoint file rather than depending on unstable
 * internal manager classes. This is the same strategy used by the earlier working integration and
 * keeps Xaero optional at runtime. The file is read only on the client and never modified.
 */
public final class WaypointMapProvider {
    private static final long RESCAN_TICKS = 20L;
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
        load(file, level);
    }

    public List<FlightMapMarker> markers() {
        return Collections.unmodifiableList(new ArrayList<>(markers));
    }

    public boolean isAvailable() { return lastFile != null; }

    public void clear() {
        markers.clear();
        nextRefreshTick = 0L;
        lastFile = null;
        lastModified = Long.MIN_VALUE;
    }

    private void load(Path file, ClientLevel level) {
        markers.clear();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int index = 0;
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split(":", -1);
                if (fields.length < 14 || !fields[0].equalsIgnoreCase("waypoint")) continue;

                String name = fields[1].replace("\\:", ":");
                Integer x = parseInt(fields[2]);
                Integer y = parseInt(fields[3]);
                Integer z = parseInt(fields[4]);
                if (x == null || y == null || z == null) continue;

                markers.add(new FlightMapMarker(FlightMapMarker.Type.WAYPOINT,
                        name.isBlank() ? "Waypoint" : name, x + 0.5D, y + 0.5D, z + 0.5D));
                index++;
            }
        } catch (IOException ignored) {
            // Optional integrations must never take down the client. Keep an empty snapshot.
            markers.clear();
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

    private Integer parseInt(String value) {
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
    }

    private String sanitizeServerAddress(String address) {
        return address.replace(':', '_').replace('/', '_').replace('\\', '_');
    }
}
