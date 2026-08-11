package com.flightcomputer.block;

import com.flightcomputer.FlightComputerConfig;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerActionResult;
import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.avionics.FlightOperationsState;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.avionics.ThermalState;
import com.flightcomputer.avionics.animation.FlightControllerAnimationBridge;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.VectorDirection;
import com.flightcomputer.item.CoolingUpgradeItem;
import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative controller state, FE storage, thermal protection and persistent vector links. */
public class FlightControllerBlockEntity extends BlockEntity implements GeoBlockEntity, FlightOperationsHolder {
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    private final EnergyStorage energyStorage = new EnergyStorage(
            FlightComputerConfig.ENERGY_CAPACITY.get(),
            FlightComputerConfig.ENERGY_INPUT_PER_TICK.get(),
            FlightComputerConfig.ENERGY_CAPACITY.get()) {
        @Override public boolean canReceive() { return !thermalShutdown && thermalCooldownTicks <= 0; }
        @Override public int receiveEnergy(int toReceive, boolean simulate) {
            return canReceive() ? super.receiveEnergy(toReceive, simulate) : 0;
        }
    };

    private final ItemStackHandler upgradeHandler = new ItemStackHandler(3) {
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !isThermalLockout() && stack.getItem() instanceof CoolingUpgradeItem;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isThermalLockout() ? stack : super.insertItem(slot, stack, simulate);
        }
    };

    private UUID controllerId = UUID.randomUUID();
    private FlightOperationsState flightOperations = new FlightOperationsState();
    private UUID linkedControllerId;
    private boolean terrainEnabled = true;
    private final EnumMap<FlightMode, EnumMap<VectorDirection, BlockPos>> vectorLinks =
            new EnumMap<>(FlightMode.class);

    private FlightControllerState controllerState = FlightControllerState.DEFAULT;
    private FlightControllerAction lastAction = FlightControllerAction.PULSE_DISPLAY;
    private PowerState powerState = PowerState.NORMAL;
    private ThermalState thermalState = ThermalState.NORMAL;
    private double temperature;
    private boolean thermalShutdown;
    private int thermalCooldownTicks;
    private int thermalHistoryTicker;
    private int thermalHistoryCursor;
    private final double[] thermalHistory = new double[180];
    private int syncCooldown;

    private Boolean renderedEngaged;
    private Boolean renderedStabiliser;
    private int modePulseId;
    private int displayPulseId;
    private int renderedModePulseId = -1;
    private int renderedDisplayPulseId = -1;

    public FlightControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLIGHT_CONTROLLER.get(), pos, state);
        for (FlightMode mode : FlightMode.values()) vectorLinks.put(mode, new EnumMap<>(VectorDirection.class));
    }

    public UUID getControllerId() { return controllerId; }
    @Override public FlightOperationsState getFlightOperations() { return flightOperations; }
    @Override public void setFlightOperations(FlightOperationsState state) { flightOperations = state == null ? new FlightOperationsState() : state; markDirtyAndSync(); }
    public UUID getLinkedControllerId() { return linkedControllerId; }
    public boolean isTerrainEnabled() { return terrainEnabled; }
    public FlightControllerState getControllerState() { return controllerState; }
    public boolean isEngaged() { return controllerState.engaged(); }
    public boolean isStabiliser() { return controllerState.stabiliser(); }
    public int getFlightMode() { return controllerState.flightMode().ordinal(); }
    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public ItemStackHandler getUpgradeHandler() { return upgradeHandler; }
    public PowerState getPowerState() { return powerState; }
    public ThermalState getThermalState() { return thermalState; }
    public double getTemperature() { return temperature; }
    public double getMaxTemperature() { return FlightComputerConfig.HEAT_CAPACITY.get(); }
    public boolean isThermalShutdown() { return thermalShutdown; }
    public boolean isThermalLockout() { return thermalShutdown || thermalCooldownTicks > 0; }
    public int getThermalCooldownTicksRemaining() { return thermalCooldownTicks; }
    public double getThermalCooldownSecondsRemaining() { return thermalCooldownTicks / 20.0D; }
    public CoolingUpgradeItem.Tier getCoolingTier() { return getCoolingTierInternal(); }
    public double[] getThermalHistory() { return Arrays.copyOf(thermalHistory, thermalHistory.length); }

    /** Adds propulsion/control heat after the normal per-tick thermal accounting. */
    public void addControlThermalLoad(double normalizedLoad) {
        if (level == null || level.isClientSide || isThermalLockout()) return;
        double load = Math.max(0.0D, Math.min(1.0D, normalizedLoad));
        temperature += FlightComputerConfig.BASE_HEAT_PER_TICK.get() * (1.0D + load * 8.0D);
        temperature = Math.min(FlightComputerConfig.HEAT_CAPACITY.get() * 1.05D, temperature);
        setChanged();
    }

    public void bindVector(FlightMode mode, VectorDirection direction, BlockPos target) {
        if (mode == null || direction == null || target == null) return;
        vectorLinks.get(mode).put(direction, target.immutable());
        markDirtyAndSync();
    }
    public void unbindVector(FlightMode mode, VectorDirection direction) {
        if (mode == null || direction == null) return;
        vectorLinks.get(mode).remove(direction);
        markDirtyAndSync();
    }
    public BlockPos getVectorLink(FlightMode mode, VectorDirection direction) {
        return vectorLinks.get(mode).get(direction);
    }
    public Map<VectorDirection, BlockPos> getVectorLinks(FlightMode mode) {
        return Map.copyOf(vectorLinks.get(mode));
    }

    public boolean isFunctionalityReduced() {
        return powerState == PowerState.LOW || powerState == PowerState.CRITICAL;
    }

    public boolean isOperationPermitted(FlightControllerAction action) {
        if (action == FlightControllerAction.EMERGENCY_SHUTDOWN) return true;
        return !isThermalLockout() && powerState != PowerState.NO_POWER && energyStorage.getEnergyStored() > 0;
    }

    public FlightControllerActionResult applyAction(FlightControllerAction action) {
        if (!isOperationPermitted(action)) {
            return FlightControllerActionResult.rejected(controllerState, action,
                    isThermalLockout() ? "THERMAL_SHUTDOWN" : "NO_POWER");
        }
        if (action == FlightControllerAction.TOGGLE_TERRAIN) terrainEnabled = !terrainEnabled;
        else controllerState = controllerState.apply(action);
        if (action == FlightControllerAction.EMERGENCY_SHUTDOWN) energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
        lastAction = action;
        switch (action) {
            case CYCLE_MODE -> modePulseId++;
            case PULSE_DISPLAY -> displayPulseId++;
            default -> { }
        }
        markDirtyAndSync();
        return FlightControllerActionResult.accepted(controllerState, action,
                FlightControllerAnimationBridge.forAction(action, controllerState));
    }

    /** Server ticker: consumes FE, updates thermal state, and enforces a hard 10-minute lockout. */
    public void serverTick() {
        if (level == null || level.isClientSide) return;
        if (thermalCooldownTicks > 0) thermalCooldownTicks--;

        updatePowerState();
        CoolingUpgradeItem.Tier cooling = getCoolingTierInternal();
        boolean advancedCooling = cooling == CoolingUpgradeItem.Tier.ADVANCED;
        boolean operating = controllerState.engaged() && !isThermalLockout();
        int cost = FlightComputerConfig.IDLE_OPERATION_COST.get()
                + (terrainEnabled ? FlightComputerConfig.TERRAIN_OPERATION_COST.get() : 0)
                + (operating ? FlightComputerConfig.BASE_OPERATION_COST.get() : 0)
                + (operating && advancedCooling ? FlightComputerConfig.ADVANCED_COOLING_EXTRA_COST.get() : 0);

        if (!isThermalLockout()) {
            if (energyStorage.getEnergyStored() >= cost) energyStorage.extractEnergy(cost, false);
            else {
                energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
                if (controllerState.engaged()) controllerState = controllerState.apply(FlightControllerAction.TOGGLE_ENGAGED);
            }
        }

        if (operating && energyStorage.getEnergyStored() > 0)
            temperature += FlightComputerConfig.BASE_HEAT_PER_TICK.get();
        temperature = Math.max(0.0D, temperature - coolingRate(cooling));

        if (++thermalHistoryTicker >= 20) {
            thermalHistoryTicker = 0;
            thermalHistory[thermalHistoryCursor] = FlightComputerConfig.HEAT_CAPACITY.get() <= 0
                    ? 0 : temperature / FlightComputerConfig.HEAT_CAPACITY.get();
            thermalHistoryCursor = (thermalHistoryCursor + 1) % thermalHistory.length;
        }

        updatePowerState();
        updateThermalState();
        if (!thermalShutdown && temperature >= FlightComputerConfig.HEAT_CAPACITY.get()
                * FlightComputerConfig.THERMAL_SHUTDOWN_THRESHOLD.get()) triggerThermalShutdown();

        if (thermalShutdown && thermalCooldownTicks <= 0
                && temperature <= FlightComputerConfig.HEAT_CAPACITY.get()
                * FlightComputerConfig.THERMAL_RECOVERY_THRESHOLD.get()) {
            thermalShutdown = false;
            updateThermalState();
            markDirtyAndSync();
        }

        setChanged();
        if (++syncCooldown >= 5) {
            syncCooldown = 0;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void triggerThermalShutdown() {
        thermalShutdown = true;
        thermalCooldownTicks = FlightComputerConfig.THERMAL_COOLDOWN_TICKS.get();
        energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
        if (controllerState.engaged()) controllerState = controllerState.apply(FlightControllerAction.TOGGLE_ENGAGED);
        updatePowerState();
        updateThermalState();
        onThermalShutdown();
        markDirtyAndSync();
    }

    private double coolingRate(CoolingUpgradeItem.Tier tier) {
        double base = FlightComputerConfig.COOLING_PER_TICK.get();
        return switch (tier) {
            case NONE -> base;
            case BASIC -> base * FlightComputerConfig.BASIC_COOLING_MODIFIER.get();
            case IMPROVED -> base * FlightComputerConfig.IMPROVED_COOLING_MODIFIER.get();
            case ADVANCED -> base * FlightComputerConfig.ADVANCED_COOLING_MODIFIER.get();
        };
    }

    private CoolingUpgradeItem.Tier getCoolingTierInternal() {
        CoolingUpgradeItem.Tier best = CoolingUpgradeItem.Tier.NONE;
        for (int i = 0; i < upgradeHandler.getSlots(); i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (stack.getItem() instanceof CoolingUpgradeItem upgrade
                    && upgrade.tier().ordinal() > best.ordinal()) best = upgrade.tier();
        }
        return best;
    }

    private void updatePowerState() {
        long energy = energyStorage.getEnergyStored();
        long capacity = energyStorage.getMaxEnergyStored();
        int percent = capacity <= 0 ? 0 : (int) ((energy * 100L) / capacity);
        PowerState next = energy <= 0 ? PowerState.NO_POWER
                : percent <= FlightComputerConfig.CRITICAL_THRESHOLD.get() ? PowerState.CRITICAL
                : percent <= FlightComputerConfig.LOW_THRESHOLD.get() ? PowerState.LOW
                : percent <= FlightComputerConfig.MEDIUM_THRESHOLD.get() ? PowerState.MEDIUM
                : PowerState.NORMAL;
        if (next != powerState) {
            PowerState previous = powerState;
            powerState = next;
            onPowerStateChanged(previous, next);
            markDirtyAndSync();
        }
    }

    protected void onPowerStateChanged(PowerState previous, PowerState current) {
        switch (current) {
            case MEDIUM -> onMediumPowerWarning();
            case LOW -> onLowPowerWarning();
            case CRITICAL -> onCriticalPowerAlarm();
            case NO_POWER -> onNoPower();
            case NORMAL -> { }
        }
        if (previous != PowerState.NORMAL && current == PowerState.NORMAL) onPowerRecovered();
    }
    protected void onMediumPowerWarning() { }
    protected void onLowPowerWarning() { }
    protected void onCriticalPowerAlarm() { }
    protected void onNoPower() { }
    protected void onPowerRecovered() { }

    private void updateThermalState() {
        double capacity = FlightComputerConfig.HEAT_CAPACITY.get();
        double fraction = capacity <= 0 ? 0 : temperature / capacity;
        ThermalState next = thermalShutdown || thermalCooldownTicks > 0
                ? ThermalState.THERMAL_SHUTDOWN
                : fraction >= FlightComputerConfig.THERMAL_SHUTDOWN_THRESHOLD.get() ? ThermalState.THERMAL_SHUTDOWN
                : fraction >= FlightComputerConfig.THERMAL_CRITICAL_THRESHOLD.get() ? ThermalState.CRITICAL
                : fraction >= FlightComputerConfig.THERMAL_HOT_THRESHOLD.get() ? ThermalState.HOT
                : fraction >= FlightComputerConfig.THERMAL_WARM_THRESHOLD.get() ? ThermalState.WARM
                : ThermalState.NORMAL;
        if (next != thermalState) {
            ThermalState previous = thermalState;
            thermalState = next;
            onThermalStateChanged(previous, next);
            markDirtyAndSync();
        }
    }

    protected void onThermalStateChanged(ThermalState previous, ThermalState current) {
        switch (current) {
            case HOT -> onOverheatWarning();
            case CRITICAL -> onThermalCritical();
            case THERMAL_SHUTDOWN -> onThermalShutdown();
            case NORMAL, WARM -> { }
        }
        if (previous == ThermalState.THERMAL_SHUTDOWN && current != ThermalState.THERMAL_SHUTDOWN) onThermalRecovery();
    }
    protected void onOverheatWarning() { }
    protected void onThermalCritical() { }
    protected void onThermalShutdown() { }
    protected void onThermalRecovery() { }

    public boolean linkTo(UUID targetControllerId) {
        if (targetControllerId == null || controllerId.equals(targetControllerId)) return false;
        linkedControllerId = targetControllerId;
        markDirtyAndSync();
        return true;
    }
    public void unlink() {
        if (linkedControllerId != null) {
            linkedControllerId = null;
            markDirtyAndSync();
        }
    }

    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("ControllerId", controllerId);
        flightOperations.save(tag);
        if (linkedControllerId != null) tag.putUUID("LinkedControllerId", linkedControllerId);
        tag.putBoolean("TerrainEnabled", terrainEnabled);
        controllerState.save(tag);
        tag.putString("LastAction", lastAction.name());
        tag.putInt("ModePulseId", modePulseId);
        tag.putInt("DisplayPulseId", displayPulseId);
        tag.putString("PowerState", powerState.name());
        tag.putString("ThermalState", thermalState.name());
        tag.putDouble("Temperature", temperature);
        tag.putBoolean("ThermalShutdown", thermalShutdown);
        tag.putInt("ThermalCooldownTicks", thermalCooldownTicks);
        ListTag history = new ListTag();
        for (double value : thermalHistory) history.add(DoubleTag.valueOf(value));
        tag.put("ThermalHistory", history);
        tag.putInt("ThermalHistoryCursor", thermalHistoryCursor);

        ListTag vectorTag = new ListTag();
        for (FlightMode mode : FlightMode.values()) {
            for (Map.Entry<VectorDirection, BlockPos> entry : vectorLinks.get(mode).entrySet()) {
                CompoundTag link = new CompoundTag();
                link.putString("Mode", mode.name());
                link.putString("Direction", entry.getKey().name());
                link.putLong("Pos", entry.getValue().asLong());
                vectorTag.add(link);
            }
        }
        tag.put("VectorLinks", vectorTag);
        tag.put("Energy", energyStorage.serializeNBT(registries));
        tag.put("Upgrades", upgradeHandler.serializeNBT(registries));
    }

    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean firstClientLoad = renderedModePulseId == -1 && renderedDisplayPulseId == -1;
        controllerId = tag.hasUUID("ControllerId") ? tag.getUUID("ControllerId") : UUID.randomUUID();
        flightOperations = FlightOperationsState.load(tag);
        linkedControllerId = tag.hasUUID("LinkedControllerId") ? tag.getUUID("LinkedControllerId") : null;
        terrainEnabled = !tag.contains("TerrainEnabled") || tag.getBoolean("TerrainEnabled");
        controllerState = FlightControllerState.load(tag);
        try { lastAction = FlightControllerAction.valueOf(tag.getString("LastAction")); }
        catch (IllegalArgumentException ignored) { lastAction = FlightControllerAction.PULSE_DISPLAY; }
        try { powerState = PowerState.valueOf(tag.getString("PowerState")); }
        catch (IllegalArgumentException ignored) { powerState = PowerState.NORMAL; }
        try { thermalState = ThermalState.valueOf(tag.getString("ThermalState")); }
        catch (IllegalArgumentException ignored) { thermalState = ThermalState.NORMAL; }

        modePulseId = tag.getInt("ModePulseId");
        displayPulseId = tag.getInt("DisplayPulseId");
        temperature = tag.getDouble("Temperature");
        thermalShutdown = tag.getBoolean("ThermalShutdown");
        thermalCooldownTicks = Math.max(0, tag.getInt("ThermalCooldownTicks"));

        Arrays.fill(thermalHistory, 0);
        if (tag.contains("ThermalHistory", Tag.TAG_LIST)) {
            ListTag history = tag.getList("ThermalHistory", Tag.TAG_DOUBLE);
            int count = Math.min(history.size(), thermalHistory.length);
            for (int i = 0; i < count; i++) thermalHistory[i] = history.getDouble(i);
        }
        thermalHistoryCursor = Math.floorMod(tag.getInt("ThermalHistoryCursor"), thermalHistory.length);

        for (EnumMap<VectorDirection, BlockPos> map : vectorLinks.values()) map.clear();
        if (tag.contains("VectorLinks", Tag.TAG_LIST)) {
            ListTag vectorTag = tag.getList("VectorLinks", Tag.TAG_COMPOUND);
            for (int i = 0; i < vectorTag.size(); i++) {
                CompoundTag link = vectorTag.getCompound(i);
                try {
                    FlightMode mode = FlightMode.valueOf(link.getString("Mode"));
                    VectorDirection direction = VectorDirection.valueOf(link.getString("Direction"));
                    vectorLinks.get(mode).put(direction, BlockPos.of(link.getLong("Pos")));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        if (tag.contains("Energy")) energyStorage.deserializeNBT(registries, tag.get("Energy"));
        if (tag.contains("Upgrades")) upgradeHandler.deserializeNBT(registries, tag.getCompound("Upgrades"));
        if (firstClientLoad) {
            renderedModePulseId = modePulseId;
            renderedDisplayPulseId = displayPulseId;
        }
        renderedEngaged = null;
        renderedStabiliser = null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "engaged", 0, this::engagedPredicate));
        controllers.add(new AnimationController<>(this, "stabiliser", 0, this::stabiliserPredicate));
        controllers.add(new AnimationController<>(this, "mode", 0, this::modePredicate));
        controllers.add(new AnimationController<>(this, "display", 0, this::displayPredicate));
    }
    private PlayState engagedPredicate(AnimationState<FlightControllerBlockEntity> state) {
        boolean engaged = controllerState.engaged();
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedEngaged == null || renderedEngaged != engaged) {
            renderedEngaged = engaged;
            controller.setAnimation(RawAnimation.begin().thenPlayAndHold(
                    engaged ? FlightControllerAnimationBridge.ENGAGED_ON : FlightControllerAnimationBridge.ENGAGED_OFF));
        }
        return PlayState.CONTINUE;
    }
    private PlayState stabiliserPredicate(AnimationState<FlightControllerBlockEntity> state) {
        boolean stabiliser = controllerState.stabiliser();
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedStabiliser == null || renderedStabiliser != stabiliser) {
            renderedStabiliser = stabiliser;
            controller.setAnimation(RawAnimation.begin().thenPlayAndHold(
                    stabiliser ? FlightControllerAnimationBridge.STABILISER_ON : FlightControllerAnimationBridge.STABILISER_OFF));
        }
        return PlayState.CONTINUE;
    }
    private PlayState modePredicate(AnimationState<FlightControllerBlockEntity> state) {
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedModePulseId != modePulseId) {
            renderedModePulseId = modePulseId;
            controller.forceAnimationReset();
            controller.setAnimation(RawAnimation.begin().thenPlay(FlightControllerAnimationBridge.MODE_PRESS));
            return PlayState.CONTINUE;
        }
        return controller.hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }
    private PlayState displayPredicate(AnimationState<FlightControllerBlockEntity> state) {
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedDisplayPulseId != displayPulseId) {
            renderedDisplayPulseId = displayPulseId;
            controller.forceAnimationReset();
            controller.setAnimation(RawAnimation.begin().thenPlay(FlightControllerAnimationBridge.DISPLAY_PRESS));
            return PlayState.CONTINUE;
        }
        return controller.hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animatableCache; }
}
