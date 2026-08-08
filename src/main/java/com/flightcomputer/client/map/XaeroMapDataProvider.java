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

/**
 * Native Xaero World Map terrain provider.
 *
 * Xaero World Map 1.44.2 already owns the decoded map tiles and their 64x64
 * colour textures. We consume that in-memory representation instead of
 * reverse-engineering .xwmc/cache.xaero files or scanning Minecraft chunks.
 *
 * Xaero is a hard runtime dependency for this integration, so this class is
 * intentionally compiled against Xaero's public map classes.
 */
public final class XaeroMapDataProvider implements FlightMapDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int XAERO_TILE_SIDE = 16;
    private static final int XAERO_TEXTURE_SIDE = 64;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int MAX_PENDING_PER_TICK = 64;

    private final Map<Long, int[]> chunkTiles = new LinkedHashMap<>();
    private final Set<Long> decodedKeys = new HashSet<>();
    private final Set<Long> pendingChunks = new HashSet<>();
    private final ArrayDeque<Long> pendingQueue = new ArrayDeque<>();

    private String activeIdentity;
    private long tickCounter;
    private String diagnosticReport = "Xaero provider not initialized.";

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
            updateDiagnostic("Xaero World Map session/processor is not ready.");
            return;
        }

        int processed = 0;
        while (processed < MAX_PENDING_PER_TICK && !pendingQueue.isEmpty()) {
            long key = pendingQueue.pollFirst();
            pendingChunks.remove(key);

            if (chunkTiles.containsKey(key)) {
                processed++;
                continue;
            }

            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            int[] decoded = readNativeChunk(level, chunkX, chunkZ);
            if (decoded != null) {
                chunkTiles.put(key, decoded);
                decodedKeys.add(key);
            } else {
                // Xaero can finish processing/uploading a tile after our request.
                // Requeue it so the terrain appears as soon as its native texture
                // becomes available rather than getting permanently stuck.
                queueChunk(key);
            }
            processed++;
        }

        if (pendingQueue.isEmpty()) {
            updateDiagnostic("Xaero native terrain ready"
                    + "\nworld=" + safe(processor.getCurrentWorldId())
                    + "\ndimension=" + safe(processor.getCurrentDimId())
                    + "\nmap=" + safe(processor.getCurrentMWId())
                    + "\nloadedChunks=" + chunkTiles.size()
                    + "\npending=0");
        }
    }

    /** Transfers decoded 16x16 chunk tiles into TerrainMapCache. */
    public Map<Long, int[]> drainDecodedTiles() {
        Map<Long, int[]> result = new LinkedHashMap<>();
        for (Long key : decodedKeys) {
            int[] tile = chunkTiles.get(key);
            if (tile != null) result.put(key, tile);
        }
        decodedKeys.clear();
        return result;
    }

    public String diagnostics() {
        return diagnosticReport;
    }

    @Override
    public void clear() {
        chunkTiles.clear();
        decodedKeys.clear();
        pendingChunks.clear();
        pendingQueue.clear();
        activeIdentity = null;
        tickCounter = 0L;
        diagnosticReport = "Xaero provider cleared.";
    }

    private void ensureLevel(ClientLevel level) {
        if (level == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        String identity = buildIdentity(minecraft, level);
        if (identity.equals(activeIdentity)) return;

        chunkTiles.clear();
        decodedKeys.clear();
        pendingChunks.clear();
        pendingQueue.clear();

        activeIdentity = identity;
        diagnosticReport = "Waiting for Xaero World Map session"
                + "\nworld=" + identity
                + "\ndimension=" + level.dimension().location();
    }

    private int[] readNativeChunk(ClientLevel level, int chunkX, int chunkZ) {
        try {
            MapProcessor processor = getProcessor(level);
            if (processor == null || !processor.isMapWorldUsable()) return null;
            if (processor.getWorld() != level) return null;

            int caveLayer = processor.getCurrentCaveLayer();
            MapTile tile = processor.getMapTile(chunkX, chunkZ, caveLayer);
            if (tile == null || !tile.isLoaded()) return null;

            MapTileChunk tileChunk = processor.getMapChunk(chunkX >> 2, chunkZ >> 2, caveLayer);
            if (tileChunk == null) return null;

            LeafRegionTexture texture = tileChunk.getLeafTexture();
            if (texture == null || !texture.isUploaded()) return null;

            ByteBuffer source = texture.getDirectColorBuffer();
            if (source == null) return null;

            int requiredBytes = XAERO_TEXTURE_SIDE * XAERO_TEXTURE_SIDE * BYTES_PER_PIXEL;
            if (source.capacity() < requiredBytes) {
                updateDiagnostic("Xaero native texture buffer too small"
                        + "\nchunk=" + chunkX + "," + chunkZ
                        + "\ncapacity=" + source.capacity()
                        + "\nrequired=" + requiredBytes);
                return null;
            }

            int localTileX = chunkX & 3;
            int localTileZ = chunkZ & 3;
            int baseX = localTileX * XAERO_TILE_SIDE;
            int baseZ = localTileZ * XAERO_TILE_SIDE;

            int[] result = new int[XAERO_TILE_SIDE * XAERO_TILE_SIDE];
            ByteBuffer pixels = source.duplicate().order(ByteOrder.BIG_ENDIAN);

            for (int localZ = 0; localZ < XAERO_TILE_SIDE; localZ++) {
                for (int localX = 0; localX < XAERO_TILE_SIDE; localX++) {
                    int pixelIndex = (baseZ + localZ) * XAERO_TEXTURE_SIDE + baseX + localX;
                    result[localZ * XAERO_TILE_SIDE + localX]
                            = pixels.getInt(pixelIndex * BYTES_PER_PIXEL);
                }
            }

            updateDiagnostic("Xaero native terrain tile read"
                    + "\nworld=" + safe(processor.getCurrentWorldId())
                    + "\ndimension=" + safe(processor.getCurrentDimId())
                    + "\nmap=" + safe(processor.getCurrentMWId())
                    + "\nchunk=" + chunkX + "," + chunkZ
                    + "\nxaeroTile=" + tile.getChunkX() + "," + tile.getChunkZ()
                    + "\ntexture=64x64"
                    + "\nformat=" + texture.getColorBufferFormat()
                    + "\ncompressed=" + texture.isColorBufferCompressed());

            return result;
        } catch (RuntimeException e) {
            LOGGER.debug("[FlightComputer] Xaero native terrain tile is not ready", e);
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private void updateDiagnostic(String value) {
        diagnosticReport = value + "\ntick=" + tickCounter;
    }
}
