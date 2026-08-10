# Flight Map Rebuild — Independent Native Renderer

## Objective

Replace the current embedded-Xaero-GuiMap approach with a first-party Flight Computer map renderer that is completely independent of Xaero World Map's GUI, camera, zoom, pan, screen lifecycle, and render transform.

The normal Xaero World Map must remain untouched. The Flight Computer must never render, resize, translate, scissor, capture, tick, or otherwise reuse `GuiMap` as its visual map surface.

Xaero World Map may remain an implementation detail/data source where useful: its existing bridge/decoder can be used to locate and decode the already-written Xaero terrain tile/cache data. The decoded data must be normalized into Flight Computer-owned tile/chunk data before rendering.

## Core architecture

```text
Minecraft World / Xaero cached map data
                  |
        Xaero bridge / decoder
          (DATA ONLY, no GUI)
                  |
       Flight Map tile cache
                  |
       +----------+----------+
       |                     |
 Flight Map State       Flight Map POI DB
       |                     |
       +----------+----------+
                  |
          FlightMapRenderer
                  |
          Navigation Console
```

### Absolute separation rule

Normal Xaero:
- owns its own `GuiMap`
- owns its own camera/zoom/pan
- owns its own screen lifecycle
- renders only through its own GUI

Flight Computer:
- owns its own map viewport
- owns its own camera centre
- owns its own zoom
- owns its own pan
- owns its own dimension
- owns its own tracked-chunk set
- owns its own tile cache
- owns its own markers/POIs
- owns its own route state

There must be zero viewport/state/render leakage in either direction.

## Xaero bridge responsibility

Use Xaero only as a source/decoder where this is reliable and verified against the installed Xaero World Map 1.44.2 data format.

The bridge may:
- locate the active Xaero world-map profile/dimension data
- discover existing `.xwmc` cache regions
- decode verified Xaero terrain pixels/tiles
- convert decoded data into the mod's own `FlightMapTile` representation
- report decoder/version/data availability diagnostics

The bridge must NOT:
- instantiate or retain `GuiMap` for rendering
- call `GuiMap.render()`
- call `GuiMap.tick()` for Flight Map purposes
- replace `Minecraft.screen` with a captured Xaero screen
- alter Xaero camera fields
- alter Xaero persistent zoom/position
- forward Flight Map mouse input into Xaero
- use Xaero's GUI viewport as the Flight Map viewport

Do not guess binary formats. Keep the currently verified `.xwmc` decoding path, improve it only when the exact 1.44.2 format has been established, and add diagnostics/tests for malformed/unknown data.

## Flight Controller tracking

Each physical Flight Controller block is a tracking anchor.

A controller owns:
- persistent controller UUID
- dimension
- controller block position
- tracking radius/range
- tracked chunk coordinates
- tracking/terrain state

The tracked area must be associated with that controller rather than a global player map.

The first implementation should track the chunks/regions represented by the controller's configured tracking radius without forcing Minecraft server/client chunk loads solely for map rendering.

Where Xaero cached terrain already exists, decode it into the Flight Computer cache. If a tile has not been explored/decoded, show an explicit unexplored/unknown representation rather than silently loading world chunks or inventing terrain.

The cache should be lightweight and asynchronous from rendering. Disk access and decoding happen outside `render()`.

## Flight Map tile model

Introduce a first-party normalized representation such as:

```text
FlightMapTile
- chunkX
- chunkZ
- dimension
- 16x16 or equivalent normalized terrain/color samples
- source/version metadata if required
- explored/available flag
```

The renderer consumes only these normalized tiles.

The tile cache should support:
- in-memory fast lookup
- persistent Flight Computer cache under the mod's own namespace
- asynchronous decode/queueing
- invalidation on dimension/server/controller changes
- no render-thread filesystem reads
- no forced Minecraft chunk loads

Do not make the renderer dependent on Xaero classes.

## Renderer

Create/use a first-party `FlightMapRenderer`.

It must render directly into the Navigation Console's map rectangle using:
- `FlightMapViewport`
- normalized `FlightMapTile` data
- tracked chunks
- Flight Computer POIs
- route overlays
- aircraft/controller position

The renderer must perform its own world-to-screen transform.

Conceptually:

```text
worldX/worldZ
      |
FlightMapViewport centre + zoom
      |
map rectangle coordinates
      |
clipped first-party terrain tiles
```

No Xaero screen-space transform should be involved.

## Viewport

`FlightMapViewport` is the sole source of truth for Flight Map camera state:
- centre X
- centre Z
- zoom / pixels-per-block
- dimension
- optional rotation later

Mouse drag pans this state.
Mouse wheel changes this state.
Neither action reaches Xaero.

Opening/closing normal Xaero must not alter these values.
Opening/closing Flight Map must not alter Xaero's values.

## Points of interest

Combine normalized terrain with first-party POIs, including as applicable:
- Flight Controllers
- linked Flight Controllers
- aircraft/sublevels
- airports
- docks
- pads
- beacons
- registered waypoints
- player/aircraft position
- claims where the mod has verified data

POIs must be dimension-aware and world-coordinate based.

Do not use Xaero's GUI marker rendering as the POI renderer. The Flight Computer draws its own icons/labels and decides which POIs are visible.

## Track / Route / Travel

These functions belong to the Flight Computer navigation layer, not Xaero.

### TRACK

Track establishes/updates the known terrain and navigation data associated with the active Flight Controller. It should respect the controller's tracking radius and avoid unnecessary world loads.

### ROUTE

Route planning operates over the Flight Computer's own known/available navigation data and POIs. It should support:
- origin
- destination
- intermediate stops
- distance
- bearing
- ETA when speed is known
- route preview rendered by the Flight Computer

Do not require Xaero GUI state for route planning.

### TRAVEL

Travel consumes the active Flight Computer route and provides the navigation/flight-control layer with the next target and guidance data. It should integrate with the existing Flight Controller/aircraft control work rather than attempting to move or manipulate the Xaero map.

## Performance requirements

Previous map-generation work caused errors and performance risks. Avoid repeating them.

Hard requirements:
- no synchronous disk reads in `render()`
- no Xaero decode in `render()`
- no world/chunk loading from `render()`
- no scanning huge map directories every frame
- bounded decode/queue work per tick
- cache decoded tiles
- render only the Flight Controller's tracked/available area
- avoid creating thousands of temporary objects per frame
- degrade gracefully when tile data is missing

## Failure handling

Unknown Xaero format/version:
- do not guess
- leave affected tiles unavailable
- log a concise diagnostic
- keep the Flight Computer GUI functional

Missing Xaero data:
- Flight Map still opens
- known Flight Computer POIs can still render
- unknown terrain is visibly represented
- no crash

Corrupt `.xwmc` file:
- skip the bad region/tile
- preserve other valid data
- log the specific file and reason

Dimension/server change:
- invalidate active controller map context
- load the correct map identity
- never mix tile data between worlds/dimensions

## Removal target

The old path that embeds a captured native Xaero `GuiMap` should be removed from the Flight Map rendering path once the first-party renderer is operational.

The old classes can remain temporarily behind an explicit compatibility boundary during migration, but no production Flight Map render may call the native Xaero GUI.

## Verification matrix

1. Open normal Xaero, zoom out, close, open Flight Map: Flight Map zoom is unchanged.
2. Open Flight Map, zoom in, close, open normal Xaero: Xaero zoom is unchanged.
3. Pan normal Xaero far away, then open Flight Map: Flight Map centre is unchanged.
4. Pan Flight Map far away, then open normal Xaero: Xaero centre is unchanged.
5. Repeatedly alternate both maps: zero state leakage.
6. Render Flight Map while normal Xaero is closed: works.
7. Open normal Xaero after Flight Map: normal Xaero renders normally.
8. Flight Map remains functional if Xaero GUI classes are never instantiated by the Flight Map path.
9. Tracked chunks are scoped to the active Flight Controller and dimension.
10. Missing/unexplored tiles do not trigger forced chunk loads.
11. Corrupt/unknown Xaero cache data does not crash the client.
12. Route/Track/Travel operate from Flight Computer-owned data.
13. Build with compile errors fixed before declaring completion.
14. Run the game and visually verify the map is one clean rectangular first-party render with no duplicate/full-screen Xaero render, controls, compass, zoom widgets, or border leakage.

## Implementation rule

Do not paper over the old renderer with more translations, scissors, screen resizing, camera-field overrides, or render-order hacks. The Flight Map must become a genuinely separate renderer with a shared data/decoding layer only.
