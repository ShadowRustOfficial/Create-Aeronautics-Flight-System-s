# Phase 5.2 Patch Manifest — Flight Operations Expansion

Every item below is explicitly part of Phase 5.2. Nothing in this list is a future idea to be silently dropped from the patch.

| Addition | Base / structure | Runtime/UI integration |
|---|---|---|
| Truly isolated subsystem screens | **ADDED** | Operations screen added; existing navigation remains isolated |
| Ship name + callsign + stable UUID separation | **ADDED** | Identity controls added |
| Compact map contacts | **ADDED** | Contact model/registry added |
| Right-click contact details | **ADDED** | Dedicated contact details surface + interaction boundary added |
| Dynamic contact target | **ADDED** | Contact target contract is established; live server contact feed remains integration work |
| MANUAL / STABILIZED / AUTOPILOT | **EXISTING + RETAINED** | Existing controller path preserved |
| Independent altitude/heading/position/velocity holds | **ADDED** | Hold state/network controls added |
| NORMAL / COMBAT / LANDING / EMERGENCY profiles | **ADDED** | Profile state/network controls added |
| DEFENSIVE combat mode | **ADDED** | Home/evasive/return state and controls added; allocator/runtime integration remains to be wired |
| OFFENSIVE combat mode | **ADDED** | Callsign target state and controls added; live contact pursuit remains to be wired |
| Multi-thruster vector banks | **EXISTING + RETAINED** | Universal allocator remains source of truth |
| Thruster health/degradation | **ADDED** | Health model added; live propulsion health feed remains to be wired |
| Thermal/Cooling hard screen separation | **EXISTING + RETAINED** | Separate screens remain required |
| Cooling policy modes | **ADDED** | Cooling mode foundation added |
| Emergency controls | **ADDED** | Emergency state/control foundation added |
| Landing scan / safe landing | **ADDED** | Landing state/mode and UI controls added; terrain landing controller remains to be wired |
| Auto-docking scan/alignment/approach | **ADDED** | Docking state/target contract and UI controls added; connector scanner/runtime remains to be wired |
| Red docking override | **ADDED** | Override state and network action added |
| Route segments | **ADDED** | Route segment model added; existing route UI/runtime still needs conversion to the segment list |
| Waystones / Waypoints in Route | **RETAINED** | Existing map providers remain the source for route selection |
| Collision / terrain safety | **ADDED** | Objective priority foundation added |
| Pre-flight check | **ADDED** | Actionable checklist model added |
| Diagnostics / control-loop telemetry | **ADDED** | Existing telemetry retained; expansion fields are staged through the new models |
| Central objective arbitration | **ADDED** | Priority arbiter added |
| Universal small/large vehicle behaviour | **RETAINED** | No fixed vehicle-size assumptions introduced |
| Reduced 20M FE power/heat baseline | **IMPLEMENTED** | 500/300/200 FE/t baseline and 0.04 heat/t baseline retained |

## Definition of done

A row is not finally complete until its base data model, server-authoritative network path, runtime behaviour and isolated UI are all connected and the clean Gradle build passes. The manifest is intentionally explicit so that UI-only stubs cannot be mistaken for completed flight mechanics.
