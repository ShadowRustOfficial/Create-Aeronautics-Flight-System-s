package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public final class FlightComputerNetwork {
    private static final String VERSION = "5";
    public static final CustomPacketPayload.Type<ControllerActionPayload> CONTROLLER_ACTION_TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "controller_action"));
    public static final CustomPacketPayload.Type<LinkVectorPayload> LINK_VECTOR_TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "link_vector"));
    public static final CustomPacketPayload.Type<TelemetryPayload> TELEMETRY_TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "telemetry"));
    public static final CustomPacketPayload.Type<SetTargetPayload> SET_TARGET_TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "set_target"));
    public static final CustomPacketPayload.Type<ClearTargetPayload> CLEAR_TARGET_TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "clear_target"));

    public record ControllerActionPayload(BlockPos pos, int actionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, ControllerActionPayload> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, ControllerActionPayload::pos, ByteBufCodecs.VAR_INT, ControllerActionPayload::actionId, ControllerActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return CONTROLLER_ACTION_TYPE; }
    }
    public record LinkVectorPayload(BlockPos controllerPos, BlockPos targetPos, int modeId, int directionId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, LinkVectorPayload> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, LinkVectorPayload::controllerPos, BlockPos.STREAM_CODEC, LinkVectorPayload::targetPos, ByteBufCodecs.VAR_INT, LinkVectorPayload::modeId, ByteBufCodecs.VAR_INT, LinkVectorPayload::directionId, LinkVectorPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return LINK_VECTOR_TYPE; }
    }
    public record SetTargetPayload(BlockPos controllerPos, double x, double y, double z, String name) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, SetTargetPayload> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, SetTargetPayload::controllerPos, ByteBufCodecs.DOUBLE, SetTargetPayload::x, ByteBufCodecs.DOUBLE, SetTargetPayload::y, ByteBufCodecs.DOUBLE, SetTargetPayload::z, ByteBufCodecs.STRING_UTF8, SetTargetPayload::name, SetTargetPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return SET_TARGET_TYPE; }
    }
    public record ClearTargetPayload(BlockPos controllerPos) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, ClearTargetPayload> STREAM_CODEC = BlockPos.STREAM_CODEC.map(ClearTargetPayload::new, ClearTargetPayload::controllerPos);
        @Override public Type<? extends CustomPacketPayload> type() { return CLEAR_TARGET_TYPE; }
    }

    public record TelemetryPayload(UUID controllerId, double x, double y, double z, double speed, double heading, double pitch, double roll,
                                   boolean targetPresent, double targetX, double targetY, double targetZ, String targetName, double distance,
                                   double temperature, double maxTemperature, int thermalState, int cooldownTicks, int energy, int maxEnergy, int coolingTier,
                                   double stabiliserNorth, double stabiliserEast, double stabiliserSouth, double stabiliserWest, double stabiliserUp, double stabiliserDown,
                                   double autopilotNorth, double autopilotEast, double autopilotSouth, double autopilotWest, double autopilotUp, double autopilotDown) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, TelemetryPayload> STREAM_CODEC = StreamCodec.of(TelemetryPayload::encode, TelemetryPayload::decode);
        private void encode(ByteBuf buf) {
            buf.writeLong(controllerId.getMostSignificantBits()); buf.writeLong(controllerId.getLeastSignificantBits());
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeDouble(speed); buf.writeDouble(heading); buf.writeDouble(pitch); buf.writeDouble(roll);
            buf.writeBoolean(targetPresent); buf.writeDouble(targetX); buf.writeDouble(targetY); buf.writeDouble(targetZ); ByteBufCodecs.STRING_UTF8.encode(buf, targetName == null ? "" : targetName);
            buf.writeDouble(distance); buf.writeDouble(temperature); buf.writeDouble(maxTemperature); buf.writeVarInt(thermalState); buf.writeVarInt(cooldownTicks); buf.writeVarInt(energy); buf.writeVarInt(maxEnergy); buf.writeVarInt(coolingTier);
            double[] values = {stabiliserNorth,stabiliserEast,stabiliserSouth,stabiliserWest,stabiliserUp,stabiliserDown,autopilotNorth,autopilotEast,autopilotSouth,autopilotWest,autopilotUp,autopilotDown};
            for (double value : values) buf.writeDouble(value);
        }
        private static TelemetryPayload decode(ByteBuf buf) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            double x=buf.readDouble(), y=buf.readDouble(), z=buf.readDouble(), speed=buf.readDouble(), heading=buf.readDouble(), pitch=buf.readDouble(), roll=buf.readDouble();
            boolean target=buf.readBoolean(); double tx=buf.readDouble(), ty=buf.readDouble(), tz=buf.readDouble(); String name=ByteBufCodecs.STRING_UTF8.decode(buf);
            double distance=buf.readDouble(), temperature=buf.readDouble(), maxTemperature=buf.readDouble(); int thermal=buf.readVarInt(), cooldown=buf.readVarInt(), energy=buf.readVarInt(), maxEnergy=buf.readVarInt(), cooling=buf.readVarInt();
            double[] v=new double[12]; for(int i=0;i<v.length;i++) v[i]=buf.readDouble();
            return new TelemetryPayload(id,x,y,z,speed,heading,pitch,roll,target,tx,ty,tz,name,distance,temperature,maxTemperature,thermal,cooldown,energy,maxEnergy,cooling,v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11]);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TELEMETRY_TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION)
                    .playToServer(CONTROLLER_ACTION_TYPE, ControllerActionPayload.STREAM_CODEC, FlightComputerNetwork::handleAction)
                    .playToServer(LINK_VECTOR_TYPE, LinkVectorPayload.STREAM_CODEC, FlightComputerNetwork::handleVectorLink)
                    .playToServer(SET_TARGET_TYPE, SetTargetPayload.STREAM_CODEC, FlightComputerNetwork::handleSetTarget)
                    .playToServer(CLEAR_TARGET_TYPE, ClearTargetPayload.STREAM_CODEC, FlightComputerNetwork::handleClearTarget)
                    .playToClient(TELEMETRY_TYPE, TelemetryPayload.STREAM_CODEC, FlightComputerNetwork::handleTelemetry);
        }
    }

    private static boolean near(ServerPlayer player, BlockPos pos, double distanceSquared) { return player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5) <= distanceSquared; }
    private static void handleAction(ControllerActionPayload payload, IPayloadContext context) { context.enqueueWork(() -> { if (!(context.player() instanceof ServerPlayer player) || !near(player,payload.pos(),64.0)) return; BlockEntity be=player.level().getBlockEntity(payload.pos()); if (be instanceof FlightControllerBlockEntity c) FlightControllerAction.fromNetworkId(payload.actionId()).ifPresent(c::applyAction); }); }
    private static void handleVectorLink(LinkVectorPayload payload, IPayloadContext context) { context.enqueueWork(() -> { if (!(context.player() instanceof ServerPlayer player) || !near(player,payload.controllerPos(),64.0) || !near(player,payload.targetPos(),1024.0)) return; FlightMode[] modes=FlightMode.values(); VectorDirection[] dirs=VectorDirection.values(); if(payload.modeId()<0||payload.modeId()>=modes.length||payload.directionId()<0||payload.directionId()>=dirs.length)return; BlockEntity be=player.level().getBlockEntity(payload.controllerPos()); if(!(be instanceof FlightControllerBlockEntity c)||player.level().getBlockState(payload.targetPos()).isAir())return; c.bindVector(modes[payload.modeId()],dirs[payload.directionId()],payload.targetPos()); }); }
    private static void handleSetTarget(SetTargetPayload payload, IPayloadContext context) { context.enqueueWork(() -> { if (!(context.player() instanceof ServerPlayer player) || !near(player,payload.controllerPos(),64.0)) return; BlockEntity be=player.level().getBlockEntity(payload.controllerPos()); if(!(be instanceof FlightControllerBlockEntity c))return; if(Double.isNaN(payload.x())||Double.isNaN(payload.y())||Double.isNaN(payload.z()))return; FlightControlRuntimeManager.setTarget(c,new Vec3(payload.x(),payload.y(),payload.z()),payload.name()); }); }
    private static void handleClearTarget(ClearTargetPayload payload, IPayloadContext context) { context.enqueueWork(() -> { if (!(context.player() instanceof ServerPlayer player) || !near(player,payload.controllerPos(),64.0))return; BlockEntity be=player.level().getBlockEntity(payload.controllerPos()); if(be instanceof FlightControllerBlockEntity c) FlightControlRuntimeManager.clearTarget(c); }); }
    private static void handleTelemetry(TelemetryPayload payload, IPayloadContext context) { context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.flightcomputer.client.FlightComputerTelemetryClient.accept(payload))); }

    public static void sendControllerAction(BlockPos pos, FlightControllerAction action) { PacketDistributor.sendToServer(new ControllerActionPayload(pos,action.networkId())); }
    public static void sendVectorLink(BlockPos controllerPos, BlockPos targetPos, FlightMode mode, VectorDirection direction) { PacketDistributor.sendToServer(new LinkVectorPayload(controllerPos,targetPos,mode.ordinal(),direction.ordinal())); }
    public static void sendTarget(BlockPos controllerPos, double x, double y, double z, String name) { PacketDistributor.sendToServer(new SetTargetPayload(controllerPos,x,y,z,name)); }
    public static void clearTarget(BlockPos controllerPos) { PacketDistributor.sendToServer(new ClearTargetPayload(controllerPos)); }
    public static void sendTelemetry(ServerPlayer player, TelemetryPayload payload) { PacketDistributor.sendToPlayer(player,payload); }
    private FlightComputerNetwork() { }
}
