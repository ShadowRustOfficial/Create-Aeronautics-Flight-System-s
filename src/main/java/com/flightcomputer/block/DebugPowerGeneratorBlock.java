package com.flightcomputer.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Development-only infinite FE source. It only powers a Flight Controller when placed
 * directly behind its rear face, preventing it from becoming a general-purpose generator.
 */
public final class DebugPowerGeneratorBlock extends BaseEntityBlock {
    private static final MapCodec<DebugPowerGeneratorBlock> CODEC = simpleCodec(DebugPowerGeneratorBlock::new);

    public DebugPowerGeneratorBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DebugPowerGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (currentLevel, currentPos, currentState, blockEntity) -> {
            if (blockEntity instanceof DebugPowerGeneratorBlockEntity generator) {
                generator.serverTick();
            }
        };
    }
}
