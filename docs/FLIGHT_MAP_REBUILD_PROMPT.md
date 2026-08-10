# Reusable Implementation Prompt — Flight Map Rebuild

Treat this repository and `docs/FLIGHT_MAP_REBUILD_SPEC.md` as the authoritative requirements.

Rebuild the Flight Computer MAP as a completely first-party renderer. Do not render or embed Xaero's normal `GuiMap` at any stage.

The normal Xaero World Map GUI must remain entirely independent: its camera, zoom, pan, screen lifecycle, persistent state, controls, compass, overlays, and render transform must never be read from or modified by the Flight Computer.

Use the existing Xaero bridge only where it provides verified access to the already-cached terrain/map data. Xaero may be the decoder/data source, not the GUI renderer. Decode `.xwmc` data into Flight Computer-owned normalized tiles and render those tiles with our own `FlightMapRenderer`.

Do not guess Xaero's binary format. Inspect the exact installed Xaero World Map 1.44.2 implementation/data and preserve only verified decoding logic. Unknown/corrupt data must fail gracefully rather than crash.

The Flight Controller block is the tracking anchor. Associate the controller UUID, dimension, position, and tracking radius with a controller-owned set of tracked chunks/regions. The Flight Map must render only terrain data known/tracked for that controller. Do not force Minecraft world/chunk loads solely to make the map render. Use existing cached Xaero data when available and show unknown/unexplored terrain otherwise.

Build the Flight Map as:

- `FlightMapViewport`: own centre X/Z, zoom, dimension and optional rotation.
- `FlightMapTile`: normalized terrain tile independent of Xaero classes.
- `FlightMapTileCache`: fast in-memory + persistent mod-owned cache, asynchronously populated.
- `FlightMapTracker`: controller-scoped tracked chunk/region state.
- `FlightMapRenderer`: first-party terrain + POI + route renderer.
- `FlightMapPoiRegistry`/equivalent: our own points of interest.
- navigation state for `Track`, `Route`, and `Travel`.

The map must combine decoded terrain with our own knowledge of:

- Flight Controllers
- linked controllers
- aircraft/sublevels
- airports
- docks
- pads
- beacons
- waypoints
- player/aircraft position
- claims or other POIs only when data is actually available

`Track` updates the controller's known/tracked map area. `Route` plans against our own known map/POI data and produces stops, distance, bearing and ETA where possible. `Travel` consumes that route and supplies the flight-control/navigation layer with its next target and guidance data.

Rendering rules:

- no `GuiMap.render()`
- no `GuiMap.tick()` for Flight Map purposes
- no captured Xaero screen
- no replacing `Minecraft.screen` with Xaero's screen
- no screen resizing hacks
- no translation/scissor hacks around the normal Xaero GUI
- no copying Xaero camera fields
- no forwarding Flight Map mouse input to Xaero
- no synchronous disk reads from `render()`
- no synchronous Xaero decoding from `render()`
- no forced chunk loads from `render()`
- no huge directory scan every frame

Use bounded asynchronous/tick-time decode and cached normalized tiles. Keep rendering cheap and deterministic.

The Flight Map viewport is the sole source of truth for Flight Map pan/zoom. Mouse drag and wheel input modify only FlightMapViewport.

The normal Xaero map must behave exactly as before.

Required verification:

1. Zoom normal Xaero, then open Flight Map: no zoom leakage.
2. Zoom Flight Map, then open normal Xaero: no zoom leakage.
3. Pan normal Xaero, then open Flight Map: no centre leakage.
4. Pan Flight Map, then open normal Xaero: no centre leakage.
5. Alternate repeatedly: zero state leakage.
6. No native Xaero controls/compass/full-screen map appear inside or outside the Flight Map rectangle.
7. The Flight Map is one clean rectangular render owned by our GUI.
8. Controller tracking is dimension- and controller-specific.
9. Missing/corrupt/unknown map data never crashes the client.
10. Track/Route/Travel use our own data model, not Xaero GUI state.
11. Fix compile errors and client/server classloading issues.
12. Run the actual client and verify the result visually before declaring completion.

Do not declare success because the code compiles. The visual result and state-isolation tests are mandatory.

If the current architecture prevents this clean separation, replace it. Do not keep patching the old embedded `GuiMap` renderer.
