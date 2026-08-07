package com.flightcomputer.block;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerActionResult;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.animation.FlightControllerAnimationBridge;
import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Server-authoritative Flight Controller state, persistence, synchronization and animation bridge. */
public class FlightControllerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private FlightControllerState controllerState = FlightControllerState.DEFAULT;
    private FlightControllerAction lastAction = FlightControllerAction.PULSE_DISPLAY;
    private int animationPulseTicks;

    private Boolean renderedEngaged;
    private Boolean renderedStabiliser;
    private int modePulseId;
    private int displayPulseId;
    private int renderedModePulseId = -1;
    private int renderedDisplayPulseId = -1;

    public FlightControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLIGHT_CONTROLLER.get(), pos, state);
    }

    public FlightControllerState getControllerState() { return controllerState; }
    public boolean isEngaged() { return controllerState.engaged(); }
    public boolean isStabiliser() { return controllerState.stabiliser(); }
    public int getFlightMode() { return controllerState.flightMode().ordinal(); }

    public FlightControllerActionResult applyAction(FlightControllerAction action) {
        controllerState = controllerState.apply(action);
        lastAction = action;

        animationPulseTicks = switch (action) {
            case CYCLE_MODE -> {
                modePulseId++;
                yield 10;
            }
            case PULSE_DISPLAY -> {
                displayPulseId++;
                yield 12;
            }
            default -> 0;
        };

        markDirtyAndSync();
        return FlightControllerActionResult.accepted(controllerState, action,
                FlightControllerAnimationBridge.forAction(action, controllerState));
    }

    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void serverTick() {
        if (animationPulseTicks > 0) animationPulseTicks--;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        controllerState.save(tag);
        tag.putString("LastAction", lastAction.name());
        tag.putInt("AnimationPulseTicks", animationPulseTicks);
        tag.putInt("ModePulseId", modePulseId);
        tag.putInt("DisplayPulseId", displayPulseId);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerState = FlightControllerState.load(tag);
        try { lastAction = FlightControllerAction.valueOf(tag.getString("LastAction")); }
        catch (IllegalArgumentException ignored) { lastAction = FlightControllerAction.PULSE_DISPLAY; }
        animationPulseTicks = tag.getInt("AnimationPulseTicks");
        modePulseId = tag.getInt("ModePulseId");
        displayPulseId = tag.getInt("DisplayPulseId");
        renderedEngaged = null;
        renderedStabiliser = null;
        renderedModePulseId = -1;
        renderedDisplayPulseId = -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
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
        if (lastAction == FlightControllerAction.CYCLE_MODE && renderedModePulseId != modePulseId) {
            renderedModePulseId = modePulseId;
            controller.forceAnimationReset();
            controller.setAnimation(RawAnimation.begin().thenPlay(FlightControllerAnimationBridge.MODE_PRESS));
            return PlayState.CONTINUE;
        }
        return controller.hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }

    private PlayState displayPredicate(AnimationState<FlightControllerBlockEntity> state) {
        AnimationController<FlightControllerBlockEntity> controller = state.getController();
        if (lastAction == FlightControllerAction.PULSE_DISPLAY && renderedDisplayPulseId != displayPulseId) {
            renderedDisplayPulseId = displayPulseId;
            controller.forceAnimationReset();
            controller.setAnimation(RawAnimation.begin().thenPlay(FlightControllerAnimationBridge.DISPLAY_PRESS));
            return PlayState.CONTINUE;
        }
        return controller.hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animatableCache; }
}
