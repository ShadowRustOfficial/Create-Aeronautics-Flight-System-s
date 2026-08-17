package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.identity.FlightIdentityAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Dedicated identity channel; nameplate changes are executed through Sable's command path. */
public final class FlightIdentityNetwork {
    private static final String VERSION = "1";
    private static final CustomPacketPayload.Type<SetIdentityPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "set_flight_identity"));

    private FlightIdentityNetwork() { }

    public record SetIdentityPayload(BlockPos controllerPos, int field, String value) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, SetIdentityPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetIdentityPayload::controllerPos,
                ByteBufCodecs.VAR_INT, SetIdentityPayload::field,
                ByteBufCodecs.STRING_UTF8, SetIdentityPayload::value,
                SetIdentityPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToServer(TYPE, SetIdentityPayload.STREAM_CODEC, FlightIdentityNetwork::handle);
    }

    public static void setName(BlockPos controllerPos, String name) {
        send(controllerPos, 0, name == null ? "" : name);
    }

    public static void setId(BlockPos controllerPos, String id) {
        send(controllerPos, 1, id == null ? "" : id);
    }

    private static void send(BlockPos pos, int field, String value) {
        if (pos == null || field < 0 || field > 1) return;
        PacketDistributor.sendToServer(new SetIdentityPayload(pos, field, value));
    }

    private static void handle(SetIdentityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = payload.controllerPos();
            if (pos == null || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D * 64.0D) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof FlightControllerBlockEntity controller)) return;
            if (!(controller instanceof FlightIdentityAccess identity)) return;

            String value = payload.value() == null ? "" : payload.value().trim();
            if (payload.field() == 0) {
                if (value.isEmpty()) return;
                if (value.length() > 64) value = value.substring(0, 64);
                String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
                if (player.getServer() != null) {
                    player.getServer().getCommands().performPrefixedCommand(
                            player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                            "sable name set @v \"" + escaped + "\"");
                }
                identity.flightcomputer$setSubLevelName(value);
            } else {
                if (value.length() > 32) value = value.substring(0, 32);
                identity.flightcomputer$setFlightId(value);
            }
        });
    }
}
