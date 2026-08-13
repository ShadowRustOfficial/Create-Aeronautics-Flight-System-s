# Sound Physics + Sable Compatibility — Verified Implementation

## What was verified

The Sound Physics: Aeronautics source does **not** expose the exact `SoundPhysicsAPI.registerGeometryProvider(...)`, `registerMovingSource(...)`, `IAcousticGeometryProvider`, or `IMovingSoundSource` names from the earlier proposed integration plan. Those names were therefore **not** implemented as assumed.

Instead, the Aeronautics fork adds its own acoustic-provider architecture to Sound Physics:

- `AcousticScenes.setProvider(...)`
- `AcousticWorldProvider#createScene(...)`
- `AcousticScene#rayCast(...)`
- `SableAcousticScene`, which combines normal/root world rays with transformed Sable acoustic spaces
- `SableAcousticIntegration`, which installs the Sable acoustic provider and Sable Doppler provider

The verified fork implementation transforms world ray endpoints into each Sable space's local coordinates, raycasts the local space, then transforms the hit back into world space. See the source paths in the repository `Halew3/Sound-Physics-Aeronautics`.

Upstream Sound Physics Remastered instead performs direct occlusion through its internal `calculateOcclusion` / `runOcclusion` path and uses normal world-level raycasts. The current upstream source does not expose the Aeronautics provider API described above.

## Current Flight Computer implementation

This branch intentionally uses a smaller, soft compatibility surface:

```text
Flight Computer sound
        |
        v
Minecraft positional SoundInstance
        |
        +--> Sound Physics Remastered (optional)
        |       |
        |       +--> normal SPR occlusion/reverb
        |       |
        |       +--> our optional Sable occlusion hook
        |
        +--> vanilla positional audio when SPR is absent
```

### Main-thread snapshot boundary

`SableAcousticCache` performs Sable and client-world access during the client tick only. It caches an acoustic scalar result for the current Flight Computer sound source(s). The Sound Physics mixin consumes that prepared value on the audio-processing side and does not query Sable, block states, chunks, plots, or transforms there.

This boundary is deliberate because Sound Physics itself performs client sound processing on paths that can be sensitive to world access, and Sable sublevels expose moving/rotated world state.

### Sable transforms

The bridge uses the verified Sable API surface:

- `SubLevel.logicalPose()`
- `Pose3d.transformPositionInverse(Vec3)`
- `SubLevel.getPlot()`
- `EmbeddedPlotLevelAccessor(LevelPlot)`
- `SubLevel.getUniqueId()`

The Sable source also verifies that `logicalPose()` is the current pose containing translation/rotation and that `lastPose()` stores the previous tick's pose.

### Sound source registration

The optional client `SoundManager` mixin registers Flight Computer sound positions for Sable snapshotting. The Flight Computer ambience also explicitly registers its current moving source every tick.

### Current scope

The first implementation phase adds **Sable hull/interior direct occlusion** to normal SPR occlusion for Flight Computer sound sources. It does not claim to reproduce the full Aeronautics fork's reflected-ray scene provider or its full Doppler pipeline yet.

That is intentional: the branch starts with the smallest verified hook into upstream SPR rather than creating an unverified API or copying the fork wholesale.

## Fallback rules

- No Sound Physics: Flight Computer uses normal Minecraft positional audio.
- No Sable: Sable bridge stays inactive.
- Sable API unavailable: acoustic contribution is zero; no gameplay systems are affected.
- SPR target method unavailable: optional mixin is soft-gated and may simply not apply.
- Acoustic snapshot fails: Flight Computer sound continues normally.

## Existing flight-control protection

No MPC, PID, stabilisation, autopilot, route, thruster, terrain, thermal, or cooling control code is required by this compatibility layer. The integration is isolated to the client audio path and optional SPR mixins.

## Source references

Sound Physics: Aeronautics:
- `common/src/main/java/com/sonicether/soundphysics/acoustic/AcousticScenes.java`
- `common/src/main/java/com/sonicether/soundphysics/acoustic/AcousticWorldProvider.java`
- `common/src/main/java/com/sonicether/soundphysics/acoustic/AcousticScene.java`
- `neoforge/src/main/java/com/sonicether/soundphysics/integration/sable/SableAcousticIntegration.java`
- `neoforge/src/main/java/com/sonicether/soundphysics/integration/sable/SableAcousticScene.java`

Sable:
- `common/src/main/java/dev/ryanhcode/sable/sublevel/SubLevel.java`
- `common/src/main/java/dev/ryanhcode/sable/api/SubLevelHelper.java`
- `common/src/main/java/dev/ryanhcode/sable/sublevel/plot/EmbeddedPlotLevelAccessor.java`
- `common/src/main/java/dev/ryanhcode/sable/sound/MovingSoundInstanceDelegate.java`

Upstream Sound Physics Remastered:
- `common/src/main/java/com/sonicether/soundphysics/SoundPhysics.java`

This branch reimplements the relevant behaviour independently; it does not copy Sound Physics: Aeronautics source code into Flight Computer.
