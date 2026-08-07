package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.block.FlightControllerBlockEntity;
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
    private static final String VERSION = "2";
    public static final CustomPacketPayload.Type<ControllerActionPayload> CONTROLLER_ACTION_TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "controller_action"));

    /** A request only; the server verifies distance, block type and action before applying it. */
    public record ControllerActionPayload(BlockPos pos, int actionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, ControllerActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ControllerActionPayload::pos,
                ByteBufCodecs.VAR_INT, ControllerActionPayload::actionId,
                ControllerActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return CONTROLLER_ACTION_TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION).playToServer(CONTROLLER_ACTION_TYPE, ControllerActionPayload.STREAM_CODEC, FlightComputerNetwork::handleAction);
        }
    }

    private static void handleAction(ControllerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;
            BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
            if (!(blockEntity instanceof FlightControllerBlockEntity controller)) return;
            FlightControllerAction.fromNetworkId(payload.actionId()).ifPresent(controller::applyAction);
        });
    }

    public static void sendControllerAction(BlockPos pos, FlightControllerAction action) {
        PacketDistributor.sendToServer(new ControllerActionPayload(pos, action.networkId()));
    }

    private FlightComputerNetwork() {}
}
