package com.flightcomputer.client.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.region.MapTile;
import xaero.map.region.MapTileChunk;
import xaero.map.region.texture.LeafRegionTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Native Xaero World Map 1.44.2 terrain adapter. */
public final class XaeroMapDataProvider implements FlightMapDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TILE_SIDE = 16;
    private static final int TEXTURE_SIDE = 64;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int MAX_PENDING_PER_TICK = 64;
    private static final int RETRY_INTERVAL_TICKS = 10;

    private final Map<Long, int[]> chunkTiles = new LinkedHashMap<>();
    private final Set<Long> decodedKeys = new HashSet<>();
    private final Set<Long> pendingChunks = new HashSet<>();
    private final ArrayDeque<Long> pendingQueue = new ArrayDeque<>();
    private String activeIdentity;
    private long tickCounter;
    private long lastRetryTick;
    private String diagnosticReport = "Xaero provider not initialized.";

    private int mapTileNull;
    private int mapTileNotLoaded;
    private int mapChunkNull;
    private int textureNull;
    private int bufferNull;
    private int decodedCount;

    @Override
    public int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int[] cached = chunkTiles.get(key);
        if (cached != null) return cached;
        int[] decoded = readNativeChunk(level, chunkX, chunkZ);
        if (decoded != null) {
            chunkTiles.put(key, decoded);
            decodedKeys.add(key);
            pendingChunks.remove(key);
            decodedCount++;
            return decoded;
        }
        queueChunk(key);
        return null;
    }

    @Override
    public void tick(ClientLevel level) {
        ensureLevel(level);
        tickCounter++;
        if (level == null || Minecraft.getInstance().player == null) return;

        MapProcessor processor = getProcessor(level);
        if (processor == null) {
            updateDiagnostic("Xaero session/processor unavailable.\nOpen the World Map once to initialise it.");
            return;
        }

        /* Retry failed native reads at a controlled rate instead of hammering Xaero every tick. */
        if (tickCounter - lastRetryTick >= RETRY_INTERVAL_TICKS) {
            lastRetryTick = tickCounter;
            int processed = 0;
            while (processed++ < MAX_PENDING_PER_TICK && !pendingQueue.isEmpty()) {
                long key = pendingQueue.pollFirst();
                pendingChunks.remove(key);
                if (chunkTiles.containsKey(key)) continue;
                int[] decoded = readNativeChunk(level, ChunkPos.getX(key), ChunkPos.getZ(key));
                if (decoded != null) {
                    chunkTiles.put(key, decoded);
                    decodedKeys.add(key);
                    decodedCount++;
                } else {
                    queueChunk(key);
                }
            }
        }

        updateDiagnostic("Xaero native terrain adapter"
                + "\nworld=" + safe(processor.getCurrentWorldId())
                + "\ndimension=" + safe(processor.getCurrentDimId())
                + "\nmap=" + safe(processor.getCurrentMWId())
                + "\nloaded=" + chunkTiles.size()
                + "\npending=" + pendingQueue.size()
                + "\nmapTileNull=" + mapTileNull
                + "\nmapTileNotLoaded=" + mapTileNotLoaded
                + "\nmapChunkNull=" + mapChunkNull
                + "\ntextureNull=" + textureNull
                + "\nbufferNull=" + bufferNull
                + "\ndecoded=" + decodedCount);
    }

    public Map<Long, int[]> drainDecodedTiles() {
        Map<Long, int[]> result = new LinkedHashMap<>();
        for (Long key : decodedKeys) {
            int[] tile = chunkTiles.get(key);
            if (tile != null) result.put(key, tile);
        }
        decodedKeys.clear();
        return result;
    }

    public String diagnostics() { return diagnosticReport; }

    @Override
    public void clear() {
        chunkTiles.clear();
        decodedKeys.clear();
        pendingChunks.clear();
        pendingQueue.clear();
        activeIdentity = null;
        tickCounter = 0L;
        lastRetryTick = 0L;
        mapTileNull = 0;
        mapTileNotLoaded = 0;
        mapChunkNull = 0;
        textureNull = 0;
        bufferNull = 0;
        decodedCount = 0;
        diagnosticReport = "Xaero provider cleared.";
    }

    private void ensureLevel(ClientLevel level) {
        if (level == null) return;
        String identity = buildIdentity(Minecraft.getInstance(), level);
        if (identity.equals(activeIdentity)) return;
        clear();
        activeIdentity = identity;
        diagnosticReport = "Waiting for Xaero World Map session\nworld=" + identity
                + "\ndimension=" + level.dimension().location();
    }

    private int[] readNativeChunk(ClientLevel level, int chunkX, int chunkZ) {
        try {
            MapProcessor processor = getProcessor(level);
            if (processor == null) return null;
            if (!processor.isMapWorldUsable()) {
                updateDiagnostic("Xaero map world is not usable yet.");
                return null;
            }
            if (processor.getWorld() != level) {
                updateDiagnostic("Xaero processor is attached to a different client world.");
                return null;
            }

            int caveLayer = processor.getCurrentCaveLayer();
            MapTile tile = processor.getMapTile(chunkX, chunkZ, caveLayer);
            if (tile == null) {
                mapTileNull++;
                return null;
            }
            if (!tile.isLoaded()) {
                mapTileNotLoaded++;
                return null;
            }

            MapTileChunk tileChunk = processor.getMapChunk(chunkX >> 2, chunkZ >> 2, caveLayer);
            if (tileChunk == null) {
                mapChunkNull++;
                return null;
            }
            LeafRegionTexture texture = tileChunk.getLeafTexture();
            if (texture == null) {
                textureNull++;
                return null;
            }

            // Do not require GL upload state. The CPU-side direct buffer is the data we
            // actually consume and can be ready before Xaero's render upload completes.
            ByteBuffer source = texture.getDirectColorBuffer();
            if (source == null) {
                bufferNull++;
                return null;
            }

            int requiredBytes = TEXTURE_SIDE * TEXTURE_SIDE * BYTES_PER_PIXEL;
            if (source.capacity() < requiredBytes) {
                updateDiagnostic("Xaero color buffer is smaller than expected.\ncapacity=" + source.capacity()
                        + "\nrequired=" + requiredBytes);
                return null;
            }

            int localTileX = Math.floorMod(chunkX, 4);
            int localTileZ = Math.floorMod(chunkZ, 4);
            int baseX = localTileX * TILE_SIDE;
            int baseZ = localTileZ * TILE_SIDE;
            int[] result = new int[TILE_SIDE * TILE_SIDE];
            ByteBuffer pixels = source.duplicate().order(ByteOrder.BIG_ENDIAN);

            for (int z = 0; z < TILE_SIDE; z++) {
                for (int x = 0; x < TILE_SIDE; x++) {
                    int pixel = (baseZ + z) * TEXTURE_SIDE + baseX + x;
                    result[z * TILE_SIDE + x] = pixels.getInt(pixel * BYTES_PER_PIXEL);
                }
            }

            return result;
        } catch (RuntimeException e) {
            LOGGER.debug("[FlightComputer] Xaero native terrain tile is not ready", e);
            updateDiagnostic("Xaero native tile read deferred: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private MapProcessor getProcessor(ClientLevel level) {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return null;
        MapProcessor processor = session.getMapProcessor();
        if (processor == null || processor.getWorld() != level) return null;
        return processor;
    }

    private void queueChunk(long key) {
        if (pendingChunks.add(key)) pendingQueue.addLast(key);
    }

    private String buildIdentity(Minecraft minecraft, ClientLevel level) {
        String world = minecraft.getCurrentServer() != null
                ? "server:" + minecraft.getCurrentServer().ip
                : "singleplayer:" + singleplayerWorldName(minecraft);
        return world + "|" + level.dimension().location();
    }

    private String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private String safe(String value) { return value == null || value.isBlank() ? "<none>" : value; }
    private void updateDiagnostic(String value) { diagnosticReport = value + "\ntick=" + tickCounter; }
}
