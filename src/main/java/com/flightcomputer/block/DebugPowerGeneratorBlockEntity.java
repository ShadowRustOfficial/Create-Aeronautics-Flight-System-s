package com.flightcomputer.block;

import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Infinite development FE source for controller testing. */
public final class DebugPowerGeneratorBlockEntity extends BlockEntity {
    private static final int FE_PER_TICK = 1_000_000;

    public DebugPowerGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEBUG_POWER_GENERATOR.get(), pos, state);
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = worldPosition.relative(direction);
            if (!(level.getBlockEntity(neighbourPos) instanceof FlightControllerBlockEntity controller)) continue;

            // The generator must be physically attached to the controller's rear face.
            Direction controllerFacing = controller.getBlockState().getValue(FlightControllerBlock.FACING);
            if (!worldPosition.equals(controller.getBlockPos().relative(controllerFacing.getOpposite()))) continue;

            controller.getEnergyStorage().receiveEnergy(FE_PER_TICK, false);
            controller.setChanged();
            return;
        }
    }
}
