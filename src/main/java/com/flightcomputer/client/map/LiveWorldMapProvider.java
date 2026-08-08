package com.flightcomputer.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Fallback provider that samples only chunks already loaded by Minecraft. */
public final class LiveWorldMapProvider implements FlightMapDataProvider {
    @Override
    public int[] getChunkTile(ClientLevel level, int chunkX, int chunkZ) {
        if (level == null || !level.hasChunk(chunkX, chunkZ)) return null;
        return computeChunk(level, chunkX, chunkZ);
    }

    private int[] computeChunk(ClientLevel level, int chunkX, int chunkZ) {
        int[] grid = new int[256];
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int y = Math.max(level.getMinBuildHeight(),
                        level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1);
                pos.set(wx, y, wz);
                BlockState state = level.getBlockState(pos);
                MapColor mapColor = state.getMapColor(level, pos);
                grid[lz * 16 + lx] = 0xFF000000 | (mapColor.col & 0xFFFFFF);
            }
        }
        return grid;
    }

    @Override public void tick(ClientLevel level) { }
    @Override public void clear() { }
}
