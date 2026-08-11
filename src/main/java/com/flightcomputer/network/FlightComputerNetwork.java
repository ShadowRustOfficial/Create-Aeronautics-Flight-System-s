package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.VectorDirection;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FlightComputerNetwork {
    private static final String VERSION = "3";

    public static final CustomPacketPayload.Type<ControllerActionPayload> CONTROLLER_ACTION_TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "controller_action"));
    public static final CustomPacketPayload.Type<LinkVectorPayload> LINK_VECTOR_TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "link_vector"));

    public record ControllerActionPayload(BlockPos pos, int actionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, ControllerActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ControllerActionPayload::pos,
                ByteBufCodecs.VAR_INT, ControllerActionPayload::actionId,
                ControllerActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return CONTROLLER_ACTION_TYPE; }
    }

    public record LinkVectorPayload(BlockPos controllerPos, BlockPos targetPos,
                                    int modeId, int directionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, LinkVectorPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LinkVectorPayload::controllerPos,
                BlockPos.STREAM_CODEC, LinkVectorPayload::targetPos,
                ByteBufCodecs.VAR_INT, LinkVectorPayload::modeId,
                ByteBufCodecs.VAR_INT, LinkVectorPayload::directionId,
                LinkVectorPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return LINK_VECTOR_TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION)
                    .playToServer(CONTROLLER_ACTION_TYPE, ControllerActionPayload.STREAM_CODEC,
                            FlightComputerNetwork::handleAction)
                    .playToServer(LINK_VECTOR_TYPE, LinkVectorPayload.STREAM_CODEC,
                            FlightComputerNetwork::handleVectorLink);
        }
    }

    private static void handleAction(ControllerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5,
                    payload.pos().getZ() + 0.5) > 64.0) return;
            BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
            if (!(blockEntity instanceof FlightControllerBlockEntity controller)) return;
            FlightControllerAction.fromNetworkId(payload.actionId()).ifPresent(controller::applyAction);
        });
    }

    private static void handleVectorLink(LinkVectorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(payload.controllerPos().getX() + 0.5, payload.controllerPos().getY() + 0.5,
                    payload.controllerPos().getZ() + 0.5) > 64.0) return;
            if (player.distanceToSqr(payload.targetPos().getX() + 0.5, payload.targetPos().getY() + 0.5,
                    payload.targetPos().getZ() + 0.5) > 1024.0) return;

            FlightMode[] modes = FlightMode.values();
            VectorDirection[] directions = VectorDirection.values();
            if (payload.modeId() < 0 || payload.modeId() >= modes.length
                    || payload.directionId() < 0 || payload.directionId() >= directions.length) return;

            BlockEntity blockEntity = player.level().getBlockEntity(payload.controllerPos());
            if (!(blockEntity instanceof FlightControllerBlockEntity controller)) return;
            if (player.level().getBlockState(payload.targetPos()).isAir()) return;

            controller.bindVector(modes[payload.modeId()], directions[payload.directionId()], payload.targetPos());
        });
    }

    public static void sendControllerAction(BlockPos pos, FlightControllerAction action) {
        PacketDistributor.sendToServer(new ControllerActionPayload(pos, action.networkId()));
    }

    public static void sendVectorLink(BlockPos controllerPos, BlockPos targetPos,
                                      FlightMode mode, VectorDirection direction) {
        PacketDistributor.sendToServer(new LinkVectorPayload(
                controllerPos, targetPos, mode.ordinal(), direction.ordinal()));
    }

    private FlightComputerNetwork() {}
}
