package com.flightcomputer.client.map;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads Xaero World Map's real on-disk terrain cache without touching live Minecraft chunks.
 * Each .xwmc is a ZIP container containing cache.xaero; region filenames are 32x32-chunk regions.
 */
public final class XaeroMapDataProvider implements FlightMapDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_REGIONS_PER_TICK = 1;
    private static final int REGION_CHUNKS = 32;
    private static final int CHUNKS_PER_SECTION = 4;
    private static final long NBT_BUDGET = 2_000_000L;

    private final Map<Long, int[]> chunkTiles = new HashMap<>();
    private final Set<Long> queuedRegions = new HashSet<>();
    private final Set<Long> attemptedRegions = new HashSet<>();
    private final Set<Long> decodedKeys = new LinkedHashSet<>();
    private final ArrayDeque<Long> regionQueue = new ArrayDeque<>();

    private String activeIdentity;
    private XaeroWorldMapLocator.MapInstance activeMap;
    private String diagnosticReport = "Xaero provider not initialized.";

    @Override
    public int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ) {
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

    @Override
    public void tick(ClientLevel level) {
        ensureLevel(level);
        int processed = 0;
        while (processed < MAX_REGIONS_PER_TICK && !regionQueue.isEmpty()) {
            long key = regionQueue.pollFirst();
            queuedRegions.remove(key);
            attemptedRegions.add(key);
            Path regionFile = regionFile(key);
            if (regionFile == null) {
                updateDiagnostic("No Xaero tile file for requested region " + ChunkPos.getX(key) + "_" + ChunkPos.getZ(key));
            } else {
                try {
                    DecodeResult result = decodeRegion(regionFile, ChunkPos.getX(key), ChunkPos.getZ(key));
                    updateDiagnostic(String.format("Decoded %s | format=%d.%d | sections=%d | chunks=%d | bytes=%d | pending=%d",
                            regionFile.getFileName(), result.majorVersion, result.minorVersion,
                            result.sections, result.chunks, result.bytes, regionQueue.size()));
                } catch (IOException | RuntimeException e) {
                    updateDiagnostic("FAILED to decode " + regionFile + " : " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                    LOGGER.warn("[FlightComputer] Xaero tile decode failed: {}", regionFile, e);
                }
            }
            processed++;
        }
    }

    public Map<Long, int[]> drainDecodedTiles() {
        Map<Long, int[]> result = new HashMap<>();
        for (Long key : decodedKeys) {
            int[] tile = chunkTiles.get(key);
            if (tile != null) result.put(key, tile);
        }
        decodedKeys.clear();
        return result;
    }

    public String diagnostics() { return diagnosticReport; }

    private void ensureLevel(ClientLevel level) {
        if (level == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        String identity = buildIdentity(minecraft, level);
        if (identity.equals(activeIdentity)) return;
        clear();
        activeIdentity = identity;
        activeMap = XaeroWorldMapLocator.locate(level).orElse(null);
        writeDiscoveryReport(level);
    }

    private String buildIdentity(Minecraft minecraft, ClientLevel level) {
        String server = minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip
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
        String name = regionX + "_" + regionZ;
        List<Path> preferred = List.of(
                activeMap.instanceDirectory().resolve("cache").resolve("1").resolve(name + ".xwmc"),
                activeMap.instanceDirectory().resolve("cache_1").resolve(name + ".xwmc"));
        for (Path path : preferred) if (Files.isRegularFile(path)) return path;
        Path fallbackCurrent = findCacheFile(name, ".xwmc");
        if (fallbackCurrent != null) return fallbackCurrent;
        return findCacheFile(name, ".xwmc.outdated");
    }

    private Path findCacheFile(String name, String suffix) {
        if (activeMap == null) return null;
        try (var files = Files.walk(activeMap.instanceDirectory(), 6)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !containsCavesDirectory(activeMap.instanceDirectory(), path))
                    .filter(path -> path.getFileName().toString().equals(name + suffix))
                    .filter(this::isCacheFile).findFirst().orElse(null);
        } catch (IOException ignored) { return null; }
    }

    private boolean isCacheFile(Path path) {
        Path relative = activeMap.instanceDirectory().relativize(path);
        if (relative.getNameCount() < 2) return false;
        String first = relative.getName(0).toString().toLowerCase(java.util.Locale.ROOT);
        return first.equals("cache") || first.matches("cache_\\d+");
    }

    private boolean containsCavesDirectory(Path base, Path file) {
        for (Path part : base.relativize(file)) if (part.toString().equalsIgnoreCase("caves")) return true;
        return false;
    }

    private DecodeResult decodeRegion(Path file, int regionX, int regionZ) throws IOException {
        int sections = 0, chunks = 0, major = 0, minor = -1;
        long bytes = Files.size(file);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            boolean found = false;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().equals("cache.xaero")) continue;
                found = true;
                DecodeResult result = decodeRegionStream(new DataInputStream(zip), regionX, regionZ, new ArrayList<>());
                sections += result.sections; chunks += result.chunks; major = result.majorVersion; minor = result.minorVersion;
                zip.closeEntry();
                break;
            }
            if (!found) throw new IOException("missing cache.xaero entry");
        }
        return new DecodeResult(major, minor, sections, chunks, bytes);
    }

    private DecodeResult decodeRegionStream(DataInputStream in, int regionX, int regionZ, List<BlockState> palette) throws IOException {
        int firstByte = in.read();
        if (firstByte < 0) return new DecodeResult(0, -1, 0, 0, 0);
        int major = 0, minor = -1;
        if (firstByte == 255) {
            int fullVersion = in.readInt();
            minor = fullVersion & 0xFFFF;
            major = (fullVersion >>> 16) & 0xFFFF;
            firstByte = -1;
        }
        int sections = 0, chunks = 0;
        while (true) {
            int sectionCoords = firstByte == -1 ? in.read() : firstByte;
            if (sectionCoords < 0) break;
            firstByte = -1;
            int sectionX = sectionCoords >>> 4, sectionZ = sectionCoords & 15;
            if (sectionX >= 8 || sectionZ >= 8) break;
            sections++;
            for (int chunkX = 0; chunkX < CHUNKS_PER_SECTION; chunkX++) {
                for (int chunkZ = 0; chunkZ < CHUNKS_PER_SECTION; chunkZ++) {
                    int firstPixelInfo = in.readInt();
                    if (firstPixelInfo == -1) continue;
                    int worldChunkX = regionX * REGION_CHUNKS + sectionX * CHUNKS_PER_SECTION + chunkX;
                    int worldChunkZ = regionZ * REGION_CHUNKS + sectionZ * CHUNKS_PER_SECTION + chunkZ;
                    int[] tile = new int[256];
                    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        int info = (x == 0 && z == 0) ? firstPixelInfo : in.readInt();
                        tile[z * 16 + x] = readPixel(in, info, major, minor, palette);
                    }
                    long key = ChunkPos.asLong(worldChunkX, worldChunkZ);
                    chunkTiles.put(key, tile);
                    decodedKeys.add(key);
                    chunks++;
                }
            }
        }
        return new DecodeResult(major, minor, sections, chunks, 0);
    }

    private int readPixel(DataInputStream in, int info, int major, int minor, List<BlockState> palette) throws IOException {
        BlockState state = (info & 1) != 0 ? readBlockState(in, info, major, palette) : Blocks.GRASS_BLOCK.defaultBlockState();
        if ((info & 64) != 0) in.readUnsignedByte();
        if ((info & 2) != 0) {
            int amount = in.readUnsignedByte();
            for (int i = 0; i < amount; i++) readOverlay(in, major, minor, palette);
        }
        int colorType = (info >>> 2) & 3;
        int color = 0xFF000000 | (state.getBlock().defaultMapColor().col & 0xFFFFFF);
        if (colorType == 3) {
            int customColor = in.readInt();
            if (customColor != -1) color = 0xFF000000 | (customColor & 0xFFFFFF);
        }
        if ((colorType != 0 && colorType != 3) || (info & 1048576) != 0) readBiome(in, major, minor);
        return color;
    }

    private void readBiome(DataInputStream in, int major, int minor) throws IOException {
        if (major >= 3 && minor >= 1) in.readUTF(); else in.readUnsignedByte();
    }

    private BlockState readBlockState(DataInputStream in, int info, int major, List<BlockState> palette) throws IOException {
        if (major == 0) { in.readInt(); return Blocks.GRASS_BLOCK.defaultBlockState(); }
        if ((info & 2097152) != 0) {
            CompoundTag nbt = NbtIo.read(in, NbtAccounter.create(NBT_BUDGET));
            BlockState state = blockStateFromTag(nbt); palette.add(state); return state;
        }
        int paletteIndex = in.readInt();
        if (paletteIndex < 0 || paletteIndex >= palette.size()) return Blocks.GRASS_BLOCK.defaultBlockState();
        return palette.get(paletteIndex);
    }

    private void readOverlay(DataInputStream in, int major, int minor, List<BlockState> palette) throws IOException {
        int info = in.readInt();
        if ((info & 1) != 0) {
            if (major == 0) in.readInt();
            else if ((info & 1024) != 0) { CompoundTag nbt = NbtIo.read(in, NbtAccounter.create(NBT_BUDGET)); palette.add(blockStateFromTag(nbt)); }
            else { int paletteIndex = in.readInt(); if (paletteIndex < 0 || paletteIndex >= palette.size()) return; }
        }
        if (minor < 1 && (info & 2) != 0) in.readInt();
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
        } catch (RuntimeException ignored) { return Blocks.GRASS_BLOCK.defaultBlockState(); }
    }

    private void writeDiscoveryReport(ClientLevel level) {
        StringBuilder report = new StringBuilder();
        report.append("Flight Computer Xaero World Map diagnostic\n");
        report.append("dimension=").append(level.dimension().location()).append('\n');
        report.append("worldRootFound=").append(activeMap != null).append('\n');
        if (activeMap == null) report.append("No matching Xaero world-map directory/dimension/cache was found.\n");
        else {
            Path worldDirectory = activeMap.dimensionDirectory().getParent();
            report.append("worldDirectory=").append(worldDirectory).append('\n');
            report.append("dimensionDirectory=").append(activeMap.dimensionDirectory()).append('\n');
            report.append("instanceDirectory=").append(activeMap.instanceDirectory()).append('\n');
            report.append("currentXwmcCount=").append(activeMap.regionFiles()).append('\n');
            report.append("outdatedXwmcCount=").append(activeMap.outdatedFiles()).append('\n');
            report.append("expectedTileFormat=ZIP container containing cache.xaero\n");
            report.append("tileCapacity=32x32 chunks = 512x512 blocks per region\n");
            appendTileNames(report, activeMap.instanceDirectory());
        }
        diagnosticReport = report.toString();
        writeDiagnosticFile(diagnosticReport);
        LOGGER.info(diagnosticReport.replace('\n', ' '));
    }

    private void appendTileNames(StringBuilder report, Path instance) {
        try (var files = Files.walk(instance, 6)) {
            List<String> names = files.filter(Files::isRegularFile)
                    .filter(path -> !containsCavesDirectory(instance, path))
                    .filter(this::isCacheFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xwmc") || path.getFileName().toString().endsWith(".xwmc.outdated"))
                    .map(instance::relativize).map(Path::toString).sorted().limit(32).toList();
            report.append("tileSamples=").append(names).append('\n');
        } catch (IOException e) { report.append("tileScanError=").append(safeMessage(e)).append('\n'); }
    }

    private void updateDiagnostic(String line) {
        diagnosticReport = diagnosticReport + line + '\n';
        writeDiagnosticFile(diagnosticReport);
    }

    private void writeDiagnosticFile(String report) {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("flightcomputer").resolve("xaero_diagnostics.txt");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) { }
    }

    private static String safeMessage(Throwable throwable) { return throwable.getMessage() == null ? "<no message>" : throwable.getMessage(); }

    @Override
    public void clear() {
        chunkTiles.clear(); queuedRegions.clear(); attemptedRegions.clear(); decodedKeys.clear(); regionQueue.clear();
        activeIdentity = null; activeMap = null; diagnosticReport = "Xaero provider cleared.";
    }

    private record DecodeResult(int majorVersion, int minorVersion, int sections, int chunks, long bytes) {}
}
