# Flight Computer — Sound Physics Compatibility

## Audio layers

The Flight Computer audio system intentionally separates continuous flight ambience from transient flyby audio:

- `ambient_drone_quiet` — quiet, continuous ship/stabiliser drone.
- `ambient_flight` — deeper continuous propulsion ambience; client volume/intensity follows flight speed and fades out as the aircraft settles.
- `flyby_aircraft` — dedicated external flyby effect. It is not used as the normal speed-up/slow-down layer.
- `engine_powering_down` — engine spool-down sound played with the authoritative emergency-shutdown transition.

The continuous ambience sources are emitted as normal world-space `SoundSource.BLOCKS` sounds with `relative=false`. Sound Physics Remastered / Sound Physics: Aeronautics therefore remains responsible for distance attenuation, block occlusion, absorption, reverberation and supported Doppler processing.

## External-only flyby

Flyby audio is an exterior effect. Before a flyby source is created, the compatibility layer can compare the listener's Sable sublevel with the Sable sublevel containing the source position.

- Player outside the source sublevel: flyby is eligible.
- Player inside the same source sublevel: flyby is suppressed completely.
- Sable unavailable/API lookup unavailable: the compatibility layer fails open so an external flyby is not accidentally suppressed.

## Emergency shutdown audio

`EMERGENCY_SHUTDOWN` remains server-authoritative. On the accepted shutdown transition the controller now emits both:

1. `emergency_shutdown`
2. `engine_powering_down`

The second sound is the supplied engine spool-down effect. A controller-local 200-tick cooldown prevents repeated shutdown presses from replaying the pair more often than once every 10 seconds.

## Safety

This compatibility branch does not modify MPC, PID, stabilisation, autopilot, route logic, thruster allocation, thermal calculations, terrain generation, or diagnostics beyond the audio integration boundary.
