# Flight Computer 0.3.0
Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21.

Target integrations: Create 6.0.10, Create Aeronautics 1.3.0, Sable 2.0.3, Create Propulsion: Simulated 1.1.5, Aeroclaim 0.9.3, Open Parties and Claims 0.29.3, GeckoLib 4.8.4.

## What's in this revision

- Added GeckoLib 4.8.4 as the animation engine for the Flight Controller block, replacing the earlier plan to hand-code ModelPart rotations. The block entity, model, and renderer scaffolding live under `com.flightcomputer.block` and `com.flightcomputer.client.render`.
- Added the first real Java source tree for the mod: main mod class, block/item/block-entity registries, client renderer registration, `neoforge.mods.toml`.
- Started a first-party **Flight Map** screen (`com.flightcomputer.client.gui.FlightMapScreen`, default key `M`) instead of hooking into Xaero's Minimap/World Map. Neither Xaero mod publishes a documented public API for third-party marker registration - every integration found in the wild (way2wayfabric, server_waypoint, XaeroPlus, Map Link) works around that with unofficial waypoint-file bridges or internal-class access that isn't guaranteed stable across Xaero updates. A small purpose-built overlay avoids that fragility.
- The Flight Map is deliberately narrow in scope: it only draws markers that Flight Computer itself pushes into `MarkerRegistry` (flight waypoints, claimed sub-levels, landing pads), with per-category show/hide toggles. It does not track entities or mobs and does not attempt full terrain/chunk rendering.

## Still needed before this compiles clean

- **Model re-export**: `Flight Controller 1x1.java` in the models repo is a plain Blockbench `EntityModel` export (static geometry only). GeckoLib needs a different export - install the GeckoLib Blockbench plugin and re-export the same `.bbmodel` as `geo/flight_controller.geo.json` plus `animations/flight_controller.animation.json` (idle/active animations, referenced by name in `FlightControllerBlockEntity`). Drop both under `src/main/resources/assets/flightcomputer/`.
- A texture at `assets/flightcomputer/textures/block/flight_controller.png`.
- Carried-over note: the exact CurseForge file ID for Create 6.0.10 still hasn't been confirmed - check the `compileOnly("com.simibubi.create:...")` coordinate in `build.gradle` against a real Gradle run.

This revision was assembled without a live Gradle/JDK environment, so `.\gradlew build` hasn't been run against it yet. Send back the first compiler error and it'll get fixed the same way as always.
