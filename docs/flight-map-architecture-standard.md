# Flight Computer Map Architecture Standard

## Decision

Use a **provider-agnostic native Flight Computer map pipeline** as the long-term rendering standard. Keep Xaero as the first external provider and compatibility/fallback source, but do not make the Flight Computer dependent on Xaero's live `GuiMap` or internal rendering lifecycle.

JourneyMap is the strongest architectural reference for the pipeline shape: world/chunk sampling -> bounded render jobs -> reusable image/tile buffers -> region cache -> presentation. Its public source history exposes explicit cartography, chunk-render, model, IO, and render layers. The current JourneyMap repository is public but the inspected current root is intentionally small, so legacy source is the clearest detailed implementation evidence available.

Xaero is the strongest current integration candidate for already-rendered terrain and is highly performant as a standalone map, but its internal classes are not a stable public API. The current Flight Computer integration is therefore treated as an adapter, never as the architecture itself.

VoxelMap is not selected as the primary provider. Its source/API availability and licensing are less suitable for embedding, and community evidence reports poor large-area performance in some workloads. It remains a fallback research reference only.

## Evidence comparison

| Area | Xaero World Map | JourneyMap | VoxelMap | Flight Computer standard |
|---|---|---|---|---|
| API/integration | Internal/native classes; powerful but version-sensitive | Rich source architecture, but no small stable embedding API | Source availability varies by release; not a clean embedding contract | Stable internal `FlightMapDataProvider` contract; adapters isolate external APIs |
| Data flow | Native map session -> map tiles/regions -> textures | Chunk model -> cartography renderer -> region image set/cache -> map UI | Chunk/map data -> cached map representation -> minimap/worldmap | ClientLevel snapshot -> bounded tile jobs -> normalized CPU tile -> GPU tile cache |
| Tiling | Region/tile based; current adapter observes 4x4 16px tiles in 64px leaf textures | Explicit 16x16 chunk painting and region image sets | Chunk/layer cache; disk formats differ from other mods | 16x16 logical chunk tiles, grouped into configurable 4x4/region pages |
| Threading | Internal lifecycle is opaque; adapter must avoid guessing readiness | Rendering is job-oriented and cache-oriented; reusable buffers reduce allocation | Can become CPU-heavy for broad map views according to community reports | Main-thread snapshot only; worker-thread colorization/packing; render-thread upload only |
| Performance | Excellent standalone performance; embedding adds reflection/internal-state risk | Good architectural model; explicit reuse/caching is valuable | Less attractive for large-area workloads | Avoid full-screen native screen rendering and repeated decode; bounded work + dirty tiles + cache |
| Failure mode | Native session/tile readiness can stall integration | Render jobs can retry and cache incomplete chunks | Large scans can cause CPU pressure | Explicit state machine: DISCOVER -> QUEUE -> SNAPSHOT -> DECODE -> READY -> UPLOAD |
| UI isolation | Requires careful screen/render-state containment | Naturally separable model/render layers | UI is primarily its own map | Flight Computer owns the UI; providers never render UI into it |
| Long-term dependency risk | High | Medium | Medium/high | Low: provider contract is owned by Flight Computer |

## Why the current Xaero loop happens

The observed `requested region -1 -3 ...` cycling is consistent with the current adapter repeatedly sampling a native Xaero tile that is not ready, then re-queuing it. The queue is bounded, but readiness and request ownership are coupled. A diagnostic counter can therefore show changing coordinates while useful work remains at zero.

The current `TerrainMapCache` also performs provider work from render-oriented lookup paths. The replacement standard must make a render lookup **read-only**: a miss can mark a tile dirty/requested, but cannot synchronously enter a provider decoder.

## Standard pipeline

```text
Minecraft client world
        |
        | main/client tick: bounded snapshot acquisition
        v
+-----------------------+
| Tile Request Scheduler|  priority: centre -> near rings -> far rings
+-----------+-----------+
            |
            v
+-----------------------+
| FlightMapDataProvider |  Xaero / Journey / Native adapters
+-----------+-----------+
            |
            | immutable normalized TileData
            v
+-----------------------+
| CPU Tile Cache        |  keyed by dimension + chunk + revision
+-----------+-----------+
            |
            | dirty/ready tiles only
            v
+-----------------------+
| GPU Tile Cache        |  render-thread upload
+-----------+-----------+
            |
            v
+-----------------------+
| Flight Computer UI    |  scissor/pose owned exclusively by host
+-----------------------+
```

### Hard rules

1. **No provider owns the Flight Computer UI.**
2. **No provider render call is allowed to draw outside the map viewport.**
3. **Render-time tile lookup never performs disk IO, chunk scans, decoder work, or blocking waits.**
4. **Minecraft world objects are read only on the client/main thread.** Worker jobs receive immutable primitive snapshots.
5. **GPU texture creation/upload occurs on the render thread only.**
6. **A tile request is idempotent.** One key can exist in at most one queue state.
7. **Retry is time-based and bounded.** A failed tile cannot spin every frame.
8. **Every queue transition increments a diagnostic counter.**
9. **Permanent diagnostics remain in the Flight Computer diagnostics panel:** power level, workload, queue depth, decoded/ready/uploaded tiles, provider state, last error, and render-state status.
10. **Terrain, route, waypoint, controller and power data are separate channels.** A terrain failure must never remove or suppress the other peripherals.

## Performance target

The standard is not declared faster by assumption. It is considered successful only when a profiling pass demonstrates:

- zero synchronous provider work from the render path;
- bounded client-tick work per frame/tick;
- no repeated request of the same tile while it is pending;
- no unbounded queue growth;
- no repeated allocation of 16x16 tile buffers for unchanged data;
- no render-state leakage after map render or screen close;
- materially fewer provider/decoder invocations for the same visible viewport than the current implementation.

## Milestones

### M1 — Framework and observability

- Introduce provider-neutral tile/request/state objects.
- Centralize request scheduling and priority.
- Restore permanent power/peripheral diagnostics.
- Add counters for requested, pending, snapshot, decoded, ready, uploaded, failed, retried and dropped tiles.
- Keep Xaero adapter available but stop allowing UI code to own provider state.

### M2 — Stable Xaero adapter

- Keep the existing native Xaero map as a presentation/reference path only.
- Route terrain data through the provider contract.
- Never synchronously decode a tile during UI rendering.
- Preserve waypoint/route behavior.
- Preserve viewport scissor and render-state reset.

### M3 — Native Flight Computer renderer

- Read only loaded client chunks on the client thread.
- Produce immutable color/height/biome snapshots.
- Process snapshots off-thread using bounded workers.
- Upload changed pages on the render thread.
- Cache by dimension/chunk/revision.
- Render only the visible pages and a small prefetch ring.

### M4 — Provider benchmark and fallback

Run the same scripted viewport traversal against Xaero and native providers. Record CPU time, allocations, tile latency, retries, cache hit rate and frame-time impact. JourneyMap remains the architectural reference; VoxelMap remains an optional compatibility experiment, not a dependency.

### M5 — Selection gate

Select native as default if it meets the target without sacrificing terrain fidelity. Keep Xaero as an optional provider when present. If native fails a fidelity requirement, use Xaero for that capability behind the same interface. Do not couple the UI to either implementation.

## Diagnostics contract

The diagnostics screen must expose, at minimum:

- Provider: `NATIVE`, `XAERO`, `JOURNEYMAP`, `VOXELMAP`, or `NONE`
- Provider state: `DISCOVERING`, `READY`, `DEGRADED`, `WAITING`, `FAILED`
- Requested / pending / snapshot / decoded / ready / uploaded
- Retry count and last retry age
- Queue capacity and dropped/coalesced requests
- Cache hits / misses
- Last provider error
- Viewport centre and visible tile bounds
- Render-state guard: `CLEAN` / `DIRTY`
- Power level: `GOOD` / `MEDIUM` / `LOW` / `CRITICAL`

The diagnostics panel is part of the permanent Flight Computer UI and must not be replaced by provider-native diagnostics.

## Source evidence

- JourneyMap legacy source exposes `ChunkRenderController`, `ChunkPainter`, `RegionImageSet` usage, renderer specializations, reusable 16x16 buffers and explicit completion/error handling. This is the primary architectural reference for the proposed pipeline.
- Current Xaero releases are actively maintained and support NeoForge, but the Flight Computer must not treat internal map classes as a stable public API.
- VoxelMap supports minimap/worldmap and has broad version coverage, but its integration contract and performance characteristics are less attractive for this embedded use case.
