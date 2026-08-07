package com.flightcomputer.block;

import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Block entity for the Flight Controller. Geometry and animations are supplied by
 * GeckoLib (see FlightControllerModel).
 *
 * Persistence: every field a player can set (name, target, toggled buttons, power
 * state) is written in saveAdditional/loadAdditional below and now survives a chunk
 * unload/reload. Previously nothing here was persisted at all.
 *
 * Animation: the old single "active" boolean drove one looping clip and a second press
 * of the same logical button never restarted anything, because GeckoLib treats
 * setAnimation(sameAnimationInstance) as a no-op while it's already playing. Button
 * presses now go through a triggerable controller keyed per ControllerButtons entry
 * (triggerAnim always forces a (re)play), so each press animates every time. The
 * idle/active loop is kept as-is for overall power state.
 */
public class FlightControllerBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.flight_controller.idle");
    private static final RawAnimation ACTIVE = RawAnimation.begin().thenLoop("animation.flight_controller.active");

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private final FlightControllerState controllerState = new FlightControllerState();
    private boolean active = false;

    public FlightControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLIGHT_CONTROLLER.get(), pos, state);
    }

    public FlightControllerState getControllerState() {
        return controllerState;
    }

    /** Direction the vehicle's nose points; the button face is always the opposite side. */
    public Direction getFacing() {
        return getBlockState().getValue(FlightControllerBlock.FACING);
    }

    public void setActive(boolean active) {
        this.active = active;
        setChanged();
    }

    public boolean isActive() {
        return active;
    }

    /** Presses a registered button: flips its toggle (if any) and fires its one-shot clip. */
    public void pressButton(String buttonId) {
        FlightControllerButton button = ControllerButtons.get(buttonId);
        if (button == null) return;
        if (button.isToggle()) {
            controllerState.toggle(buttonId);
        }
        triggerAnim("buttons", "press_" + buttonId);
        setChanged();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "state", 5, this::statePredicate));

        AnimationController<FlightControllerBlockEntity> buttons =
                new AnimationController<>(this, "buttons", 0, state -> PlayState.STOP);
        for (FlightControllerButton button : ControllerButtons.all()) {
            buttons.triggerableAnim("press_" + button.id(), RawAnimation.begin().thenPlay(button.pressAnimation()));
        }
        controllers.add(buttons);
    }

    private PlayState statePredicate(AnimationState<FlightControllerBlockEntity> state) {
        state.getController().setAnimation(active ? ACTIVE : IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Active", active);
        controllerState.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("Active");
        controllerState.load(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
}
