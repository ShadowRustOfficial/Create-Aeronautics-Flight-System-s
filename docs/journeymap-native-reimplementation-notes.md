# Native reimplementation notes

This project does **not** copy JourneyMap's proprietary source. JourneyMap's public issue repository states its source is not public and is under ARR. We can, however, reproduce the architectural properties exposed by its public API and by normal observable map behaviour.

## Reproduced concepts

- asynchronous tile acquisition;
- bounded image/tile requests;
- callback/completion style delivery;
- world/dimension scoped state;
- separate map data and GUI presentation;
- persistent world-scoped data ownership;
- waypoint/overlay data independent from terrain;
- cache-first rendering with deferred work.

## Flight Computer adaptation

The API is intentionally ours:

`NativeMapPipeline.request(key)` -> schedule

`NativeMapPipeline.tick(level)` -> bounded client snapshot + completion pump

`NativeMapCache.get(key)` -> read-only renderer path

`NativeMapTileWorker.generate(snapshot)` -> pure worker function

Future stages will add regional page stitching and GPU texture residency. The GUI will consume a renderer-owned viewport model and never access Minecraft chunks directly.

## Why this is preferable to a literal bridge

A bridge keeps us dependent on another mod's lifecycle, storage, renderer and version-specific internals. The native system makes the Flight Computer the owner of its own cache and scheduling policy, while adapters can remain available for fallback or comparison.
