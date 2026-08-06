package com.flightcomputer.block;

import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * GeckoLib (see FlightControllerModel) rather than hand-coded ModelPart rotation.
 * The two animation names referenced below ("animation.flight_controller.idle" /
 * "...active") must exist in the re-exported animation.json once the model is
 * converted to GeckoLib's format - see BUILD_NOTES.md.
 */
public class FlightControllerBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.flight_controller.idle");
    private static final RawAnimation ACTIVE = RawAnimation.begin().thenLoop("animation.flight_controller.active");

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private boolean active = false;

    public FlightControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLIGHT_CONTROLLER.get(), pos, state);
    }

    public void setActive(boolean active) {
        this.active = active;
        setChanged();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "state", 5, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<FlightControllerBlockEntity> state) {
        state.getController().setAnimation(active ? ACTIVE : IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }
}
