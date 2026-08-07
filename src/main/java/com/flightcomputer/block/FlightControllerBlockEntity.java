package com.flightcomputer.block;

import com.flightcomputer.FlightComputerConfig;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerActionResult;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.avionics.animation.FlightControllerAnimationBridge;
import com.flightcomputer.item.CoolingUpgradeItem;
import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

import java.util.UUID;

/** Server-authoritative state, FE storage, thermal state and upgrades for one controller block. */
public class FlightControllerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private final EnergyStorage energyStorage = new EnergyStorage(
            FlightComputerConfig.ENERGY_CAPACITY.get(),
            FlightComputerConfig.ENERGY_INPUT_PER_TICK.get(),
            0);
    private final ItemStackHandler upgradeHandler = new ItemStackHandler(3) {
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof CoolingUpgradeItem; }
    };

    /** Persistent identity for this physical controller. Never shared with another placement. */
    private UUID controllerId = UUID.randomUUID();

    private FlightControllerState controllerState = FlightControllerState.DEFAULT;
    private FlightControllerAction lastAction = FlightControllerAction.PULSE_DISPLAY;
    private PowerState powerState = PowerState.NORMAL;
    private PowerState lastPowerState = PowerState.NORMAL;
    private double temperature;
    private boolean thermalShutdown;
    private int syncCooldown;

    private Boolean renderedEngaged;
    private Boolean renderedStabiliser;
    private int modePulseId;
    private int displayPulseId;
    private int renderedModePulseId = -1;
    private int renderedDisplayPulseId = -1;

    public FlightControllerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.FLIGHT_CONTROLLER.get(), pos, state); }
    public UUID getControllerId() { return controllerId; }
    public FlightControllerState getControllerState() { return controllerState; }
    public boolean isEngaged() { return controllerState.engaged(); }
    public boolean isStabiliser() { return controllerState.stabiliser(); }
    public int getFlightMode() { return controllerState.flightMode().ordinal(); }
    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public ItemStackHandler getUpgradeHandler() { return upgradeHandler; }
    public PowerState getPowerState() { return powerState; }
    public double getTemperature() { return temperature; }
    public double getMaxTemperature() { return FlightComputerConfig.HEAT_CAPACITY.get(); }
    public boolean isThermalShutdown() { return thermalShutdown; }
    public boolean isFunctionalityReduced() { return powerState == PowerState.LOW || powerState == PowerState.CRITICAL; }

    public FlightControllerActionResult applyAction(FlightControllerAction action) {
        // The panel controls must remain individually interactive even while the power
        // system is empty. Power restrictions belong to actual flight/operating behavior;
        // they must not suppress the physical control animation/state layer.
        if (action == FlightControllerAction.TOGGLE_ENGAGED && thermalShutdown && !controllerState.engaged()) {
            return FlightControllerActionResult.accepted(controllerState, action, FlightControllerAnimationBridge.forAction(action, controllerState));
        }
        controllerState = controllerState.apply(action);
        lastAction = action;
        switch (action) {
            case CYCLE_MODE -> modePulseId++;
            case PULSE_DISPLAY -> displayPulseId++;
            default -> { }
        }
        markDirtyAndSync();
        return FlightControllerActionResult.accepted(controllerState, action, FlightControllerAnimationBridge.forAction(action, controllerState));
    }

    /** Server ticker: consumes FE, updates power state, temperature and thermal protection. */
    public void serverTick() {
        if (level == null || level.isClientSide) return;
        updatePowerState();
        CoolingUpgradeItem.Tier cooling = getCoolingTier();
        boolean advancedCooling = cooling == CoolingUpgradeItem.Tier.ADVANCED;
        boolean operating = controllerState.engaged() && !thermalShutdown;
        int cost = FlightComputerConfig.BASE_OPERATION_COST.get() + (advancedCooling ? FlightComputerConfig.ADVANCED_COOLING_EXTRA_COST.get() : 0);

        if (operating) {
            if (energyStorage.getEnergyStored() >= cost) {
                energyStorage.extractEnergy(cost, false);
                temperature += FlightComputerConfig.BASE_HEAT_PER_TICK.get();
                temperature = Math.max(0.0D, temperature - coolingRate(cooling));
            } else {
                controllerState = controllerState.apply(FlightControllerAction.TOGGLE_ENGAGED);
            }
        } else temperature = Math.max(0.0D, temperature - coolingRate(cooling));

        if (!advancedCooling && temperature >= FlightComputerConfig.HEAT_CAPACITY.get() * FlightComputerConfig.THERMAL_SHUTDOWN_THRESHOLD.get()) {
            thermalShutdown = true;
            if (controllerState.engaged()) controllerState = controllerState.apply(FlightControllerAction.TOGGLE_ENGAGED);
        }
        if (thermalShutdown && temperature <= FlightComputerConfig.HEAT_CAPACITY.get() * 0.25D) thermalShutdown = false;
        updatePowerState();
        setChanged();
        if (++syncCooldown >= 5) { syncCooldown = 0; level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    }

    private double coolingRate(CoolingUpgradeItem.Tier tier) {
        double base = FlightComputerConfig.COOLING_PER_TICK.get();
        return switch (tier) { case BASIC -> base; case IMPROVED -> base * 2.0D; case ADVANCED -> base * 4.0D; };
    }

    private CoolingUpgradeItem.Tier getCoolingTier() {
        CoolingUpgradeItem.Tier best = CoolingUpgradeItem.Tier.BASIC;
        for (int i = 0; i < upgradeHandler.getSlots(); i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (stack.getItem() instanceof CoolingUpgradeItem upgrade && upgrade.tier().ordinal() > best.ordinal()) best = upgrade.tier();
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
            lastPowerState = previous;
            onPowerStateChanged(previous, next);
        }
    }

    /** Transition hook: audio/UI can subscribe here later without running every tick. */
    protected void onPowerStateChanged(PowerState previous, PowerState current) {
        switch (current) {
            case MEDIUM -> { /* Medium warning sound + UI popup hook. */ }
            case LOW -> { /* Low warning sound + reduced-function UI hook. */ }
            case CRITICAL -> { /* Critical alarm + critical UI popup hook. */ }
            case NORMAL, NO_POWER -> { /* Recovery/shutdown hooks reserved. */ }
        }
    }

    private void markDirtyAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("ControllerId", controllerId);
        controllerState.save(tag);
        tag.putString("LastAction", lastAction.name());
        tag.putInt("ModePulseId", modePulseId);
        tag.putInt("DisplayPulseId", displayPulseId);
        tag.putString("PowerState", powerState.name());
        tag.putDouble("Temperature", temperature);
        tag.putBoolean("ThermalShutdown", thermalShutdown);
        tag.put("Energy", energyStorage.serializeNBT(registries));
        tag.put("Upgrades", upgradeHandler.serializeNBT(registries));
    }

    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean firstClientLoad = renderedModePulseId == -1 && renderedDisplayPulseId == -1;
        if (tag.hasUUID("ControllerId")) controllerId = tag.getUUID("ControllerId");
        else controllerId = UUID.randomUUID();
        controllerState = FlightControllerState.load(tag);
        try { lastAction = FlightControllerAction.valueOf(tag.getString("LastAction")); } catch (IllegalArgumentException ignored) { lastAction = FlightControllerAction.PULSE_DISPLAY; }
        try { powerState = PowerState.valueOf(tag.getString("PowerState")); } catch (IllegalArgumentException ignored) { powerState = PowerState.NORMAL; }
        lastPowerState = powerState;
        modePulseId = tag.getInt("ModePulseId");
        displayPulseId = tag.getInt("DisplayPulseId");
        temperature = tag.getDouble("Temperature");
        thermalShutdown = tag.getBoolean("ThermalShutdown");
        if (tag.contains("Energy")) energyStorage.deserializeNBT(registries, tag.get("Energy"));
        if (tag.contains("Upgrades")) upgradeHandler.deserializeNBT(registries, tag.getCompound("Upgrades"));
        if (firstClientLoad) { renderedModePulseId = modePulseId; renderedDisplayPulseId = displayPulseId; }
        renderedEngaged = null; renderedStabiliser = null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "engaged", 0, this::engagedPredicate));
        controllers.add(new AnimationController<>(this, "stabiliser", 0, this::stabiliserPredicate));
        controllers.add(new AnimationController<>(this, "mode", 0, this::modePredicate));
        controllers.add(new AnimationController<>(this, "display", 0, this::displayPredicate));
    }
    private PlayState engagedPredicate(AnimationState<FlightControllerBlockEntity> state) {
        boolean engaged = controllerState.engaged(); AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedEngaged == null || renderedEngaged != engaged) { renderedEngaged = engaged; controller.setAnimation(RawAnimation.begin().thenPlayAndHold(engaged ? FlightControllerAnimationBridge.ENGAGED_ON : FlightControllerAnimationBridge.ENGAGED_OFF)); }
        return PlayState.CONTINUE;
    }
    private PlayState stabiliserPredicate(AnimationState<FlightControllerBlockEntity> state) {
        boolean stabiliser = controllerState.stabiliser(); AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedStabiliser == null || renderedStabiliser != stabiliser) { renderedStabiliser = stabiliser; controller.setAnimation(RawAnimation.begin().thenPlayAndHold(stabiliser ? FlightControllerAnimationBridge.STABILISER_ON : FlightControllerAnimationBridge.STABILISER_OFF)); }
        return PlayState.CONTINUE;
    }
    private PlayState modePredicate(AnimationState<FlightControllerBlockEntity> state) {
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedModePulseId != modePulseId) { renderedModePulseId = modePulseId; controller.forceAnimationReset(); controller.setAnimation(RawAnimation.begin().thenPlay(FlightControllerAnimationBridge.MODE_PRESS)); return PlayState.CONTINUE; }
        return controller.hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }
    private PlayState displayPredicate(AnimationState<FlightControllerBlockEntity> state) {
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (renderedDisplayPulseId != displayPulseId) { renderedDisplayPulseId = displayPulseId; controller.forceAnimationReset(); controller.setAnimation(RawAnimation.begin().thenPlay(FlightControllerAnimationBridge.DISPLAY_PRESS)); return PlayState.CONTINUE; }
        return controller.hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animatableCache; }
}
