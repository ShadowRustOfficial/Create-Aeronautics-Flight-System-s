package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable Xaero waypoint reader using the persistent waypoints.txt format. */
public final class XaeroWaypointFileProvider {
    private long nextScan;
    private final List<FlightMapMarker> markers = new ArrayList<>();

    public void tick(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || level == null || mc.level != level || level.getGameTime() < nextScan) return;
        nextScan = level.getGameTime() + 20;
        Path file = locate(mc, level);
        if (file == null) { markers.clear(); return; }
        try {
            List<FlightMapMarker> next = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || !line.startsWith("waypoint:")) continue;
                String[] f = line.split(":", -1);
                if (f.length < 5) continue;
                try {
                    String name = f[1].replace("\\:", ":");
                    double x = Double.parseDouble(f[2].trim()) + .5D;
                    double y = Double.parseDouble(f[3].trim()) + .5D;
                    double z = Double.parseDouble(f[4].trim()) + .5D;
                    next.add(new FlightMapMarker(FlightMapMarker.Type.WAYPOINT, name.isBlank() ? "Waypoint" : name, x, y, z));
                } catch (NumberFormatException ignored) { }
            }
            markers.clear(); markers.addAll(next);
        } catch (Exception ignored) { }
    }

    public List<FlightMapMarker> markers() { return Collections.unmodifiableList(markers); }

    private Path locate(Minecraft mc, ClientLevel level) {
        Path root = mc.gameDirectory.toPath().resolve("xaero").resolve("minimap");
        String world = mc.getCurrentServer() != null ? "Multiplayer_" + mc.getCurrentServer().ip.replace(':','_').replace('/','_').replace('\\','_') : "unknown";
        String dim = switch (level.dimension().location().toString()) {
            case "minecraft:overworld" -> "dim%0";
            case "minecraft:the_nether" -> "dim%-1";
            case "minecraft:the_end" -> "dim%1";
            default -> null;
        };
        if (dim == null) return null;
        Path direct = root.resolve(world).resolve(dim).resolve("waypoints.txt");
        if (Files.isRegularFile(direct)) return direct;
        try (var dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory).map(p -> p.resolve(dim).resolve("waypoints.txt")).filter(Files::isRegularFile).findFirst().orElse(null);
        } catch (Exception ignored) { return null; }
    }
}
