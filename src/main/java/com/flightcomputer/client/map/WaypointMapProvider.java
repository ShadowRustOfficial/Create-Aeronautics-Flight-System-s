package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Client-side read-only adapter for Xaero's persisted waypoint data. */
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

    /** Force an immediate read, used by the Route selector so a first click does not race the scan timer. */
    public void refreshNow(ClientLevel level) {
        nextRefreshTick = 0L;
        tick(level);
    }

    public List<FlightMapMarker> markers() { return List.copyOf(markers); }
    public boolean isAvailable() { return lastFile != null; }
    public void clear() { markers.clear(); nextRefreshTick = 0L; lastFile = null; lastModified = Long.MIN_VALUE; }

    private void load(Path file) {
        List<FlightMapMarker> next = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                if (!line.regionMatches(true, 0, "waypoint:", 0, 9)) continue;
                List<String> fields = splitXaeroFields(line);
                // Current Xaero waypoint records begin:
                // waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:...
                // Older records use the same first six coordinate fields, so this parser deliberately
                // only depends on the stable prefix and ignores newer trailing fields.
                if (fields.size() < 6) continue;
                Integer x = parseInt(fields.get(3));
                Integer y = parseInt(fields.get(4));
                Integer z = parseInt(fields.get(5));
                if (x == null || y == null || z == null) continue;
                if (fields.size() > 6 && Boolean.parseBoolean(fields.get(6))) continue;

                String name = fields.get(1).trim();
                if (name.isBlank()) name = "Waypoint";
                next.add(new FlightMapMarker(FlightMapMarker.Type.WAYPOINT, name, x + .5D, y + .5D, z + .5D));
            }
        } catch (IOException ignored) {
            return;
        }
        markers.clear();
        markers.addAll(next);
    }

    private List<String> splitXaeroFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ':') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        fields.add(current.toString());
        return fields;
    }

    private Path locateWaypointFile(Minecraft minecraft, ClientLevel level) {
        String serverAddress = minecraft.getCurrentServer() == null ? "" : minecraft.getCurrentServer().ip;
        String serverName = minecraft.getCurrentServer() == null ? "" : minecraft.getCurrentServer().name;
        String world = minecraft.getCurrentServer() != null
                ? "Multiplayer_" + sanitizeServerAddress(serverAddress)
                : singleplayerWorldName(minecraft);

        Set<String> worldAliases = new LinkedHashSet<>();
        worldAliases.add(normalize(world));
        if (!serverAddress.isBlank()) {
            String address = sanitizeServerAddress(serverAddress);
            worldAliases.add(normalize("Multiplayer_" + address));
            worldAliases.add(normalize(address));
            String baseDomain = address.split("_", 2)[0];
            if (!baseDomain.isBlank()) worldAliases.add(normalize("Multiplayer_" + baseDomain));
        }
        if (!serverName.isBlank()) worldAliases.add(normalize("Multiplayer_" + serverName));
        Set<String> dimensionAliases = dimensionAliases(level);

        List<Path> roots = List.of(
                minecraft.gameDirectory.toPath().resolve("XaeroWaypoints"),
                minecraft.gameDirectory.toPath().resolve("xaero").resolve("minimap")
        );
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            Path match = findWaypointFile(root, worldAliases, dimensionAliases);
            if (match != null) return match;
        }
        return null;
    }

    private Path findWaypointFile(Path root, Set<String> worldAliases, Set<String> dimensionAliases) {
        try (var worlds = Files.list(root)) {
            for (Path worldDir : worlds.filter(Files::isDirectory).toList()) {
                String worldName = normalize(worldDir.getFileName().toString());
                boolean worldMatch = worldAliases.stream().anyMatch(alias -> worldName.equals(alias) || worldName.contains(alias) || alias.contains(worldName));
                if (!worldMatch) continue;

                // Some Xaero versions keep waypoints directly under the multiplayer folder;
                // newer layouts may put them below a dimension/sub-world directory.
                Path direct = worldDir.resolve("waypoints.txt");
                if (Files.isRegularFile(direct)) return direct;

                try (var dimensions = Files.list(worldDir)) {
                    for (Path dimensionDir : dimensions.filter(Files::isDirectory).toList()) {
                        String dimensionName = normalize(dimensionDir.getFileName().toString());
                        boolean dimensionMatch = dimensionAliases.stream().anyMatch(alias -> dimensionName.equals(alias) || dimensionName.contains(alias) || alias.contains(dimensionName));
                        if (!dimensionMatch) continue;
                        Path file = dimensionDir.resolve("waypoints.txt");
                        if (Files.isRegularFile(file)) return file;
                    }
                }

                // Last-resort compatibility for Xaero sub-world naming changes. We only walk the
                // already-matched server folder, never the entire .minecraft directory.
                try (var files = Files.walk(worldDir, 4)) {
                    Path fallback = files.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().equalsIgnoreCase("waypoints.txt"))
                            .filter(p -> {
                                String path = normalize(p.toString());
                                return dimensionAliases.stream().anyMatch(path::contains) || path.contains("waypoints");
                            })
                            .findFirst().orElse(null);
                    if (fallback != null) return fallback;
                }
            }
        } catch (IOException ignored) { }
        return null;
    }

    private Set<String> dimensionAliases(ClientLevel level) {
        String id = level.dimension().location().toString();
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(normalize(id));
        aliases.add(normalize(id.replace(':', '$')));
        aliases.add(normalize(id.replace(':', '_')));
        switch (id) {
            case "minecraft:overworld" -> { aliases.add("overworld"); aliases.add("dim0"); aliases.add("null"); aliases.add("internal_overworld_waypoints"); }
            case "minecraft:the_nether" -> { aliases.add("the_nether"); aliases.add("the_nether"); aliases.add("dim_1"); aliases.add("internal_the_nether_waypoints"); }
            case "minecraft:the_end" -> { aliases.add("the_end"); aliases.add("dim1"); aliases.add("internal_the_end_waypoints"); }
            default -> { }
        }
        return aliases;
    }

    private String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private long lastModified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException ignored) { return Long.MIN_VALUE; } }
    private Integer parseInt(String value) { try { return Integer.valueOf(value.trim()); } catch (NumberFormatException ignored) { return null; } }
    private String normalize(String value) { return value.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_'); }
    private String sanitizeServerAddress(String address) { return address.replace(':', '_').replace('/', '_').replace('\\', '_'); }
}
