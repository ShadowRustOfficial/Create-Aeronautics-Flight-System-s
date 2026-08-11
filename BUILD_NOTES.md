# Flight Computer 0.5.0 build notes

Target environment:
- Minecraft 1.21.1
- NeoForge 21.1.233
- Java 21
- Create 6.0.10 (Maven build 223, compile-only API)
- GeckoLib 4.8.4 (implementation, runtime-required)

## 0.5.0 foundation changes
- Bumped the project version to `0.5.0`.
- Replaced integer-driven controller mutation with named, server-validated `FlightControllerAction` values.
- Added immutable `FlightControllerState` and `FlightMode`; this is the persistent, synchronized source of truth for UI, model and future avionics modules.
- Added `FlightControllerAnimationBridge`, so Blockbench animation names are mapped in one place rather than scattered through UI and block code.
- Added declarative Tier-1 panel button definitions. Shift + right click with an empty offhand now maps physical panel regions to the exact same actions used by the UI.
- Removed obsolete `DistExecutor` and `GeckoLib.initialize()` calls. Corrected the GeckoLib 4.8.4 `AnimatableInstanceCache` import.
- Added the required 1.21.1 `BaseEntityBlock` codec implementation.

## Changes in the 0.4.0 revision
- `build.gradle`: added the GeckoLib Cloudsmith Maven repository and an `implementation "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:${geckolib_version}"` dependency.
- `gradle.properties`: `mod_version` bumped to `0.3.0`, added `geckolib_version=4.8.4`.
- New source tree:
  - `FlightComputer` - main mod class, calls `GeckoLib.initialize()`, registers the deferred registers.
  - `ModBlocks` / `ModItems` / `ModBlockEntities` - DeferredRegister setup for the Flight Controller block.
  - `FlightControllerBlock` / `FlightControllerBlockEntity` - the block entity implements `GeoBlockEntity` with a two-state (idle/active) `AnimationController`.
  - `FlightControllerModel` / `FlightControllerRenderer` - GeckoLib `GeoModel` + `GeoBlockRenderer` pointing at geo/animation/texture resource locations.
  - `ClientSetup` - registers the block entity renderer and client setup hook.
  - `KeyBindings` / `KeyInputHandler` - `M` key opens the Flight Map screen.
- New `com.flightcomputer.map` package: `MapMarker`, `MarkerCategory`, `MarkerRegistry` - a client-side, in-memory store that other Flight Computer systems push points of interest into.
- New `com.flightcomputer.client.gui.FlightMapScreen` - flat top-down overlay centered on the player, drawing only markers from `MarkerRegistry` for the player's current dimension, with per-category show/hide buttons. No entity/mob tracking, no chunk/terrain rendering.
- `neoforge.mods.toml` added, with a required dependency on `geckolib` (`[4.8.4,)`).

## Xaero API investigation (why we didn't integrate directly)
Neither Xaero's Minimap nor Xaero's World Map publish an official, documented API artifact for third-party mods. Every third-party integration found (way2wayfabric, server_waypoint, XaeroPlus, Map Link) either writes directly to Xaero's own waypoint file format, depends on Xaero's internal classes with no stability guarantee, or ships as a separately-maintained bridge mod. Since Flight Computer needs to keep working across Xaero updates without chasing internal API breaks, the first-party `FlightMapScreen` is the safer long-term path. It can later be extended to *also* export to Xaero's waypoint file format as a bonus, without ever making Flight Computer depend on Xaero being installed.

## Not yet done
- GeckoLib geo model / animation JSON export (current `Flight Controller 1x1.java` export predates the GeckoLib decision and is plain-geometry only - needs re-export from Blockbench with the GeckoLib plugin).
- Block/item textures and the underlying blockstate/model JSON wiring for `flight_controller`.
- Wiring `MarkerRegistry` up to real game data (Aeroclaim/OPAC claimed sub-levels, placed flight waypoints, detected landing pads) - the registry exists but nothing populates it yet.
- `create_file_id` / exact Create 6.0.10 Maven coordinate still needs confirming against a real Gradle run.
- This revision has not been run through `.\gradlew build` - no Gradle/JDK environment was available here. Next step is a local build and the resulting compiler output.


## 0.4.0 implementation notes
- Kept the existing package structure rather than replacing the 0.3.0 foundation.
- Installed the user-supplied Geo/animation exports under `assets/flightcomputer/geo` and `assets/flightcomputer/animations`.
- Added four independent GeckoLib controllers so the engaged and stabiliser latches can remain in their own physical states while mode/display actions remain separate.
- Added server-side persistence/sync for engaged, stabiliser, flight mode and short display/mode press pulses.
- Added `NavigationConsoleScreen` and retained the old `FlightMapScreen` as a lightweight standalone map implementation.
- Added `FlightComputerNetwork.ControllerActionPayload`.
- Added `ControllerLinkRegistry`, `VectorLink`, `FlightLinkToolItem`, and `LinkOverlayScreen` as the next-stage link system scaffold.
- Added `ModCreativeTabs`.

## Phase 5.2 runtime integration
- Connected the server tick to the real thruster registry, six-axis stabiliser, MPC navigator, allocator and thermal load path.
- Published live route/thermal/power telemetry to nearby clients.
- Kept thermal and cooling as standalone consoles and exposed navigation buttons to them from every navigation page.
- Moved route waypoint/waystone selection into the Route page itself.
