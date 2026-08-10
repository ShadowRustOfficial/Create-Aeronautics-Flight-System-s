package com.flightcomputer.client.map.nativeimpl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Optional Flight Computer-owned persistent tile cache. It never reads another map mod's files.
 * Disk IO is deliberately explicit so callers can move it off the render/client tick later.
 */
public final class NativeMapDiskCache {
    private static final int MAGIC = 0x46434D31; // FCM1
    private static final int VERSION = 1;

    private final Path root;

    public NativeMapDiskCache(Path root) {
        this.root = root;
    }

    public void write(NativeMapTile tile) throws IOException {
        Path file = fileFor(tile.key());
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(tile.sourceRevision());
            int[] pixels = tile.pixels();
            out.writeInt(pixels.length);
            for (int pixel : pixels) out.writeInt(pixel);
        }
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public NativeMapTile read(NativeMapTileKey key) throws IOException {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
            long revision = in.readLong();
            int length = in.readInt();
            if (length != NativeMapTile.SIDE * NativeMapTile.SIDE) return null;
            int[] pixels = new int[length];
            for (int i = 0; i < length; i++) pixels[i] = in.readInt();
            return new NativeMapTile(key, pixels, revision);
        }
    }

    private Path fileFor(NativeMapTileKey key) {
        String dimension = key.dimension().location().toString().replace(':', '_');
        String world = safe(key.worldId()).replaceAll("[^a-zA-Z0-9._-]", "_");
        return root.resolve(world)
                .resolve(dimension)
                .resolve("layer-" + key.layer())
                .resolve(key.chunkX() + "_" + key.chunkZ() + ".fctile");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
