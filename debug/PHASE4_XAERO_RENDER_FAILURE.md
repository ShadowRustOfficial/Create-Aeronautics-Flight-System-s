# Phase 4 Xaero Render Failure

Date: 2026-08-10

This document records the exact state of the Phase 4 test before the Flight Computer map renderer is reworked.

## Observed in game

The Flight Computer MAP viewport renders several rectangular fragments of Xaero terrain at different offsets, separated by black gaps. Native Xaero UI controls (direction indicator / map controls / coordinate text) are also visible in positions that do not correspond to the Flight Computer viewport.

This proves the problem is deeper than the Flight Map rectangle's border or a simple camera offset.

## Current failing approach

The Phase 4 prototype captures the real `xaero.map.gui.GuiMap` screen created by Xaero and later calls its `render()` method from inside the Navigation Console. A scissor rectangle and pose translation are applied around that call.

That approach is not safe for embedding because `GuiMap` is a full Minecraft Screen and owns screen-space layout/render state. Its internal map rendering assumes the full Xaero World Map screen rather than an arbitrary sub-rectangle.

## Required Phase 5 architecture

Do not embed `GuiMap.render()`.

Instead:

`Xaero XWMC / decoded map data`

-> shared read-only terrain cache

-> `FlightMapRenderer`

-> exact Flight Computer viewport rectangle

The normal Xaero World Map remains completely separate and continues using its own `GuiMap` screen and camera state.

## Non-negotiable requirements

- No `GuiMap.render()` from Flight Computer.
- No `GuiMap.tick()` from Flight Computer.
- No reuse of normal Xaero camera state for Flight Map camera.
- Flight pan/zoom is independent.
- Flight renderer draws one continuous map rectangle.
- No native Xaero GUI fragments may appear outside the Flight viewport.
- Preserve existing XWMC cache/decoding work where practical.
