# Flight Computer 0.5.0
Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21.

Target integrations: Create 6.0.10, Create Aeronautics 1.3.0, Sable 2.0.3, Create Propulsion: Simulated 1.1.5, Aeroclaim 0.9.3, Open Parties and Claims 0.29.3, GeckoLib 4.8.4.

## 0.5.0 foundation

- `FlightControllerState` is now the one persisted source of truth for controller state.
- GUI actions and Shift + Right Click panel actions use the same named `FlightControllerAction` payload and server-side dispatcher.
- `FlightControllerAnimationBridge` keeps Blockbench animation names in one mapping layer.
- The first Tier-1 physical-button layout is declarative in `FlightControllerButtonLayout`; adjust its local X/Z bounds when the final Blockbench panel layout changes.
- Updated the GeckoLib integration to the installed 4.8.4 API: no obsolete `GeckoLib.initialize()`, and the correct animatable cache package.
- Added the NeoForge/Minecraft 1.21.1 `BaseEntityBlock` codec implementation and removed all `DistExecutor` usage.

## Carried forward from 0.4.0

- Added GeckoLib 4.8.4 as the animation engine for the Flight Controller block, replacing the earlier plan to hand-code ModelPart rotations. The block entity, model, and renderer scaffolding live under `com.flightcomputer.block` and `com.flightcomputer.client.render`.
- Added the first real Java source tree for the mod: main mod class, block/item/block-entity registries, client renderer registration, `neoforge.mods.toml`.
- Started a first-party **Flight Map** screen (`com.flightcomputer.client.gui.FlightMapScreen`, default key `M`) instead of hooking into Xaero's Minimap/World Map. Neither Xaero mod publishes a documented public API for third-party marker registration - every integration found in the wild (way2wayfabric, server_waypoint, XaeroPlus, Map Link) works around that with unofficial waypoint-file bridges or internal-class access that isn't guaranteed stable across Xaero updates. A small purpose-built overlay avoids that fragility.
- The Flight Map is deliberately narrow in scope: it only draws markers that Flight Computer itself pushes into `MarkerRegistry` (flight waypoints, claimed sub-levels, landing pads), with per-category show/hide toggles. It does not track entities or mobs and does not attempt full terrain/chunk rendering.

## Still needed before this compiles clean

- **Model re-export**: `Flight Controller 1x1.java` in the models repo is a plain Blockbench `EntityModel` export (static geometry only). GeckoLib needs a different export - install the GeckoLib Blockbench plugin and re-export the same `.bbmodel` as `geo/flight_controller.geo.json` plus `animations/flight_controller.animation.json` (idle/active animations, referenced by name in `FlightControllerBlockEntity`). Drop both under `src/main/resources/assets/flightcomputer/`.
- A texture at `assets/flightcomputer/textures/block/flight_controller.png`.
- Carried-over note: the exact CurseForge file ID for Create 6.0.10 still hasn't been confirmed - check the `compileOnly("com.simibubi.create:...")` coordinate in `build.gradle` against a real Gradle run.

This revision was assembled without a live Gradle/JDK environment, so `.\gradlew build` hasn't been run against it yet. Send back the first compiler error and it'll get fixed the same way as always.


## 0.4.0 continuation

This revision preserves the existing GeckoLib/MarkerRegistry/source layout and adds the next layer discussed in the update plan:

- The supplied Blockbench Geo JSON and animation JSON are now installed at the resource paths expected by the renderer.
- Flight Controller state now maps to the supplied `Engaged`, `Stabiliser`, `Mode Select`, and `Display` animations. Toggle animations use GeckoLib hold-on-last-frame behavior so a switch stays depressed until the state is changed.
- Added the four-tab Navigation Console screen: Map, Route, Flight Control, Diagnostics. The map remains first-party and deliberately lightweight; terrain rendering and external Xaero integration are not claimed as complete.
- Added a server-authoritative controller-action payload so GUI controls change the block entity rather than only changing client visuals.
- Added a first-party Flight Link Tool and a wiring overlay/data model for vector-to-target bindings. The overlay is a scaffold for the actual Create redstone/vector adapter pass.
- Added a Flight Computer creative tab and a placeholder Flight Link Tool asset.
- Added a temporary placeholder Flight Controller texture so the resource set is runnable; replace it with the final texture.

### Animation naming
The current animation resource uses the names supplied in `model.animation.json`: `Engaged (Toggle on)`, `Engaged (Toggle off)`, `Stabiliser's (Toggle on)`, `Stabilisers (Toggle off)`, `Mode Select (Press)`, and `Display (Press)`. The malformed `Display (Press_` key from the supplied export was normalized to `Display (Press)`.

