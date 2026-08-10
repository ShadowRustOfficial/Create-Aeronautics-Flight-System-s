# Native Xaero Flight Map

## Architecture

The Flight Computer does **not** embed or render Xaero's `GuiMap`. It owns its own viewport, mouse input, zoom, panning, marker layer and route/navigation state.

Xaero World Map is used only as a terrain data provider:

```text
Xaero .xwmc/cache data
        |
        v
Xaero MapSaveLoad / MapProcessor
        |
        v
MapTileChunk -> LeafRegionTexture
        |
        v
getDirectColorBuffer()
        |
        v
FlightMapTextureCache
        |
        v
FlightMapRenderer
```

This deliberately avoids reproducing Xaero's cache decoder. Xaero handles cache versions, compression, palettes, biome colouring and disk IO itself.

## Performance model

- No `.xwmc` filesystem scanning from the render loop.
- No Minecraft chunk loading for map rendering.
- Map regions are requested through Xaero's existing `MapSaveLoad` queue.
- The Flight Map selects the closest Xaero map level to the current zoom.
- Each 64x64 Xaero leaf is uploaded to one cached GPU texture.
- The GUI draws textured quads rather than thousands of `GuiGraphics.fill()` calls.
- Texture uploads are capped at six new/changed leaves per frame to prevent upload spikes.
- GPU textures use an LRU cache capped at 192 leaves.
- Existing Xaero texture versions are reused until Xaero reports a changed version.
- Terrain outside the Flight Controller's tracked radius is never rendered by the Flight Map.

## Independence guarantees

The following belong exclusively to Flight Computer:

- `FlightMapViewport.centerX/centerZ`
- `FlightMapViewport.blocksPerPixel`
- map drag input
- map wheel input
- controller/player markers
- Flight Computer POIs
- future Track / Route / Travel overlays

Xaero's GUI camera and interaction state are never read or modified.

## Dependency

The build uses Xaero's official Maven coordinates as `compileOnly` dependencies. The Xaero jars are not bundled into Flight Computer; the player's installed Xaero World Map and XaeroLib provide the runtime classes.

Targeted versions for the current branch:

- Minecraft 1.21.1
- NeoForge 21.1.x
- Xaero World Map 1.44.2+
- XaeroLib 1.1.15+

The integration is client-side only.
