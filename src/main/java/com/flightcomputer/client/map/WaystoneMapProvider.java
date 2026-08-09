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
 * Optional Waystones integration.
 *
 * The Flight Computer shows the same Waystones the current player has activated,
 * rather than enumerating every Waystone in the singleplayer/server database.
 * This prevents distant/unavailable Waystones from appearing as false map markers.
 */
public final class WaystoneMapProvider {
    private static final long RESCAN_TICKS = 20L;

    private volatile List<WaystoneSnapshot> snapshot = List.of();
    private long nextScan;

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
        MarkerRegistry.clearCategory(MarkerCategory.WAYSTONE);
    }

    private void requestSnapshot(Minecraft minecraft) {
        if (minecraft.player == null) return;

        // Use the player's activated list in both singleplayer and multiplayer.
        // getAllWaystones(server) includes distant/unavailable destinations and was
        // the reason the Flight Computer could display more Waystones than expected.
        snapshot = WaystonesAPI.getActivatedWaystones(minecraft.player).stream()
                .map(WaystoneMapProvider::snapshot)
                .toList();
    }

    private static WaystoneSnapshot snapshot(Waystone waystone) {
        var pos = waystone.getPos();
        return new WaystoneSnapshot(
                waystone.getWaystoneUid().toString(),
                waystone.getName().getString(),
                waystone.getDimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(),
                waystone.isValid());
    }

    private record WaystoneSnapshot(String id, String name, String dimensionId,
                                    int x, int y, int z, boolean valid) { }
}
