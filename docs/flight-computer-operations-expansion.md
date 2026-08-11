# Aero Flight Computer — Operations Expansion

This document is the implementation contract for the next Flight Computer pass. The UI must use independent screens for each major subsystem; no subsystem panel may be injected into another screen's render pass.

## UI isolation rule

Each major subsystem owns its own `Screen` surface and widget lifecycle:

- MAP — map and map controls only.
- ROUTE — route destinations, Waystones, Waypoints, coordinates and route execution only.
- FLIGHT CONTROL — engagement, manual/stabilized/autopilot state, control profiles and live control telemetry only.
- DIAGNOSTICS — health, power, control-loop and propulsion diagnostics only.
- THERMAL — temperature, heat state, lockout and thermal protection only.
- COOLING — cooling bays, installed upgrades and cooling controls only.
- IDENTITY — ship name, callsign, owner and map-contact visibility only.
- COMBAT — defensive/offensive combat assistance only.
- LANDING — landing scan and safe landing assistance only.
- DOCKING — docking scan, alignment, approach and docking control only.

The shared header/navigation may be reused, but the active subsystem screen must render only its own content. Do not use `Render.Pre`/`Render.Post` overlays to inject subsystem UI into another screen.

## Ship identity

Identity is persistent to the Flight Computer/sub-level and should retain the underlying stable UUID. A custom display identifier may mirror the Aeronautics Simulated nameplate behaviour without destroying the stable internal UUID.

Identity fields:

- Ship Name
- Callsign
- Owner
- Stable Flight Computer/Sub-Level UUID
- Map Contact Visibility

Ship name and callsign are set through explicit small action buttons beside their text fields.

## Map contacts

Other Flight Computers are represented as map contacts but their detailed identity information is NOT permanently displayed.

The map marker should remain compact. Right-clicking a Flight Computer marker opens a contact information panel containing, where permitted:

- Ship Name
- Callsign
- Owner
- Distance
- Altitude
- Heading
- Velocity
- Flight mode/status
- Stable contact UUID

The contact panel may provide actions such as `TRACK CONTACT` and `SET AS NAVIGATION TARGET`.

## Combat control profile

Combat is a control profile separate from the primary flight mode. It has exactly two operational modes:

### Defensive

The pilot enters a configurable Home/Escape location in a text box and commits it with a small `SET HOME` button.

The controller can perform fast evasive manoeuvres while maintaining stabilisation and, when activated, flee toward the configured home location. It should prioritise survival, obstacle avoidance and route-to-home over normal navigation objectives.

### Offensive

The pilot enters a target callsign in a text box and commits it with a small `SET TARGET` button.

The controller resolves the callsign against active Flight Computer contacts and can track that contact as a dynamic navigation target. The target position must be refreshed from live contact telemetry rather than converted into a static coordinate once.

Offensive mode must not automatically fire or control weapons. It provides flight/track assistance only; any weapon system remains separately controlled.

Combat UI should clearly expose:

- Mode: DEFENSIVE / OFFENSIVE
- Home or Target field depending on mode
- Target/contact resolution state
- Current distance
- Relative velocity
- Bearing/heading
- `ENGAGE COMBAT ASSIST`
- `ABORT`

## Landing modes

Landing is separate from docking.

### Landing Assist / Scan

Scan the nearby terrain/landing area and select a safe landing region. The controller performs a controlled descent and stops safely below/over the selected landing approach height. It does NOT attempt to connect to a docking block.

### Auto-Docking

Scan for the nearest compatible docking block/connector associated with a landing pad. The controller should:

1. Find a valid docking target.
2. Establish an approach point.
3. Align vehicle orientation with the docking connector.
4. Reduce relative velocity.
5. Approach the connector.
6. Complete docking when the connector/docking condition is satisfied.
7. Hold position after docking.

The docking target is a dynamic target and must not be permanently replaced by a stale coordinate.

## Docking override

Auto-Docking has a clearly visible red `OVERRIDE` control.

- Normal state: Auto-Docking may control alignment/approach.
- Override active: automatic docking control is immediately released and pilot control is restored.
- Override must be safe to use during an approach and must not leave the controller continuing to command the old docking target.
- Pilot can then take off/manually manoeuvre without having to disable the entire Flight Computer.

## Control priority

The runtime should resolve objectives in this order:

1. Emergency/override release.
2. Collision/terrain safety.
3. Thermal/power protection.
4. Defensive flee / emergency return.
5. Docking approach/alignment when explicitly engaged.
6. Landing assist.
7. Offensive contact tracking when explicitly engaged.
8. Route navigation.
9. Stabilisation/heading/altitude assistance.
10. Manual pilot input.

The exact implementation may refine priority ordering, but a higher-priority safety function must be able to cancel a lower-priority objective cleanly.

## Dynamic contact targeting

A Flight Computer contact is a first-class navigation target. It is represented by stable identity plus live telemetry:

- UUID
- Ship name
- Callsign
- World position
- Velocity
- Heading
- Flight mode/status
- Last update tick/time

A contact target must expire or become `LOST CONTACT` when telemetry is stale. The controller must not blindly fly to the last known position forever.

## Universal vehicle behaviour

All control systems must remain vehicle-size independent. Thruster allocation must continue to use actual thruster positions, local directions, vehicle orientation, available force and torque authority. No fixed number of thrusters, fixed craft dimensions or fixed mass assumptions may be used.

## Testing requirements

Before declaring this expansion complete:

1. Open each subsystem and verify only that subsystem's UI is rendered.
2. Switch between every tab repeatedly and confirm no widgets/text from the previous screen remain.
3. Set a ship name/callsign and reload/rejoin to confirm persistence.
4. Verify another ship marker remains compact until right-clicked.
5. Verify right-click contact details show current telemetry.
6. Defensive combat: set home, engage, verify evasive flight and return-to-home behaviour.
7. Offensive combat: set callsign, verify live target tracking and stale-contact handling.
8. Landing Assist: scan and perform controlled landing without docking.
9. Auto-Docking: locate nearest valid docking connector, align, approach and dock.
10. Activate red docking override during approach and verify automatic commands stop immediately and manual control returns.
11. Test all behaviours on both small and large vehicles with multiple thrusters per vector.
12. Run `./gradlew clean build` after implementation and resolve all compile errors before gameplay validation.
