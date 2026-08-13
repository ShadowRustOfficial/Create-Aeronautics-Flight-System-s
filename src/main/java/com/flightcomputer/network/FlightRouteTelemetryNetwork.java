package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Small authoritative state packet for live Route/Flight Control UI state. */
public final class FlightRouteTelemetryNetwork {
    private static final String VERSION = "5.2";
    public static final CustomPacketPayload.Type<RouteStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "route_state"));

    private FlightRouteTelemetryNetwork() { }

    public record RouteStatePayload(UUID controllerId, boolean engaged, boolean stabiliser, int mode,
                                    boolean altitudeHold, boolean headingHold, boolean positionHold,
                                    boolean velocityHold, boolean navigationEnabled, boolean routeActive,
                                    boolean targetPresent, String targetName, double targetX, double targetY, double targetZ) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, RouteStatePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.controllerId.getMostSignificantBits());
                    buf.writeLong(p.controllerId.getLeastSignificantBits());
                    buf.writeBoolean(p.engaged);
                    buf.writeBoolean(p.stabiliser);
                    buf.writeVarInt(p.mode);
                    buf.writeBoolean(p.altitudeHold);
                    buf.writeBoolean(p.headingHold);
                    buf.writeBoolean(p.positionHold);
                    buf.writeBoolean(p.velocityHold);
                    buf.writeBoolean(p.navigationEnabled);
                    buf.writeBoolean(p.routeActive);
                    buf.writeBoolean(p.targetPresent);
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.targetName == null ? "" : p.targetName);
                    buf.writeDouble(p.targetX);
                    buf.writeDouble(p.targetY);
                    buf.writeDouble(p.targetZ);
                },
                buf -> new RouteStatePayload(
                        new UUID(buf.readLong(), buf.readLong()),
                        buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION).playToClient(TYPE, RouteStatePayload.STREAM_CODEC, FlightRouteTelemetryNetwork::handle);
        }
    }

    private static void handle(RouteStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.flightcomputer.client.FlightRouteTelemetryClient.accept(payload));
    }

    public static void send(FlightControllerBlockEntity controller) {
        if (controller == null || !(controller.getLevel() instanceof ServerLevel level)) return;
        FlightControllerState state = controller.getControllerState();
        boolean targetPresent = FlightControlRuntimeManager.hasTarget(controller);
        var target = FlightControlRuntimeManager.target(controller);
        String targetName = targetPresent ? FlightControlRuntimeManager.targetName(controller) : "";
        RouteStatePayload payload = new RouteStatePayload(
                controller.getControllerId(), state.engaged(), state.stabiliser(), state.flightMode().ordinal(),
                state.altitudeHold(), state.headingHold(), state.positionHold(), state.velocityHold(),
                state.navigationEnabled(), state.routeActive(), targetPresent, targetName,
                target == null ? 0.0D : target.x, target == null ? 0.0D : target.y, target == null ? 0.0D : target.z);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(controller.getBlockPos().getX() + 0.5D,
                    controller.getBlockPos().getY() + 0.5D,
                    controller.getBlockPos().getZ() + 0.5D) <= 128.0D * 128.0D) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
