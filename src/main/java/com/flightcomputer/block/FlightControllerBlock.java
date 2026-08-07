package com.flightcomputer.block;

import com.flightcomputer.avionics.buttons.FlightControllerButtonLayout;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FlightControllerBlock extends BaseEntityBlock {
    private static final MapCodec<FlightControllerBlock> CODEC = simpleCodec(FlightControllerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FlightControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlightControllerBlockEntity(pos, state);
    }

    /**
     * Shift-right-click with an empty offhand operates a physical panel control.
     * Ordinary clicks are observed client-side and open the navigation console.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player.isShiftKeyDown() && player.getOffhandItem().isEmpty()
                && level.getBlockEntity(pos) instanceof FlightControllerBlockEntity controller) {
            Direction facing = state.getValue(FACING);
            if (hit.getDirection() != facing) return InteractionResult.PASS;

            double blockX = hit.getLocation().x - pos.getX();
            double blockZ = hit.getLocation().z - pos.getZ();
            double u = switch (facing) {
                case NORTH -> blockX;
                case SOUTH -> 1.0 - blockX;
                case EAST -> 1.0 - blockZ;
                case WEST -> blockZ;
                default -> blockX;
            };
            double v = hit.getLocation().y - pos.getY();
            FlightControllerButtonLayout.find(u, v).ifPresent(button -> controller.applyAction(button.action()));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (currentLevel, currentPos, currentState, blockEntity) -> {
            if (blockEntity instanceof FlightControllerBlockEntity controller) controller.serverTick();
        };
    }
}
