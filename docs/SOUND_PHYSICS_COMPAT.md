# Flight Computer — Sound Physics Compatibility

## Design

Flight Computer does not hard-link against Sound Physics Remastered or Sound Physics: Aeronautics. The compatibility layer is optional and detects the shared `sound_physics_remastered` mod id at runtime.

The Flight Computer audio layer supplies ordinary positional Minecraft sounds:

- `engine_heat_critical`
- `warning_engine_overheat`
- `fire_systems_active`
- `fire_neutralised`
- `emergency_shutdown`
- `ambient_ship`

The stabiliser ambience is deliberately emitted as a `SoundSource.BLOCKS` sound with normal linear attenuation and `relative=false`. The source position follows authoritative flight telemetry, while authoritative Route telemetry supplies the Stabiliser state. This is the important integration boundary: Sound Physics can process it as a moving positional source instead of treating it as player-relative UI audio.

## Sound Physics behaviour

When `sound_physics_remastered` is not installed, Minecraft's normal positional attenuation is used.

When Sound Physics Remastered is installed, the same sound source is eligible for its normal acoustic processing. Sound Physics: Aeronautics keeps the same mod id and adds Sable sublevel acoustic support, so the moving/rotated geometry of Aeronautics vehicles can participate in occlusion and related calculations without Flight Computer duplicating an acoustic engine.

Supported Doppler behaviour is likewise left to Sound Physics: Aeronautics. Flight Computer only keeps the source moving with the authoritative Sable/world position.

## External-only flyby rule

Aircraft flybys are intended to be exterior-only effects. Before a flyby sound is created, the compatibility layer can compare the listener's Sable sublevel with the Sable sublevel containing the flyby source position.

- Player outside the source sublevel: flyby is eligible.
- Player inside the same source sublevel: flyby is suppressed completely.
- Sable unavailable/API lookup unavailable: the compatibility layer fails open and treats the listener as external rather than suppressing an external effect accidentally.

The check uses Sable's runtime `HELPER.getContaining(Entity)` and `HELPER.getContaining(Level, Position)` APIs and compares the returned sublevel `getUniqueId()` values. It is reflection-only, so the Flight Computer project does not acquire a hard Sable compile dependency.

## State safety

Route telemetry is treated as authoritative for whether Stabiliser ambience should be active. Client-side Route state expires after two seconds without an update so a vanished or unloaded controller cannot leave a stale ambient sound running indefinitely.

## Safety

No flight-control classes are modified by this integration. MPC, PID, stabilisation, autopilot, route logic, thruster allocation, thermal calculations, terrain generation and diagnostics remain outside the sound compatibility layer.

The branch is intentionally a soft compatibility layer: Flight Computer starts and functions without Sound Physics installed.

## Asset names

The canonical resource names are:

- `assets/flightcomputer/sounds/ambient_ship.ogg`
- `assets/flightcomputer/sounds/engine_heat_critical.ogg`
- `assets/flightcomputer/sounds/warning_engine_overheat.ogg`
- `assets/flightcomputer/sounds/fire_systems_active.ogg`
- `assets/flightcomputer/sounds/fire_neutralised.ogg`
- `assets/flightcomputer/sounds/emergency_shutdown.ogg`

The user-provided converted OGG pack contains all six matching assets. The binary OGGs are distributed separately from this source-only GitHub compatibility branch because the available repository write interface is text-file based.
