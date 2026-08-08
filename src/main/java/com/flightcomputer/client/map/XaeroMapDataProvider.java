package com.flightcomputer.client.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Filesystem-only Xaero World Map provider.
 *
 * Xaero's current XWMC cache is not a Minecraft chunk/section format. A current
 * cache.xaero entry contains 64x64 ARGB map textures, one texture per 64-block
 * MapTileChunk. The old provider treated those files as 32x32 Minecraft chunks
 * and interpreted arbitrary bytes as block-state records, which caused the
 * patchy green output and colour changes seen while moving.
 *
 * This provider reads the actual 64x64 colour buffers, splits them into the
 * existing Flight Computer 16x16 chunk tiles, and never loads LevelChunks.
 */
public final class XaeroMapDataProvider implements FlightMapDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int REGION_CHUNKS = 32;
    private static final int XAERO_TILE_SIDE = 64;
    private static final int CHUNK_SIDE = 16;
    private static final int TILES_PER_REGION = 8;
    private static final int SUBCHUNKS_PER_TILE = XAERO_TILE_SIDE / CHUNK_SIDE;
    private static final int MAX_REGIONS_PER_TICK = 1;
    private static final long RETRY_DELAY_TICKS = 40L;

    private final Map<Long, int[]> chunkTiles = new LinkedHashMap<>();
    private final Set<Long> decodedKeys = new LinkedHashSet<>();
    private final Set<Long> queuedRegions = new HashSet<>();
    private final Set<Long> decodedRegions = new HashSet<>();
    private final Map<Long, Long> retryAt = new HashMap<>();
    private final ArrayDeque<Long> regionQueue = new ArrayDeque<>();

    private String activeIdentity;
    private XaeroWorldMapLocator.MapInstance activeMap;
    private long tickCounter;
    private String diagnosticReport = "Xaero provider not initialized.";

    @Override
    public int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        ensureLevel(level);

        long key = ChunkPos.asLong(chunkX, chunkZ);
        int[] cached = chunkTiles.get(key);
        if (cached != null) return cached;

        int regionX = Math.floorDiv(chunkX, REGION_CHUNKS);
        int regionZ = Math.floorDiv(chunkZ, REGION_CHUNKS);
        queueRegion(ChunkPos.asLong(regionX, regionZ));
        return null;
    }

    @Override
    public void tick(ClientLevel level) {
        ensureLevel(level);
        tickCounter++;

        int processed = 0;
        while (processed < MAX_REGIONS_PER_TICK && !regionQueue.isEmpty()) {
            long regionKey = regionQueue.pollFirst();
            queuedRegions.remove(regionKey);

            Path regionFile = regionFile(regionKey);
            if (regionFile == null) {
                retryAt.put(regionKey, tickCounter + RETRY_DELAY_TICKS);
                updateDiagnostic("Xaero region not found yet: " + regionName(regionKey));
                processed++;
                continue;
            }

            try {
                DecodeResult result = decodeRegion(regionFile, ChunkPos.getX(regionKey), ChunkPos.getZ(regionKey));
                decodedRegions.add(regionKey);
                retryAt.remove(regionKey);

                boolean coordinateMatch = currentRegionMatches(level, ChunkPos.getX(regionKey), ChunkPos.getZ(regionKey));
                updateDiagnostic(String.format(
                        "Xaero World Map detected\nworld=%s\ndimension=%s\ninstance=%s\nregion=%s\nformat=%d.%d\ntiles=%d\nreadable=%s\nfiles=%s\ncurrentRegion=%s",
                        activeMap == null ? "<none>" : activeMap.dimensionDirectory().getParent(),
                        level.dimension().location(),
                        activeMap == null ? "<none>" : activeMap.instanceDirectory(),
                        regionName(regionKey),
                        result.majorVersion, result.minorVersion,
                        result.tiles.size(),
                        result.tiles.size() > 0,
                        result.tiles.keySet(),
                        coordinateMatch));
            } catch (IOException | RuntimeException e) {
                retryAt.put(regionKey, tickCounter + RETRY_DELAY_TICKS);
                LOGGER.warn("[FlightComputer] Failed to read Xaero XWMC region {}", regionFile, e);
                updateDiagnostic("Xaero XWMC read failed: " + regionFile.getFileName()
                        + " -> " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            }
            processed++;
        }
    }

    /** Transfers decoded 16x16 tiles into TerrainMapCache without exposing mutable internals. */
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
        queuedRegions.clear();
        decodedRegions.clear();
        retryAt.clear();
        regionQueue.clear();
        activeIdentity = null;
        activeMap = null;
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
        queuedRegions.clear();
        decodedRegions.clear();
        retryAt.clear();
        regionQueue.clear();

        activeIdentity = identity;
        activeMap = XaeroWorldMapLocator.locate(level).orElse(null);

        if (activeMap == null) {
            diagnosticReport = "Xaero World Map directory/profile/dimension not found for "
                    + level.dimension().location();
        } else {
            diagnosticReport = "Xaero World Map located\nworld="
                    + activeMap.dimensionDirectory().getParent()
                    + "\ndimension=" + activeMap.dimensionDirectory()
                    + "\ninstance=" + activeMap.instanceDirectory()
                    + "\ncurrentTiles=" + activeMap.regionFiles()
                    + "\noutdatedTiles=" + activeMap.outdatedFiles();
        }
    }

    private String buildIdentity(Minecraft minecraft, ClientLevel level) {
        String server = minecraft.getCurrentServer() != null
                ? minecraft.getCurrentServer().ip
                : "singleplayer:" + singleplayerWorldName(minecraft);
        return server + "|" + level.dimension().location();
    }

    private String singleplayerWorldName(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) return "unknown";
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private void queueRegion(long regionKey) {
        if (decodedRegions.contains(regionKey) || queuedRegions.contains(regionKey)) return;
        long allowedAt = retryAt.getOrDefault(regionKey, 0L);
        if (tickCounter < allowedAt) return;
        queuedRegions.add(regionKey);
        regionQueue.addLast(regionKey);
    }

    private Path regionFile(long regionKey) {
        if (activeMap == null) return null;
        int regionX = ChunkPos.getX(regionKey);
        int regionZ = ChunkPos.getZ(regionKey);
        String filename = regionX + "_" + regionZ + ".xwmc";

        try (var files = Files.walk(activeMap.instanceDirectory(), 5)) {
            List<Path> candidates = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(filename))
                    .filter(this::isCurrentCacheFile)
                    .sorted(Comparator
                            .comparingInt(this::cachePriority)
                            .thenComparing(Comparator.comparingLong(this::lastModified).reversed()))
                    .toList();
            return candidates.isEmpty() ? null : candidates.get(0);
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean isCurrentCacheFile(Path path) {
        Path relative = activeMap.instanceDirectory().relativize(path);
        if (relative.getNameCount() < 2) return false;
        for (Path part : relative) {
            if (part.toString().equalsIgnoreCase("caves")) return false;
        }
        String first = relative.getName(0).toString();
        return first.equals("cache") || first.matches("cache_\\d+");
    }

    private int cachePriority(Path path) {
        Path relative = activeMap.instanceDirectory().relativize(path);
        String first = relative.getName(0).toString();
        if (first.equals("cache") && relative.getNameCount() >= 2) {
            try { return Integer.parseInt(relative.getName(1).toString()); }
            catch (NumberFormatException ignored) { return 400; }
        }
        if (first.equals("cache_")) return 900;
        if (first.matches("cache_\\d+")) {
            try { return 500 + Integer.parseInt(first.substring(6)); }
            catch (NumberFormatException ignored) { return 900; }
        }
        return 900;
    }

    private long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    private DecodeResult decodeRegion(Path file, int regionX, int regionZ) throws IOException {
        byte[] cacheData = readCacheEntry(file);
        if (cacheData.length < 4) throw new IOException("cache.xaero is empty");

        int fullVersion = readInt(cacheData, 0);
        int major = (fullVersion >>> 16) & 0xFFFF;
        int minor = fullVersion & 0xFFFF;

        Map<String, int[]> decoded = new LinkedHashMap<>();
        Set<Integer> seenCoords = new HashSet<>();

        // Xaero's current leaf textures are 64x64 ARGB int pixels. We intentionally
        // recognize the actual texture header rather than trying to interpret the
        // surrounding height/biome payload as block-state records.
        for (int offset = 4; offset + 10 <= cacheData.length; offset++) {
            int coord = cacheData[offset] & 0xFF;
            if (coord > 0x77 || seenCoords.contains(coord)) continue;

            int compression = cacheData[offset + 1] & 0xFF;
            if (compression > 1) continue;

            int format = readInt(cacheData, offset + 2);
            int length = readInt(cacheData, offset + 6);
            if (!isSupportedColorFormat(format) || length <= 0 || length > 4 * 1024 * 1024) continue;
            if (offset + 10L + length > cacheData.length) continue;

            if (compression != 0) {
                // Current 1.44.2 reference files are uncompressed RGBA8/ARGB buffers.
                // Do not guess a decompression algorithm and risk corrupt colours.
                continue;
            }
            if (length != XAERO_TILE_SIDE * XAERO_TILE_SIDE * Integer.BYTES) continue;

            int tileX = coord >>> 4;
            int tileZ = coord & 15;
            if (tileX >= TILES_PER_REGION || tileZ >= TILES_PER_REGION) continue;

            int[] pixels = new int[XAERO_TILE_SIDE * XAERO_TILE_SIDE];
            ByteBuffer buffer = ByteBuffer.wrap(cacheData, offset + 10, length).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < pixels.length; i++) pixels[i] = buffer.getInt();

            decoded.put(tileX + "," + tileZ, pixels);
            seenCoords.add(coord);
            splitIntoChunkTiles(pixels, tileX, tileZ, regionX, regionZ);
        }

        return new DecodeResult(major, minor, decoded);
    }

    private byte[] readCacheEntry(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().equals("cache.xaero")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    return out.toByteArray();
                }
            }
        }
        throw new IOException("missing cache.xaero entry");
    }

    private void splitIntoChunkTiles(int[] pixels, int tileX, int tileZ, int regionX, int regionZ) {
        int baseChunkX = regionX * REGION_CHUNKS + tileX * SUBCHUNKS_PER_TILE;
        int baseChunkZ = regionZ * REGION_CHUNKS + tileZ * SUBCHUNKS_PER_TILE;

        for (int subZ = 0; subZ < SUBCHUNKS_PER_TILE; subZ++) {
            for (int subX = 0; subX < SUBCHUNKS_PER_TILE; subX++) {
                int[] chunk = new int[CHUNK_SIDE * CHUNK_SIDE];
                for (int localZ = 0; localZ < CHUNK_SIDE; localZ++) {
                    int sourceZ = subZ * CHUNK_SIDE + localZ;
                    for (int localX = 0; localX < CHUNK_SIDE; localX++) {
                        int sourceX = subX * CHUNK_SIDE + localX;
                        chunk[localZ * CHUNK_SIDE + localX]
                                = pixels[sourceZ * XAERO_TILE_SIDE + sourceX];
                    }
                }

                long key = ChunkPos.asLong(baseChunkX + subX, baseChunkZ + subZ);
                chunkTiles.put(key, chunk);
                decodedKeys.add(key);
            }
        }
    }

    private boolean isSupportedColorFormat(int format) {
        // GL_RGBA8 is the format used by the supplied Xaero 1.44.2 cache.
        // GL_RGBA is accepted as a harmless compatibility fallback.
        return format == 0x8058 || format == 0x1908;
    }

    private int readInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private boolean currentRegionMatches(ClientLevel level, int regionX, int regionZ) {
        if (level == null || Minecraft.getInstance().player == null) return false;
        int chunkX = Minecraft.getInstance().player.chunkPosition().x;
        int chunkZ = Minecraft.getInstance().player.chunkPosition().z;
        return Math.floorDiv(chunkX, REGION_CHUNKS) == regionX
                && Math.floorDiv(chunkZ, REGION_CHUNKS) == regionZ;
    }

    private String regionName(long key) {
        return ChunkPos.getX(key) + "_" + ChunkPos.getZ(key);
    }

    private void updateDiagnostic(String value) {
        diagnosticReport = value;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "no message" : message;
    }

    private record DecodeResult(int majorVersion, int minorVersion, Map<String, int[]> tiles) { }
}