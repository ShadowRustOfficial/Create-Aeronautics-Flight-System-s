package com.flightcomputer.client.map;

import com.flightcomputer.map.MapMarker;
import com.flightcomputer.map.MarkerCategory;
import com.flightcomputer.map.MarkerRegistry;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.List;

/**
 * Optional Waystones integration. Singleplayer snapshots the server-side database on the
 * server thread; multiplayer uses the client-visible activated list. No Waystones files are
 * modified and the integration is a no-op when the mod is absent.
 */
public final class WaystoneMapProvider {
    private static final long RESCAN_TICKS = 20L;

    private volatile List<WaystoneSnapshot> snapshot = List.of();
    private long nextScan;
    private volatile boolean serverRequestPending;

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null || !ModList.get().isLoaded("waystones")) {
            MarkerRegistry.clearCategory(MarkerCategory.WAYSTONE);
            return;
        }

        long now = level.getGameTime();
        if (now >= nextScan) {
            nextScan = now + RESCAN_TICKS;
            requestSnapshot(minecraft);
        }

        String dimensionId = level.dimension().location().toString();
        MarkerRegistry.clearCategory(MarkerCategory.WAYSTONE);
        for (WaystoneSnapshot waystone : snapshot) {
            if (!waystone.valid || !dimensionId.equals(waystone.dimensionId)) continue;
            MarkerRegistry.put(new MapMarker(
                    "waystone:" + waystone.id,
                    waystone.name,
                    MarkerCategory.WAYSTONE,
                    waystone.x,
                    waystone.y,
                    waystone.z,
                    waystone.dimensionId));
        }
    }

    public void clear() {
        snapshot = List.of();
        nextScan = 0L;
        serverRequestPending = false;
        MarkerRegistry.clearCategory(MarkerCategory.WAYSTONE);
    }

    private void requestSnapshot(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) {
            if (serverRequestPending) return;
            serverRequestPending = true;
            var server = minecraft.getSingleplayerServer();
            server.execute(() -> {
                try {
                    snapshot = WaystonesAPI.getAllWaystones(server)
                            .map(WaystoneMapProvider::snapshot)
                            .toList();
                } finally {
                    serverRequestPending = false;
                }
            });
            return;
        }

        if (minecraft.player != null) {
            snapshot = WaystonesAPI.getActivatedWaystones(minecraft.player).stream()
                    .map(WaystoneMapProvider::snapshot)
                    .toList();
        }
    }

    private static WaystoneSnapshot snapshot(Waystone waystone) {
        var pos = waystone.getPos();
        return new WaystoneSnapshot(
                waystone.getWaystoneUid().toString(),
                waystone.getEffectiveName().getString(),
                waystone.getDimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(),
                waystone.isValid());
    }

    private record WaystoneSnapshot(String id, String name, String dimensionId,
                                    int x, int y, int z, boolean valid) { }
}