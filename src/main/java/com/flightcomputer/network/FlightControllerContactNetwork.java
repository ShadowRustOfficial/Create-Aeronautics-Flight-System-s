package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.identity.FlightIdentityAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Field;
import java.util.UUID;

/** Server-authoritative discovery feed for powered Flight Controllers shown by the Navigation Console map. */
public final class FlightControllerContactNetwork {
    private static final String VERSION = "5.2";
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final double DISCOVERY_RADIUS = 512.0D;
    private static final CustomPacketPayload.Type<ContactPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "flight_controller_contact"));

    private FlightControllerContactNetwork() { }

    public record ContactPayload(UUID controllerId, String flightId, String subLevelName,
                                 double x, double y, double z,
                                 boolean powered, boolean visible) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, ContactPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.controllerId().getMostSignificantBits());
                    buf.writeLong(p.controllerId().getLeastSignificantBits());
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.flightId() == null ? "" : p.flightId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.subLevelName() == null ? "" : p.subLevelName());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeBoolean(p.powered());
                    buf.writeBoolean(p.visible());
                },
                buf -> new ContactPayload(
                        new UUID(buf.readLong(), buf.readLong()),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readBoolean(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToClient(TYPE, ContactPayload.STREAM_CODEC, FlightControllerContactNetwork::handle);
    }

    private static void handle(ContactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.flightcomputer.client.map.FlightContactRegistry.acceptPacket(payload));
    }

    /** Called from the existing per-controller server tick. Positions are logical Sable world coordinates. */
    public static void sync(FlightControllerBlockEntity controller) {
        if (controller == null || !(controller.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % SYNC_INTERVAL_TICKS != 0L) return;

        boolean powered = controller.getEnergyStorage().getEnergyStored() > 0
                && controller.getPowerState() != com.flightcomputer.avionics.PowerState.NO_POWER;
        boolean visible = powered;
        String flightId = "";
        String subLevelName = "";
        if (controller instanceof FlightIdentityAccess identity) {
            flightId = identity.flightcomputer$getFlightId();
            subLevelName = identity.flightcomputer$getSubLevelName();
        }

        BlockPos pos = controller.getBlockPos();
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.5D, z = pos.getZ() + 0.5D;
        double[] logical = resolveLogicalPosition(controller);
        if (logical != null) { x = logical[0]; y = logical[1]; z = logical[2]; }

        ContactPayload payload = new ContactPayload(
                controller.getControllerId(), flightId, subLevelName,
                x, y, z, powered, visible);

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= DISCOVERY_RADIUS * DISCOVERY_RADIUS) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Reads the already-authoritative FlightControlRuntime snapshot without coupling the network layer to Sable. */
    private static double[] resolveLogicalPosition(FlightControllerBlockEntity controller) {
        try {
            Object runtime = FlightControlRuntimeManager.runtime(controller);
            Field snapshotField = runtime.getClass().getDeclaredField("snapshot");
            snapshotField.setAccessible(true);
            Object snapshot = snapshotField.get(runtime);
            if (snapshot == null) return null;
            Field fx = snapshot.getClass().getDeclaredField("x");
            Field fy = snapshot.getClass().getDeclaredField("y");
            Field fz = snapshot.getClass().getDeclaredField("z");
            fx.setAccessible(true); fy.setAccessible(true); fz.setAccessible(true);
            double x = fx.getDouble(snapshot), y = fy.getDouble(snapshot), z = fz.getDouble(snapshot);
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new double[]{x, y, z} : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
