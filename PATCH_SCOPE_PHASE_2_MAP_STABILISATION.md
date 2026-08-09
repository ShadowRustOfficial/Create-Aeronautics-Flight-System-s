# Phase 2 Map Stabilisation Patch Scope

This branch is reserved for the Flight Computer map stabilisation pass.

## Scope

- Preserve the working Xaero World Map Bridge and native Xaero terrain rendering.
- Lock the Flight Computer map to fixed 1x zoom and remove Flight Computer zoom controls/handling.
- Correct player/cursor positioning using the Bridge's existing world-to-screen coordinate system.
- Isolate native Xaero rendering to the Flight Computer map viewport so the real Xaero World Map cannot leak outside it.
- Preserve Xaero's native waypoint rendering.
- Preserve and tidy the existing Route screen and its waypoint-selection interaction.
- Do not restore the old custom waypoint/Waystone marker system.
- Do not modify working Controller/Sub-Level logic.
- Do not add or configure Mekanism compatibility; an external compatibility mod handles that separately.
- Do not begin MPC/PID work in this patch.

## Validation

Run `gradlew clean build` after implementation and resolve all compilation errors before considering the patch complete.
