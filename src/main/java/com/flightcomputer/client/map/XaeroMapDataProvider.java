package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Optional filesystem-only Xaero World Map reader. */
public final class XaeroMapDataProvider implements FlightMapDataProvider {
    private static final int MAX_REGIONS_PER_TICK = 1;
    private static final int REGION_CHUNKS = 32;
    private static final int CHUNKS_PER_SECTION = 4;
    private static final long NBT_BUDGET = 2_000_000L;

    private final Map<Long, int[]> chunkTiles = new HashMap<>();
    private final Set<Long> queuedRegions = new HashSet<>();
    private final Set<Long> attemptedRegions = new HashSet<>();
    private final ArrayDeque<Long> regionQueue = new ArrayDeque<>();
    private String activeIdentity;
    private XaeroWorldMapLocator.MapInstance activeMap;

    @Override public int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int[] tile = chunkTiles.get(key);
        if (tile != null) return tile;
        int regionX = Math.floorDiv(chunkX, REGION_CHUNKS);
        int regionZ = Math.floorDiv(chunkZ, REGION_CHUNKS);
        long regionKey = ChunkPos.asLong(regionX, regionZ);
        if (!attemptedRegions.contains(regionKey) && !queuedRegions.contains(regionKey)) {
            queuedRegions.add(regionKey);
            regionQueue.addLast(regionKey);
        }
        return null;
    }

    @Override public void tick(ClientLevel level) {
        ensureLevel(level);
        int processed = 0;
        while (processed < MAX_REGIONS_PER_TICK && !regionQueue.isEmpty()) {
            long key = regionQueue.pollFirst();
            queuedRegions.remove(key);
            attemptedRegions.add(key);
            Path regionFile = regionFile(key);
            if (regionFile != null) {
                try { decodeRegion(regionFile, ChunkPos.getX(key), ChunkPos.getZ(key)); }
                catch (IOException | RuntimeException ignored) { }
            }
            processed++;
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
        if (activeMap == null) return null;
        int regionX = ChunkPos.getX(regionKey);
        int regionZ = ChunkPos.getZ(regionKey);
        Path file = activeMap.instanceDirectory().resolve(regionX + "_" + regionZ + ".zip");
        return Files.isRegularFile(file) ? file : null;
    }

    private void decodeRegion(Path file, int regionX, int regionZ) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            List<BlockState> palette = new ArrayList<>();
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                decodeRegionStream(new DataInputStream(zip), regionX, regionZ, palette);
                zip.closeEntry();
            }
        }
    }

    private void decodeRegionStream(DataInputStream in, int regionX, int regionZ,
                                    List<BlockState> palette) throws IOException {
        int firstByte = in.read();
        if (firstByte < 0) return;
        int majorVersion = 0;
        int minorVersion = -1;
        if (firstByte == 255) {
            int fullVersion = in.readInt();
            minorVersion = fullVersion & 0xFFFF;
            majorVersion = (fullVersion >>> 16) & 0xFFFF;
            firstByte = -1;
        }

        while (true) {
            int sectionCoords = firstByte == -1 ? in.read() : firstByte;
            if (sectionCoords < 0) break;
            firstByte = -1;
            int sectionX = sectionCoords >>> 4;
            int sectionZ = sectionCoords & 15;
            if (sectionX >= 8 || sectionZ >= 8) break;

            for (int chunkX = 0; chunkX < CHUNKS_PER_SECTION; chunkX++) {
                for (int chunkZ = 0; chunkZ < CHUNKS_PER_SECTION; chunkZ++) {
                    int firstPixelInfo = in.readInt();
                    if (firstPixelInfo == -1) continue;
                    int worldChunkX = regionX * REGION_CHUNKS + sectionX * CHUNKS_PER_SECTION + chunkX;
                    int worldChunkZ = regionZ * REGION_CHUNKS + sectionZ * CHUNKS_PER_SECTION + chunkZ;
                    int[] tile = new int[256];
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int info = (x == 0 && z == 0) ? firstPixelInfo : in.readInt();
                            tile[z * 16 + x] = readPixel(in, info, majorVersion, minorVersion, palette);
                        }
                    }
                    chunkTiles.put(ChunkPos.asLong(worldChunkX, worldChunkZ), tile);
                }
            }
        }
    }

    private int readPixel(DataInputStream in, int info, int majorVersion, int minorVersion,
                           List<BlockState> palette) throws IOException {
        BlockState state = (info & 1) != 0
                ? readBlockState(in, info, majorVersion, palette)
                : Blocks.GRASS_BLOCK.defaultBlockState();
        if ((info & 64) != 0) in.readUnsignedByte();
        if ((info & 2) != 0) {
            int amount = in.readUnsignedByte();
            for (int i = 0; i < amount; i++) readOverlay(in, majorVersion, minorVersion, palette);
        }
        int colorType = (info >>> 2) & 3;
        int color = 0xFF000000 | (state.getBlock().defaultMapColor().col & 0xFFFFFF);
        if (colorType == 3) {
            int customColor = in.readInt();
            if (customColor != -1) color = 0xFF000000 | (customColor & 0xFFFFFF);
        }
        if ((colorType != 0 && colorType != 3) || (info & 1048576) != 0) readBiome(in, majorVersion, minorVersion);
        return color;
    }

    private void readBiome(DataInputStream in, int majorVersion, int minorVersion) throws IOException {
        if (majorVersion >= 3 && minorVersion >= 1) in.readUTF();
        else in.readUnsignedByte();
    }

    private BlockState readBlockState(DataInputStream in, int info, int majorVersion,
                                      List<BlockState> palette) throws IOException {
        if (majorVersion == 0) {
            in.readInt();
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if ((info & 2097152) != 0) {
            CompoundTag nbt = NbtIo.read(in, NbtAccounter.create(NBT_BUDGET));
            BlockState state = blockStateFromTag(nbt);
            palette.add(state);
            return state;
        }
        int paletteIndex = in.readInt();
        if (paletteIndex < 0 || paletteIndex >= palette.size()) return Blocks.GRASS_BLOCK.defaultBlockState();
        return palette.get(paletteIndex);
    }

    private void readOverlay(DataInputStream in, int majorVersion, int minorVersion,
                             List<BlockState> palette) throws IOException {
        int info = in.readInt();
        if ((info & 1) != 0) {
            if (majorVersion == 0) {
                in.readInt();
            } else if ((info & 1024) != 0) {
                CompoundTag nbt = NbtIo.read(in, NbtAccounter.create(NBT_BUDGET));
                palette.add(blockStateFromTag(nbt));
            } else {
                int paletteIndex = in.readInt();
                if (paletteIndex < 0 || paletteIndex >= palette.size()) return;
            }
        }
        if (minorVersion < 1 && (info & 2) != 0) in.readInt();
        int colorType = (info >>> 8) & 3;
        if (colorType == 2 || (info & 4) != 0) in.readInt();
        if ((info & 8) != 0) in.readInt();
    }

    private BlockState blockStateFromTag(CompoundTag tag) {
        try {
            String name = tag.getString("Name");
            if (name == null || name.isBlank()) return Blocks.GRASS_BLOCK.defaultBlockState();
            ResourceLocation id = ResourceLocation.parse(name);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) return Blocks.GRASS_BLOCK.defaultBlockState();
            return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        } catch (RuntimeException ignored) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
    }

    @Override public void clear() {
        chunkTiles.clear();
        queuedRegions.clear();
        attemptedRegions.clear();
        regionQueue.clear();
        activeIdentity = null;
        activeMap = null;
    }
}
