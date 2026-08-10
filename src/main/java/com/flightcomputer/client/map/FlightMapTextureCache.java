package com.flightcomputer.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GPU-side cache for Xaero's already-decoded 64x64 leaf textures.
 *
 * The map screen never draws individual terrain pixels. Each Xaero leaf becomes one cached
 * DynamicTexture, so a large map is rendered with a small number of textured quads. Textures are
 * uploaded only when a leaf first becomes visible or Xaero changes its texture version.
 */
public final class FlightMapTextureCache {
    private static final int MAX_TEXTURES = 192;
    private static final int UPLOADS_PER_FRAME = 6;
    private static final int SIZE = XaeroMapDataProvider.LEAF_PIXELS;

    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>(64, 0.75F, true);
    private int uploadsThisFrame;
    private String identity;

    public void beginFrame(String mapIdentity) {
        if (!mapIdentity.equals(identity)) {
            clear();
            identity = mapIdentity;
        }
        uploadsThisFrame = 0;
    }

    public boolean drawLeaf(GuiGraphics graphics, XaeroMapDataProvider provider,
                            int level, int leafX, int leafZ,
                            int screenX, int screenY, int screenWidth, int screenHeight) {
        long key = pack(level, leafX, leafZ);
        Entry entry = entries.get(key);
        XaeroMapDataProvider.LeafSnapshot snapshot = provider.getLeaf(level, leafX, leafZ);

        if (snapshot != null) {
            if (entry == null || entry.version != snapshot.textureVersion()) {
                if (uploadsThisFrame >= UPLOADS_PER_FRAME) return false;
                entry = updateTexture(key, snapshot, entry);
                uploadsThisFrame++;
            }
        }

        if (entry == null) return false;
        graphics.blit(entry.location, screenX, screenY, 0,
                0.0F, 0.0F, screenWidth, screenHeight, SIZE, SIZE);
        return true;
    }

    public void clear() {
        for (Entry entry : entries.values()) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
            entry.texture.close();
        }
        entries.clear();
    }

    private Entry updateTexture(long key, XaeroMapDataProvider.LeafSnapshot snapshot, Entry old) {
        Minecraft mc = Minecraft.getInstance();
        if (old != null) {
            writePixels(old.texture.getPixels(), snapshot.rgba());
            old.texture.upload();
            old.version = snapshot.textureVersion();
            return old;
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        writePixels(image, snapshot.rgba());
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = mc.getTextureManager().register("flightcomputer_xaero_" + Long.toUnsignedString(key), texture);
        Entry entry = new Entry(texture, location, snapshot.textureVersion());
        entries.put(key, entry);
        evictIfNeeded();
        return entry;
    }

    private void evictIfNeeded() {
        while (entries.size() > MAX_TEXTURES) {
            Iterator<Map.Entry<Long, Entry>> iterator = entries.entrySet().iterator();
            if (!iterator.hasNext()) return;
            Entry entry = iterator.next().getValue();
            iterator.remove();
            Minecraft.getInstance().getTextureManager().release(entry.location);
            entry.texture.close();
        }
    }

    /** Xaero stores RGBA bytes; NativeImage's RGBA format is packed ABGR in the Java int. */
    private static void writePixels(NativeImage image, byte[] rgba) {
        for (int y = 0, i = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++, i += 4) {
                int r = rgba[i] & 0xFF;
                int g = rgba[i + 1] & 0xFF;
                int b = rgba[i + 2] & 0xFF;
                int a = rgba[i + 3] & 0xFF;
                int abgr = r | (g << 8) | (b << 16) | (a << 24);
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    private static long pack(int level, int x, int z) {
        long value = ((long) level & 0xFFL) << 56;
        value |= ((long) x & 0x0FFFFFFFL) << 28;
        value |= (long) z & 0x0FFFFFFFL;
        return value;
    }

    private static final class Entry {
        private final DynamicTexture texture;
        private final ResourceLocation location;
        private int version;

        private Entry(DynamicTexture texture, ResourceLocation location, int version) {
            this.texture = texture;
            this.location = location;
            this.version = version;
        }
    }
}
