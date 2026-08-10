# Flight Computer map dependency boundary

## Decision

The Flight Computer does not depend on Xaero World Map, Xaero Minimap, JourneyMap, or VoxelMap.

JourneyMap is an architectural reference only. Its observable/public API concepts informed the design of the native pipeline, but no JourneyMap classes, runtime jars, APIs, files, caches, or GUI state are consumed by Flight Computer.

## Owned pipeline

Minecraft client world
-> client-thread snapshot boundary
-> immutable terrain tile
-> bounded asynchronous generation queue
-> Flight Computer CPU cache
-> Flight Computer persistent cache
-> GPU residency/upload layer
-> Flight Computer Navigation Console

## Rules

1. No map-mod imports in `src/main/java`.
2. No map-mod Maven dependencies in Gradle.
3. No map-mod dependency declarations in `neoforge.mods.toml`.
4. No reading or writing another map mod's private cache format.
5. No external map mod GUI or renderer is embedded.
6. Provider identity exposed to the UI is Flight Computer-owned.
7. Diagnostics remain inside the Flight Computer diagnostics surface.
8. The renderer must remain functional when all third-party map mods are absent.

## JourneyMap-inspired concepts retained

- asynchronous tile acquisition
- chunk-to-tile normalization
- cache-first rendering
- bounded work scheduling
- reusable regional/page-oriented storage
- separate acquisition, storage and presentation layers
- explicit completion/failure states

These are implemented as Flight Computer code rather than copied proprietary implementation code.
