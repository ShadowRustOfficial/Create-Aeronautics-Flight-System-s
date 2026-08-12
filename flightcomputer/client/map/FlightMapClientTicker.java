package com.flightcomputer.client.map;

import com.flightcomputer.FlightComputer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Keeps the native Flight Map cache synchronized with terrain already loaded by the
 * client, independently of whether the navigation GUI is open.
 */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class FlightMapClientTicker {
    private static final LiveWorldMapProvider WARM_PROVIDER = new LiveWorldMapProvider();
    private static ClientLevel activeLevel;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            if (activeLevel != null) {
                LiveWorldMapProvider.clearSessionCache();
                activeLevel = null;
            }
            return;
        }

        if (activeLevel != level) {
            LiveWorldMapProvider.clearSessionCache();
            activeLevel = level;
        }

        // The provider only samples ClientLevel#hasChunk() chunks. Missing chunks are
        // never requested merely to populate the Flight Map.
        WARM_PROVIDER.observeLoadedClientChunks(level);
    }

    private FlightMapClientTicker() {}
}
