# JourneyMap-inspired native renderer standard

## Scope

The Flight Computer will reproduce the useful *architecture and observable behaviour* of a modern map renderer without copying JourneyMap's proprietary implementation. JourneyMap's public GitHub repository states that its source is not public and is under ARR; its public API is separately available and documents asynchronous map-tile requests, bounded image size, callbacks, world-specific data paths, waypoints, and UI state.

## Target pipeline

```text
ClientLevel
  -> ChunkSnapshotter (client-thread, bounded)
  -> Immutable ChunkSnapshot
  -> MapTileWorker (worker thread, CPU only)
  -> TilePageCache (memory + optional persistent Flight Computer cache)
  -> UploadQueue (render thread)
  -> FlightMapRenderer (GUI only)
```

The GUI never scans chunks, reads disk, decodes map files, or waits for a worker. A missing tile is a cache miss and a request; it is never a synchronous render operation.

## Data model

- Logical tile: 16x16 world blocks/chunk.
- Page: a configurable square group of logical tiles, default 32x32 chunks.
- Snapshot: immutable block/height/biome/light information sufficient to build the requested visual layer.
- Tile image: compact CPU pixel buffer plus metadata (world identity, dimension, chunk/page coordinates, layer, revision, generated tick).
- GPU resource: render-thread-owned texture corresponding to a tile/page image.

## Cache policy

Use three levels:

1. **Hot tile cache**: bounded LRU of decoded CPU tiles.
2. **Page cache**: stitched pages used by the GUI to reduce draw calls and texture churn.
3. **Persistent cache**: optional compressed page files owned by Flight Computer, keyed by world identity + dimension + renderer version + layer + page coordinate.

Never use a third-party map mod's private files as the source of truth.

## Request scheduling

Requests are deduplicated by `(world, dimension, layer, tileX, tileZ)`. Priority is based on viewport distance, then camera velocity direction, then age. The scheduler has hard per-tick snapshot and upload budgets and a global pending cap. Requests that fail because a chunk is not currently available are retried with backoff.

## Rendering contract

The render pass is strictly read-only:

1. Determine visible pages.
2. Ask cache for resident GPU textures.
3. Draw resident textures.
4. Submit misses to the scheduler.
5. Draw a deterministic placeholder for missing data.
6. Draw overlays (aircraft, route, waypoints, diagnostics) after terrain.

No provider or worker may call GUI methods.

## Layers

The first native implementation should support:

- surface/topographic terrain;
- cave/vertical slice as a separate layer contract;
- biome/heat/diagnostic layers later without changing cache keys or scheduling.

## Performance target

The renderer must be judged by measurable counters rather than subjective smoothness:

- render-thread tile lookup time;
- snapshot jobs/tick;
- worker jobs/tick;
- tiles generated/sec;
- cache hit ratio;
- GPU uploads/tick and bytes uploaded;
- pending requests;
- retries and stale requests;
- frame-time contribution.

The acceptance target is zero blocking map work in the GUI render method and bounded work per client/render tick.

## Permanent Flight Computer diagnostics

The map diagnostics remain inside the Flight Computer UI and include provider mode, cache state, request state, worker state, upload state, last failure and counters. Existing power/peripheral indicators remain permanent and are not part of the renderer's optional overlays.
