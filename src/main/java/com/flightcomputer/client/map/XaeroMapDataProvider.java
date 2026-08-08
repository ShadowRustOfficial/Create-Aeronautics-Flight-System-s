package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Filesystem-only Xaero World Map reader.
 *
 * Xaero's current world-map files are .xwmc ZIP containers containing a cache.xaero
 * region stream. No Minecraft chunk is loaded or sampled by this provider.
 */
public final class XaeroMapDataProvider implements FlightMapDataProvider {
    private static final int MAX_REGION_JOBS_PER_TICK = 2;
    private static final int REGION_CHUNKS = 32;
    private static final int CHUNKS_PER_SECTION = 4;
    private static final long MAX_ENTRY_BYTES = 8L * 1024L * 1024L;

    private final Map<Long, int[]> chunkTiles = new ConcurrentHashMap<>();
    private final Set<Long> queuedRegions = ConcurrentHashMap.newKeySet();
    private final Set<Long> attemptedRegions = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Long> regionQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(new DecoderThreadFactory());

    private volatile String activeIdentity;
    private volatile XaeroWorldMapLocator.MapInstance activeMap;

    @Override
    public int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int[] tile = chunkTiles.get(key);
        if (tile != null) return tile;

        int regionX = Math.floorDiv(chunkX, REGION_CHUNKS);
        int regionZ = Math.floorDiv(chunkZ, REGION_CHUNKS);
        long regionKey = ChunkPos.asLong(regionX, regionZ);
        if (!attemptedRegions.contains(regionKey) && queuedRegions.add(regionKey)) {
            regionQueue.add(regionKey);
        }
        return null;
    }

    @Override
    public void tick(ClientLevel level) {
        ensureLevel(level);
        for (int i = 0; i < MAX_REGION_JOBS_PER_TICK; i++) {
            Long key = regionQueue.poll();
            if (key == null) return;
            queuedRegions.remove(key);
            attemptedRegions.add(key);
            decoder.execute(() -> decodeRegionSafe(key));
        }
    }

    private void decodeRegionSafe(long regionKey) {
        Path regionFile = regionFile(regionKey);
        if (regionFile == null) return;
        try {
            decodeRegion(regionFile, ChunkPos.getX(regionKey), ChunkPos.getZ(regionKey));
        } catch (IOException | RuntimeException ignored) {
            // A malformed/outdated Xaero region must never affect the render thread.
            // Successfully decoded tiles from earlier sections remain usable.
        }
    }

    private void ensureLevel(ClientLevel level) {
        if (level == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        String identity = buildIdentity(minecraft, level);
        if (identity.equals(activeIdentity)) return;

        clear();
        activeIdentity = identity;
        activeMap = XaeroWorldMapLocator.locate(level).orElse(null);
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

    private Path regionFile(long regionKey) {
        XaeroWorldMapLocator.MapInstance map = activeMap;
        if (map == null) return null;
        int regionX = ChunkPos.getX(regionKey);
        int regionZ = ChunkPos.getZ(regionKey);

        Path normal = map.instanceDirectory().resolve(regionX + "_" + regionZ + ".xwmc");
        if (Files.isRegularFile(normal)) return normal;

        Path outdated = map.instanceDirectory().resolve(regionX + "_" + regionZ + ".xwmc.outdated");
        return Files.isRegularFile(outdated) ? outdated : null;
    }

    private void decodeRegion(Path file, int regionX, int regionZ) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !"cache.xaero".equals(entry.getName())) continue;
                byte[] data = zip.readNBytes((int) MAX_ENTRY_BYTES + 1);
                if (data.length > MAX_ENTRY_BYTES) return;
                decodeRegionStream(new DataInputStream(new ByteArrayInputStream(data)), regionX, regionZ);
                return;
            }
        }
    }

    /**
     * Xaero 1.44.x stores the cache header as two unsigned shorts (format 1.25),
     * followed by 8x8 ChunksChunks. Each ChunksChunk contains a 4x4 grid of 16x16
     * block-column tiles. Older cache streams use the documented 00ff/u32 header;
     * the legacy header is accepted as well.
     */
    private void decodeRegionStream(DataInputStream in, int regionX, int regionZ) throws IOException {
        int first = in.readUnsignedByte();
        int second = in.readUnsignedByte();
        int majorVersion;
        int minorVersion;
        if (first == 0 && second == 0xFF) {
            int version = in.readInt();
            majorVersion = (version >>> 16) & 0xFFFF;
            minorVersion = version & 0xFFFF;
        } else {
            majorVersion = first;
            minorVersion = second;
        }

        while (in.available() > 0) {
            int sectionCoords = in.readUnsignedByte();
            if (sectionCoords < 0) break;
            int sectionX = sectionCoords >>> 4;
            int sectionZ = sectionCoords & 15;
            if (sectionX >= 8 || sectionZ >= 8) break;

            for (int chunkX = 0; chunkX < CHUNKS_PER_SECTION; chunkX++) {
                for (int chunkZ = 0; chunkZ < CHUNKS_PER_SECTION; chunkZ++) {
                    int[] tile = new int[256];
                    boolean present = true;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            skipInlinePaletteRecord(in);
                            int info = in.readInt();
                            if (info == -1) {
                                present = false;
                                continue;
                            }
                            int color = readPixel(in, info, majorVersion, minorVersion);
                            tile[z * 16 + x] = color;
                        }
                    }

                    if (present) {
                        int worldChunkX = regionX * REGION_CHUNKS + sectionX * CHUNKS_PER_SECTION + chunkX;
                        int worldChunkZ = regionZ * REGION_CHUNKS + sectionZ * CHUNKS_PER_SECTION + chunkZ;
                        chunkTiles.put(ChunkPos.asLong(worldChunkX, worldChunkZ), tile);
                    }
                }
            }
        }
    }

    /**
     * Current Xaero caches may inline a small palette record before continuing the
     * pixel stream. It is deliberately recognized only when the record is strongly
     * identifiable as a namespaced Minecraft ID, so arbitrary terrain bytes are not
     * consumed as metadata.
     */
    private void skipInlinePaletteRecord(DataInputStream in) throws IOException {
        if (!in.markSupported()) return;
        in.mark(512);
        try {
            int type = in.readUnsignedShort();
            int index = in.readUnsignedShort();
            int length = in.readUnsignedByte();
            if (type != 1 || index > 4096 || length <= 0 || length > 64) {
                in.reset();
                return;
            }
            byte[] id = in.readNBytes(length);
            if (id.length != length || !looksLikeResourceId(id)) {
                in.reset();
            }
        } catch (IOException | RuntimeException ex) {
            try { in.reset(); } catch (IOException ignored) { }
        }
    }

    private boolean looksLikeResourceId(byte[] id) {
        boolean colon = false;
        for (byte value : id) {
            int c = value & 0xFF;
            if (c == ':') colon = true;
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')
                    && c != '_' && c != '-' && c != ':' && c != '.') return false;
        }
        return colon;
    }

    private int readPixel(DataInputStream in, int info, int majorVersion, int minorVersion) throws IOException {
        BlockState state = Blocks.GRASS_BLOCK.defaultBlockState();

        if ((info & 1) != 0) {
            // Newer Xaero streams can pack a palette reference into the pixel flags.
            // When the packed form is present there is no standalone state payload.
            if ((info & (1 << 21)) == 0) {
                int packedState = in.readInt();
                state = blockStateFromPackedId(packedState);
            }
        }

        if ((info & 64) != 0) in.readUnsignedByte();

        if ((info & 2) != 0) {
            int amount = in.readUnsignedByte();
            // Corrupt/unknown palette data can expose a huge byte here. Do not let
            // that desynchronize the whole client; only parse plausible overlay counts.
            if (amount <= 16) {
                for (int i = 0; i < amount; i++) readOverlay(in);
            }
        }

        int colorType = (info >>> 2) & 3;
        int color = 0xFF000000 | (state.getBlock().defaultMapColor().col & 0xFFFFFF);

        if (colorType == 3) {
            int customColor = in.readInt();
            if (customColor != -1) color = 0xFF000000 | (customColor & 0xFFFFFF);
        }

        if ((colorType != 0 && colorType != 3) || (info & (1 << 20)) != 0) {
            // Xaero's newer caches may store a palette reference here. For the
            // normalized map we only need to advance over the compact reference;
            // the block/biome color remains a safe Minecraft map-color fallback.
            in.readUnsignedByte();
        }

        return color;
    }

    private void readOverlay(DataInputStream in) throws IOException {
        int info = in.readInt();
        if ((info & 1) != 0 && (info & (1 << 21)) == 0) in.readInt();
        if ((info & 0x10) != 0) in.readInt();
        int colorType = (info >>> 8) & 3;
        if (colorType == 2 || (info & 4) != 0) in.readInt();
        if ((info & 8) != 0) in.readInt();
    }

    private BlockState blockStateFromPackedId(int packedId) {
        // The exact state palette is deliberately not coupled to Xaero internals.
        // Registry IDs are accepted when they are valid; otherwise grass is a safe
        // fallback and, importantly, no world/chunk lookup is performed.
        try {
            int registryId = packedId & 0xFFFF;
            if (registryId >= 0 && registryId < BuiltInRegistries.BLOCK.size()) {
                BlockState state = BuiltInRegistries.BLOCK.byId(registryId).defaultBlockState();
                if (state != null) return state;
            }
        } catch (RuntimeException ignored) { }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    @Override
    public void clear() {
        chunkTiles.clear();
        queuedRegions.clear();
        attemptedRegions.clear();
        regionQueue.clear();
        activeIdentity = null;
        activeMap = null;
    }

    private static final class DecoderThreadFactory implements ThreadFactory {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "FlightComputer-XaeroDecoder");
            thread.setDaemon(true);
            return thread;
        }
    }
}
