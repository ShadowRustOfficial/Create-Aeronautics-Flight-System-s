package com.flightcomputer.avionics;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative base data model for the Phase 5 flight-operations expansion. */
public final class FlightOperationsState {
    private String shipName = "";
    private String callsign = "";
    private boolean mapContactVisible = true;
    private CombatMode combatMode = CombatMode.DEFENSIVE;
    private FlightControlProfile profile = FlightControlProfile.NORMAL;
    private final EnumSet<FlightHold> holds = EnumSet.noneOf(FlightHold.class);
    private String defensiveHome = "";
    private String offensiveCallsign = "";
    private boolean combatAssist;
    private LandingMode landingMode = LandingMode.SCAN_ONLY;
    private boolean landingAssist;
    private DockingState dockingState = DockingState.IDLE;
    private boolean autoDocking;
    private boolean dockingOverride;
    private boolean terrainSafety = true;
    private boolean emergencyReturn;
    private boolean preflightPassed;
    private UUID trackedContact;

    public String shipName() { return shipName; }
    public String callsign() { return callsign; }
    public boolean mapContactVisible() { return mapContactVisible; }
    public CombatMode combatMode() { return combatMode; }
    public FlightControlProfile profile() { return profile; }
    public Set<FlightHold> holds() { return EnumSet.copyOf(holds); }
    public String defensiveHome() { return defensiveHome; }
    public String offensiveCallsign() { return offensiveCallsign; }
    public boolean combatAssist() { return combatAssist; }
    public LandingMode landingMode() { return landingMode; }
    public boolean landingAssist() { return landingAssist; }
    public DockingState dockingState() { return dockingState; }
    public boolean autoDocking() { return autoDocking; }
    public boolean dockingOverride() { return dockingOverride; }
    public boolean terrainSafety() { return terrainSafety; }
    public boolean emergencyReturn() { return emergencyReturn; }
    public boolean preflightPassed() { return preflightPassed; }
    public UUID trackedContact() { return trackedContact; }

    public void setShipName(String value) { shipName = clean(value, 48); }
    public void setCallsign(String value) { callsign = clean(value, 24); }
    public void setMapContactVisible(boolean value) { mapContactVisible = value; }
    public void setCombatMode(CombatMode value) { combatMode = value == null ? CombatMode.DEFENSIVE : value; }
    public void setProfile(FlightControlProfile value) { profile = value == null ? FlightControlProfile.NORMAL : value; }
    public void setDefensiveHome(String value) { defensiveHome = clean(value, 128); }
    public void setOffensiveCallsign(String value) { offensiveCallsign = clean(value, 24); }
    public void setCombatAssist(boolean value) { combatAssist = value; }
    public void setLandingMode(LandingMode value) { landingMode = value == null ? LandingMode.SCAN_ONLY : value; }
    public void setLandingAssist(boolean value) { landingAssist = value; }
    public void setDockingState(DockingState value) { dockingState = value == null ? DockingState.IDLE : value; }
    public void setAutoDocking(boolean value) { autoDocking = value; if (!value && dockingState != DockingState.DOCKED) dockingState = DockingState.IDLE; }
    public void setDockingOverride(boolean value) {
        dockingOverride = value;
        if (value) { autoDocking = false; dockingState = DockingState.OVERRIDDEN; }
        else if (dockingState == DockingState.OVERRIDDEN) dockingState = DockingState.IDLE;
    }
    public void setTerrainSafety(boolean value) { terrainSafety = value; }
    public void setEmergencyReturn(boolean value) { emergencyReturn = value; }
    public void setPreflightPassed(boolean value) { preflightPassed = value; }
    public void setTrackedContact(UUID value) { trackedContact = value; }
    public void clearTrackedContact() { trackedContact = null; }

    public void setHold(FlightHold hold, boolean enabled) { if (hold != null) { if (enabled) holds.add(hold); else holds.remove(hold); } }
    public boolean hasHold(FlightHold hold) { return hold != null && holds.contains(hold); }

    /** Applies a server-side operation transition without touching the legacy flight controller state. */
    public FlightOperationsActionResult apply(FlightOperationsAction action) {
        if (action == null) return FlightOperationsActionResult.rejected(this, null, "INVALID_ACTION");
        switch (action) {
            case TOGGLE_MAP_CONTACT -> mapContactVisible = !mapContactVisible;
            case SET_COMBAT_DEFENSIVE -> { combatMode = CombatMode.DEFENSIVE; if (combatAssist) offensiveCallsign = ""; }
            case SET_COMBAT_OFFENSIVE -> combatMode = CombatMode.OFFENSIVE;
            case TOGGLE_COMBAT_ASSIST -> combatAssist = !combatAssist;
            case TOGGLE_LANDING_ASSIST -> landingAssist = !landingAssist;
            case TOGGLE_AUTO_DOCKING -> {
                setAutoDocking(!autoDocking);
                if (autoDocking) dockingOverride = false;
            }
            case DOCKING_OVERRIDE -> setDockingOverride(true);
            case TOGGLE_TERRAIN_SAFETY -> terrainSafety = !terrainSafety;
            case TOGGLE_EMERGENCY_RETURN -> emergencyReturn = !emergencyReturn;
            case CLEAR_TRACKED_CONTACT -> trackedContact = null;
        }
        return FlightOperationsActionResult.accepted(this, action);
    }

    public void resetOperationalAssist() {
        combatAssist = false; landingAssist = false; autoDocking = false; dockingOverride = false;
        emergencyReturn = false; trackedContact = null; dockingState = DockingState.IDLE;
    }

    public void save(CompoundTag tag) {
        tag.putString("ShipName", shipName); tag.putString("Callsign", callsign); tag.putBoolean("MapContactVisible", mapContactVisible);
        tag.putString("CombatMode", combatMode.name()); tag.putString("ControlProfile", profile.name());
        tag.putString("DefensiveHome", defensiveHome); tag.putString("OffensiveCallsign", offensiveCallsign); tag.putBoolean("CombatAssist", combatAssist);
        tag.putString("LandingMode", landingMode.name()); tag.putBoolean("LandingAssist", landingAssist);
        tag.putString("DockingState", dockingState.name()); tag.putBoolean("AutoDocking", autoDocking); tag.putBoolean("DockingOverride", dockingOverride);
        tag.putBoolean("TerrainSafety", terrainSafety); tag.putBoolean("EmergencyReturn", emergencyReturn); tag.putBoolean("PreflightPassed", preflightPassed);
        if (trackedContact != null) tag.putUUID("TrackedContact", trackedContact);
        CompoundTag holdTag = new CompoundTag(); for (FlightHold hold : FlightHold.values()) holdTag.putBoolean(hold.name(), holds.contains(hold)); tag.put("Holds", holdTag);
    }

    public static FlightOperationsState load(CompoundTag tag) {
        FlightOperationsState state = new FlightOperationsState();
        state.shipName = clean(tag.getString("ShipName"), 48); state.callsign = clean(tag.getString("Callsign"), 24);
        state.mapContactVisible = !tag.contains("MapContactVisible") || tag.getBoolean("MapContactVisible");
        state.combatMode = enumValue(CombatMode.class, tag.getString("CombatMode"), CombatMode.DEFENSIVE);
        state.profile = enumValue(FlightControlProfile.class, tag.getString("ControlProfile"), FlightControlProfile.NORMAL);
        state.defensiveHome = clean(tag.getString("DefensiveHome"), 128); state.offensiveCallsign = clean(tag.getString("OffensiveCallsign"), 24);
        state.combatAssist = tag.getBoolean("CombatAssist"); state.landingMode = enumValue(LandingMode.class, tag.getString("LandingMode"), LandingMode.SCAN_ONLY);
        state.landingAssist = tag.getBoolean("LandingAssist"); state.dockingState = enumValue(DockingState.class, tag.getString("DockingState"), DockingState.IDLE);
        state.autoDocking = tag.getBoolean("AutoDocking"); state.dockingOverride = tag.getBoolean("DockingOverride");
        state.terrainSafety = !tag.contains("TerrainSafety") || tag.getBoolean("TerrainSafety"); state.emergencyReturn = tag.getBoolean("EmergencyReturn");
        state.preflightPassed = tag.getBoolean("PreflightPassed"); state.trackedContact = tag.hasUUID("TrackedContact") ? tag.getUUID("TrackedContact") : null;
        if (tag.contains("Holds", CompoundTag.TAG_COMPOUND)) { CompoundTag holdTag = tag.getCompound("Holds"); for (FlightHold hold : FlightHold.values()) if (holdTag.getBoolean(hold.name())) state.holds.add(hold); }
        return state;
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String cleaned = value.trim().replaceAll("[\\r\\n\\t]", " ");
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }
}
