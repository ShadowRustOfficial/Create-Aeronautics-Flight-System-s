# Flight Computer — Sound Physics Compatibility

## Design

Flight Computer does not hard-link against Sound Physics Remastered or Sound Physics: Aeronautics. The compatibility layer is optional and detects the shared `sound_physics_remastered` mod id at runtime.

The Flight Computer audio layer supplies ordinary positional Minecraft sounds:

- `engine_heat_critical`
- `engine_overheat`
- `fire_systems_active`
- `fire_neutralised`
- `emergency_shutdown`
- `stabiliser_ambient`

The stabiliser ambience is deliberately emitted as a `SoundSource.BLOCKS` sound with normal linear attenuation and `relative=false`. This is the important integration boundary: Sound Physics can then intercept it as a positional source instead of treating it as player-relative UI audio.

## Sound Physics behaviour

When `sound_physics_remastered` is not installed, Minecraft's normal positional attenuation is used.

When Sound Physics Remastered is installed, the same sound source is eligible for its normal acoustic processing. Sound Physics: Aeronautics keeps the same mod id and adds Sable sublevel acoustic support, so the moving/rotated geometry of Aeronautics vehicles can participate in occlusion and related calculations without Flight Computer duplicating an acoustic engine.

Supported Doppler behaviour is likewise left to Sound Physics: Aeronautics. Flight Computer only keeps the source moving with the authoritative Sable/world position.

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

The supplied OGG pack contains the matching converted warning/ambient assets. The two newest assets, `fire_neutralised.ogg` and `emergency_shutdown.ogg`, are part of the current user-provided sound set.
