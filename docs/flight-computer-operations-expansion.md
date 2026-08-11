# Aero Flight Computer — Phase 5.2 Operations Expansion Contract

This document is the authoritative implementation checklist for the Flight Computer expansion discussed since last night. Every item below is part of this patch line and must be implemented at its base data/control structure before UI polish or gameplay validation is considered complete.

## 0. Non-negotiable UI isolation

Every major subsystem is a genuinely separate `Screen` surface with its own widget lifecycle. Selecting a subsystem must show only that subsystem's content. The shared navigation header is allowed, but no other subsystem's widgets, labels, inventory bays, thermal panels or overlays may render behind the selected screen.

Subsystems:

- MAP — native terrain/map, player/controller markers, compact Flight Computer contacts, map controls.
- ROUTE — route plan, coordinates, Waystones, Waypoints, route segments, dynamic contact targets and route execution.
- FLIGHT CONTROL — engagement, MANUAL/STABILIZED/AUTOPILOT, independent holds, PID/control profile and live control state.
- DIAGNOSTICS — health, power, control-loop, propulsion authority, thruster failures and pre-flight checks.
- THERMAL — temperature, heat load, thermal state, lockout/recovery and thermal protection only.
- COOLING — cooling bays, installed upgrades, cooling tier and cooling mode only.
- IDENTITY — ship name, callsign, owner, stable UUID and map-contact visibility only.
- COMBAT — exactly DEFENSIVE or OFFENSIVE assistance, target/home configuration and combat flight state only.
- LANDING — scan and safe landing assistance only.
- DOCKING — docking scan, alignment, approach, docking state and red override only.
- SYSTEM — emergency controls and system-level pre-flight/override functions only.

Do not inject subsystem UI through `Render.Pre`/`Render.Post` overlays. Do not solve screen separation with stale widget clearing. The active screen owns its own widgets.

## 1. Ship identity and custom identifier

Add persistent player-facing identity data while retaining the stable internal Flight Computer/sub-level UUID.

Fields:

- Ship Name
- Callsign
- Owner
- Stable Flight Computer/Sub-Level UUID
- Map Contact Visibility

Ship Name and Callsign use text boxes with small `SET NAME` / `SET CALLSIGN` actions. The display identity may reproduce the Aeronautics Simulated nameplate-style behaviour, but the stable internal UUID must not be destroyed or replaced.

## 2. Map contacts

Flight Computer contacts are compact markers. Do not permanently display detailed identity data over the map.

Right-clicking a contact marker opens its information panel containing, where permitted:

- Ship Name
- Callsign
- Owner
- Distance
- Altitude
- Velocity
- Heading
- Flight mode/status
- Stable contact UUID
- Contact age/staleness

The contact panel may expose:

- `TRACK CONTACT`
- `SET AS NAVIGATION TARGET`

Contacts are live telemetry records. A stale contact becomes `LOST CONTACT`; the controller must not continue flying indefinitely toward its last known coordinate.

## 3. Flight modes and independent holds

Primary modes remain:

- MANUAL
- STABILIZED
- AUTOPILOT

Independent secondary holds are separate controls and can be combined as appropriate:

- Altitude Hold
- Heading Hold
- Position Hold
- Velocity Hold

Do not make Autopilot a monolithic function that owns every hold.

Control profiles are separate from primary flight mode:

- NORMAL
- COMBAT
- LANDING
- EMERGENCY

## 4. Combat flight profile

Combat has exactly two operational modes.

### DEFENSIVE

- Text box for configurable Home/Escape location.
- Small `SET HOME` action.
- Fast evasive manoeuvre assistance.
- Stabilisation remains available while evading.
- `FLEE / RETURN HOME` drives toward the configured home location.
- Survival, terrain/collision safety and return-to-home outrank normal route objectives.

### OFFENSIVE

- Text box for target callsign.
- Small `SET TARGET` action.
- Resolve against active Flight Computer contacts.
- Track live target position/velocity/heading rather than a stale coordinate.
- Display contact resolution, distance, relative velocity and bearing.
- `ENGAGE COMBAT ASSIST` and `ABORT` controls.
- Offensive assistance never fires or controls weapons; weapon systems remain independent.

Combat assistance must be fast enough to support evasive manoeuvres and asymmetric multi-thruster control.

## 5. Thruster banks, authority and failure handling

The universal allocator remains based on actual thruster:

- local direction
- mount position
- vehicle orientation
- available force
- torque authority
- vehicle mass/inertia

Multi-thruster-per-vector banks are first-class. Never assume a fixed number of thrusters, craft size or mass.

Add structural support for:

- primary thrust
- manoeuvre thrust
- vertical thrust
- braking thrust
- combat manoeuvre thrust
- emergency thrust

Diagnostics should expose bank authority and degraded/failed thruster counts. The allocator should redistribute authority when a bank loses usable thrust rather than blindly continuing with the failed output.

## 6. Thermal and cooling separation

THERMAL and COOLING remain completely independent screens.

THERMAL owns:

- temperature
- normal/warm/hot/critical/shutdown state
- heat load
- thermal lockout
- recovery
- power/thermal protection

COOLING owns:

- three cooling bays
- upgrade insertion/removal
- active cooling tier
- cooling rate
- cooling mode

Cooling modes have structural support for:

- PASSIVE
- BALANCED
- AGGRESSIVE
- EMERGENCY

The current moderated 20M FE power/heat baseline remains part of this patch line.

## 7. Emergency systems

SYSTEM/EMERGENCY controls must include structural support for:

- Emergency Stabilise
- Emergency Brake
- Thrust Cutoff
- Controlled Descent
- Emergency Shutdown
- Emergency Return/Home

Emergency/override release must be capable of cancelling lower-priority objectives immediately.

## 8. Landing system

Landing is deliberately separate from docking.

### Landing Assist / Scan

- Scan nearby terrain/landing area.
- Determine a safe landing region/approach.
- Controlled descent.
- Safely settle below/over the selected landing approach height.
- Does not seek or connect to a docking block.

### Auto-Docking

- Scan for the nearest compatible docking block/connector associated with a landing pad.
- Establish a dynamic approach point.
- Align the vehicle with the connector.
- Reduce relative velocity.
- Approach the connector.
- Complete docking when the connector condition is satisfied.
- Hold after docking.

The docking target must remain dynamic and must not be reduced permanently to an old coordinate.

## 9. Docking override

Auto-Docking has a clearly visible red `OVERRIDE` control.

When activated:

- docking automation immediately relinquishes control;
- old docking targets/commands are cancelled;
- pilot/manual control returns immediately;
- the controller can take off without disabling the whole Flight Computer.

The override must be safe to activate during an approach.

## 10. Navigation and route planning

ROUTE owns:

- Waystones
- Waypoints
- coordinate destinations
- route segments
- ordering/reordering
- route execution
- dynamic Flight Computer contacts

A route segment can eventually carry:

- target
- altitude
- desired speed
- arrival behaviour

Dynamic Flight Computer contacts can become navigation targets without converting them into a permanent static coordinate.

## 11. Collision / terrain awareness

The control stack has structural support for:

- forward clearance
- terrain clearance
- obstacle detection
- safe braking/avoidance

Safety can interrupt route, combat tracking, landing or docking when an immediate collision hazard is detected.

## 12. Pre-flight check

DIAGNOSTICS/SYSTEM should provide a pre-flight checklist covering:

- Flight Computer present
- power available
- stabiliser available
- multi-vector thrust coverage
- thruster authority
- braking authority
- cooling
- thermal state
- route/navigation readiness
- terrain safety
- docking/landing readiness where requested
- degraded/failed banks

The result should identify actionable failures instead of only reporting a generic failure.

## 13. Diagnostics and telemetry

Diagnostics should expose enough live information to explain why the craft is not behaving correctly:

- mass
- inertia where available
- position
- velocity
- heading/pitch/roll
- angular rates
- control-loop rate
- PID values
- control authority
- thruster bank authority
- power draw/storage
- thermal load/state
- target state
- route state
- active safety override

## 14. Control priority

The runtime must resolve competing objectives centrally rather than letting separate features fight over the thrusters:

1. Emergency / explicit override release
2. Collision / terrain safety
3. Thermal / power protection
4. Defensive flee / emergency return
5. Auto-docking approach/alignment
6. Landing assist
7. Offensive contact tracking
8. Route navigation
9. Stabilisation / independent holds
10. Manual assistance

A higher-priority system must be able to cancel a lower-priority objective cleanly.

## 15. Universal vehicle requirement

Everything must work for small and massive vehicles.

No fixed dimensions, fixed mass, fixed thruster count or hand-tuned vector assumptions. Multi-thruster-per-vector allocation, actual mount offsets, local directions, orientation and physical inertia remain the source of truth.

## 16. Patch implementation rule

Every item in this document is marked **ADDED TO PHASE 5.2**. The implementation order is:

1. data/state structures and persistence;
2. server-authoritative network contracts;
3. control/runtime objective arbitration;
4. contact/identity registry and live telemetry;
5. isolated subsystem screens;
6. UI polish;
7. compile validation;
8. gameplay validation on small and large multi-thruster vehicles.

Do not mark a feature complete merely because a button or screen exists. Its state, network path and runtime behaviour must exist at the base layer.

## 17. Validation gate

Before declaring Phase 5.2 complete:

1. Open every subsystem repeatedly; only its own content may render.
2. Verify Thermal/Cooling never leak into other screens.
3. Set ship name/callsign and verify persistence after reload/rejoin.
4. Verify compact map contact markers and right-click-only identity details.
5. Verify defensive home/evasive/return behaviour.
6. Verify offensive callsign tracking and stale-contact handling.
7. Verify independent holds.
8. Verify Landing Scan/safe descent without docking.
9. Verify Auto-Docking scan/alignment/approach/dock.
10. Activate red docking override during approach and verify immediate manual control.
11. Verify emergency controls cancel lower-priority objectives.
12. Verify pre-flight diagnostics catch missing/degraded control authority.
13. Verify multi-thruster allocation on both small and massive vehicles.
14. Run `./gradlew clean build` and resolve every compile error before gameplay validation.
