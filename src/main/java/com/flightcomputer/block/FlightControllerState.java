package com.flightcomputer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything about a placed controller that must survive a save/reload, gathered in one
 * place so FlightControllerBlockEntity's NBT code stays small. This is the first slice
 * of the FlightControllerState described in the project architecture doc - it only
 * holds data for now, it does not dispatch actions or post AvionicsBus events yet.
 *
 * Target resolution order once navigation reads this: manual coordinates take over as
 * soon as they're set (setManualTarget), and setting a waypoint/waystone clears any
 * manual target so there is only ever one active target at a time.
 */
public final class FlightControllerState {

    private final Map<String, Boolean> buttonToggles = new LinkedHashMap<>();
    private String vehicleName = "";
    private String selectedWaypointId = null;
    private String selectedWaystoneId = null;
    private boolean hasManualTarget = false;
    private int targetX;
    private int targetY;
    private int targetZ;

    public boolean isToggled(String buttonId) {
        return buttonToggles.getOrDefault(buttonId, false);
    }

    public boolean toggle(String buttonId) {
        boolean next = !isToggled(buttonId);
        buttonToggles.put(buttonId, next);
        return next;
    }

    public String getVehicleName() {
        return vehicleName;
    }

    public void setVehicleName(String vehicleName) {
        this.vehicleName = vehicleName == null ? "" : vehicleName;
    }

    public String getSelectedWaypointId() {
        return selectedWaypointId;
    }

    public void setSelectedWaypointId(String waypointId) {
        this.selectedWaypointId = waypointId;
        this.selectedWaystoneId = null;
        this.hasManualTarget = false;
    }

    public String getSelectedWaystoneId() {
        return selectedWaystoneId;
    }

    public void setSelectedWaystoneId(String waystoneId) {
        this.selectedWaystoneId = waystoneId;
        this.selectedWaypointId = null;
        this.hasManualTarget = false;
    }

    public void setManualTarget(int x, int y, int z) {
        this.hasManualTarget = true;
        this.selectedWaypointId = null;
        this.selectedWaystoneId = null;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    public void clearTarget() {
        this.hasManualTarget = false;
        this.selectedWaypointId = null;
        this.selectedWaystoneId = null;
    }

    public boolean hasManualTarget() {
        return hasManualTarget;
    }

    @Nullable
    public BlockPos getManualTarget() {
        return hasManualTarget ? new BlockPos(targetX, targetY, targetZ) : null;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("VehicleName", vehicleName);
        if (selectedWaypointId != null) tag.putString("SelectedWaypoint", selectedWaypointId);
        if (selectedWaystoneId != null) tag.putString("SelectedWaystone", selectedWaystoneId);
        tag.putBoolean("HasManualTarget", hasManualTarget);
        if (hasManualTarget) {
            tag.putInt("TargetX", targetX);
            tag.putInt("TargetY", targetY);
            tag.putInt("TargetZ", targetZ);
        }
        ListTag toggles = new ListTag();
        for (Map.Entry<String, Boolean> entry : buttonToggles.entrySet()) {
            if (!entry.getValue()) continue;
            toggles.add(StringTag.valueOf(entry.getKey()));
        }
        tag.put("ToggledButtons", toggles);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        vehicleName = tag.getString("VehicleName");
        selectedWaypointId = tag.contains("SelectedWaypoint") ? tag.getString("SelectedWaypoint") : null;
        selectedWaystoneId = tag.contains("SelectedWaystone") ? tag.getString("SelectedWaystone") : null;
        hasManualTarget = tag.getBoolean("HasManualTarget");
        targetX = tag.getInt("TargetX");
        targetY = tag.getInt("TargetY");
        targetZ = tag.getInt("TargetZ");
        buttonToggles.clear();
        ListTag toggles = tag.getList("ToggledButtons", 8); // 8 = StringTag id
        for (int i = 0; i < toggles.size(); i++) {
            buttonToggles.put(toggles.getString(i), true);
        }
    }
}
