# Flight Computer CPU Terrain Pipeline

## Standard

The Flight Computer owns terrain acquisition, caching, generation, and presentation. No map-mod runtime is required.

## Thread boundary

Minecraft world state is read only on the client thread. A tile request first captures an immutable `TerrainChunkSnapshot` containing primitive terrain samples. Only that snapshot crosses the thread boundary.

Worker threads run `CpuTerrainTileGenerator` against the snapshot and produce a plain `int[256]` tile. Workers never access `ClientLevel`, `BlockState`, `ChunkAccess`, entities, registries, or other Minecraft world objects.

## Scheduling

- bounded queue: 64 tile jobs
- LRU decoded tile cache: 512 tiles
- worker pool: `max(1, min(4, availableProcessors - 2))`
- duplicate requests are suppressed
- render misses never block the GUI
- only loaded chunks are captured

## Rendering contract

The renderer performs cache lookup only. A miss schedules work and continues drawing. Completed CPU tiles become eligible for the later GPU upload stage; texture upload is not performed by worker threads.

## Future stages

1. regional page cache
2. persistent disk cache
3. improved terrain/biome/light shading
4. GPU page residency and batched uploads
5. benchmark against the old synchronous implementation

The CPU worker architecture is deliberately provider-neutral so the same Flight Computer renderer can consume native terrain without Xaero, JourneyMap, or VoxelMap as runtime dependencies.
