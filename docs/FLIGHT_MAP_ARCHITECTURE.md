# Flight Computer Map Architecture

## Milestone: provider framework + first refactor

The Flight Computer owns the map UI and renderer. Terrain decoding is a replaceable service.

| Layer | Responsibility | Knows about Xaero? | Performance rule |
|---|---|---:|---|
| `FlightMapScreen` | UI, viewport, markers, permanent operational status | No | No disk/region API calls from render |
| `TerrainMapCache` | Bounded decoded-leaf cache and renderer-facing sampling | No | LRU cap; cache misses are measurable |
| `TerrainProvider` | Load/decode scheduling contract | No | Provider owns request throttling |
| `XaeroMapDataProvider` | Xaero session, regions, MapChunks and decoded colour buffers | Yes | Bounded requests; retry interval; reuse Xaero decoder |
| `TerrainProviderDiagnostics` | Operational/debug status snapshot | No | Immutable snapshot; safe to display in UI |

## Provider lifecycle

`OFFLINE -> INITIALIZING -> LOADING -> READY`

Failure states are explicit:

- `DEGRADED`: provider is connected but a required stage is unavailable.
- `ERROR`: an API/decoder failure occurred.
- `OFFLINE`: no usable map session is present.

## Data flow

```text
FlightMapScreen
      |
      | TerrainViewport
      v
TerrainProvider
      |
      v
Xaero MapProcessor
      |
      +--> MapRegion / MapSaveLoad
      |
      +--> MapTileChunk
      |
      +--> LeafRegionTexture
      |
      v
RGBA leaf snapshot
      |
      v
TerrainMapCache (bounded LRU)
      |
      v
Flight Computer renderer
```

The renderer does not parse `.xwmc`, own Xaero GUI state, or choose Xaero LODs. Zoom is a Flight Computer viewport concern.

## Diagnostics contract

The permanent in-UI terrain status reports state and bounded counters: requested regions, loaded regions, decoded leaves and cached leaves. Detailed provider messages remain available through `TerrainProviderDiagnostics` for the dedicated diagnostics UI.

No diagnostic text is logged into the game world, HUD or chat by this architecture. Rendering stays clipped to the Flight Computer screen.

## Next milestone

The framework is now ready for the actual terrain rendering optimisation: stop sampling individual world pixels from the GUI and upload/cache decoded 64x64 leaves as renderable textures or batched quads. That is the next performance-critical step once this milestone compiles.
