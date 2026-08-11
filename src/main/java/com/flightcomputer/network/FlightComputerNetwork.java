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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public final class FlightComputerNetwork {
    private static final String VERSION = "4";

    public static final CustomPacketPayload.Type<ControllerActionPayload> CONTROLLER_ACTION_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "controller_action"));
    public static final CustomPacketPayload.Type<LinkVectorPayload> LINK_VECTOR_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "link_vector"));
    public static final CustomPacketPayload.Type<TelemetryPayload> TELEMETRY_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "telemetry"));

    public record ControllerActionPayload(BlockPos pos, int actionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, ControllerActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ControllerActionPayload::pos,
                ByteBufCodecs.VAR_INT, ControllerActionPayload::actionId,
                ControllerActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return CONTROLLER_ACTION_TYPE; }
    }

    public record LinkVectorPayload(BlockPos controllerPos, BlockPos targetPos, int modeId, int directionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, LinkVectorPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LinkVectorPayload::controllerPos,
                BlockPos.STREAM_CODEC, LinkVectorPayload::targetPos,
                ByteBufCodecs.VAR_INT, LinkVectorPayload::modeId,
                ByteBufCodecs.VAR_INT, LinkVectorPayload::directionId,
                LinkVectorPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return LINK_VECTOR_TYPE; }
    }

    /** Compact telemetry snapshot. It is manually encoded so the schema can grow without composite overload limits. */
    public record TelemetryPayload(UUID controllerId, double x, double y, double z, double speed,
                                   double heading, double pitch, double roll,
                                   boolean targetPresent, double targetX, double targetY, double targetZ,
                                   String targetName, double distance,
                                   double temperature, double maxTemperature, int thermalState, int cooldownTicks,
                                   int energy, int maxEnergy, int coolingTier,
                                   double stabiliserNorth, double stabiliserEast, double stabiliserSouth,
                                   double stabiliserWest, double stabiliserUp, double stabiliserDown,
                                   double autopilotNorth, double autopilotEast, double autopilotSouth,
                                   double autopilotWest, double autopilotUp, double autopilotDown) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, TelemetryPayload> STREAM_CODEC = StreamCodec.of(
                TelemetryPayload::encode, TelemetryPayload::decode);

        private void encode(ByteBuf buf) {
            writeUuid(buf, controllerId);
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeDouble(speed);
            buf.writeDouble(heading); buf.writeDouble(pitch); buf.writeDouble(roll);
            buf.writeBoolean(targetPresent); buf.writeDouble(targetX); buf.writeDouble(targetY); buf.writeDouble(targetZ);
            ByteBufCodecs.STRING_UTF8.encode(buf, targetName == null ? "" : targetName);
            buf.writeDouble(distance); buf.writeDouble(temperature); buf.writeDouble(maxTemperature);
            buf.writeVarInt(thermalState); buf.writeVarInt(cooldownTicks); buf.writeVarInt(energy); buf.writeVarInt(maxEnergy); buf.writeVarInt(coolingTier);
            double[] values = {stabiliserNorth, stabiliserEast, stabiliserSouth, stabiliserWest, stabiliserUp, stabiliserDown,
                    autopilotNorth, autopilotEast, autopilotSouth, autopilotWest, autopilotUp, autopilotDown};
            for (double value : values) buf.writeDouble(value);
        }

        private static TelemetryPayload decode(ByteBuf buf) {
            UUID id = readUuid(buf);
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble(), speed = buf.readDouble();
            double heading = buf.readDouble(), pitch = buf.readDouble(), roll = buf.readDouble();
            boolean target = buf.readBoolean();
            double tx = buf.readDouble(), ty = buf.readDouble(), tz = buf.readDouble();
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            double distance = buf.readDouble(), temperature = buf.readDouble(), maxTemperature = buf.readDouble();
            int thermal = buf.readVarInt(), cooldown = buf.readVarInt(), energy = buf.readVarInt(), maxEnergy = buf.readVarInt(), cooling = buf.readVarInt();
            double[] v = new double[12]; for (int i = 0; i < v.length; i++) v[i] = buf.readDouble();
            return new TelemetryPayload(id, x, y, z, speed, heading, pitch, roll, target, tx, ty, tz, name, distance,
                    temperature, maxTemperature, thermal, cooldown, energy, maxEnergy, cooling,
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11]);
        }

        private static void writeUuid(ByteBuf buf, UUID id) { buf.writeLong(id.getMostSignificantBits()); buf.writeLong(id.getLeastSignificantBits()); }
        private static UUID readUuid(ByteBuf buf) { return new UUID(buf.readLong(), buf.readLong()); }
        @Override public Type<? extends CustomPacketPayload> type() { return TELEMETRY_TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION)
                    .playToServer(CONTROLLER_ACTION_TYPE, ControllerActionPayload.STREAM_CODEC, FlightComputerNetwork::handleAction)
                    .playToServer(LINK_VECTOR_TYPE, LinkVectorPayload.STREAM_CODEC, FlightComputerNetwork::handleVectorLink)
                    .playToClient(TELEMETRY_TYPE, TelemetryPayload.STREAM_CODEC, FlightComputerNetwork::handleTelemetry);
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

    private static void handleVectorLink(LinkVectorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(payload.controllerPos().getX() + 0.5, payload.controllerPos().getY() + 0.5, payload.controllerPos().getZ() + 0.5) > 64.0) return;
            if (player.distanceToSqr(payload.targetPos().getX() + 0.5, payload.targetPos().getY() + 0.5, payload.targetPos().getZ() + 0.5) > 1024.0) return;
            FlightMode[] modes = FlightMode.values(); VectorDirection[] directions = VectorDirection.values();
            if (payload.modeId() < 0 || payload.modeId() >= modes.length || payload.directionId() < 0 || payload.directionId() >= directions.length) return;
            BlockEntity blockEntity = player.level().getBlockEntity(payload.controllerPos());
            if (!(blockEntity instanceof FlightControllerBlockEntity controller)) return;
            if (player.level().getBlockState(payload.targetPos()).isAir()) return;
            controller.bindVector(modes[payload.modeId()], directions[payload.directionId()], payload.targetPos());
        });
    }

    private static void handleTelemetry(TelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.flightcomputer.client.FlightComputerTelemetryClient.accept(payload)));
    }

    public static void sendControllerAction(BlockPos pos, FlightControllerAction action) {
        PacketDistributor.sendToServer(new ControllerActionPayload(pos, action.networkId()));
    }
    public static void sendVectorLink(BlockPos controllerPos, BlockPos targetPos, FlightMode mode, VectorDirection direction) {
        PacketDistributor.sendToServer(new LinkVectorPayload(controllerPos, targetPos, mode.ordinal(), direction.ordinal()));
    }
    public static void sendTelemetry(ServerPlayer player, TelemetryPayload payload) { PacketDistributor.sendToPlayer(player, payload); }
    private FlightComputerNetwork() { }
}
