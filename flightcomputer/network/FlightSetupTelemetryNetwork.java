package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.ThrusterLink;
import com.flightcomputer.control.ThrusterRegistry;
import com.flightcomputer.control.VehicleState;
import com.flightcomputer.control.VectorDirection;
import io.netty.buffer.ByteBuf;
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
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative setup telemetry for calculating a vessel's hover/stabilisation baseline. */
public final class FlightSetupTelemetryNetwork {
    private static final String VERSION = "5.2";
    public static final CustomPacketPayload.Type<SetupPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "setup_telemetry"));

    private FlightSetupTelemetryNetwork() { }

    public record SetupPayload(UUID controllerId, double mass, double envelopeDiameter, double envelopeHeight,
                               double weightForce, double verticalMaxThrust, double hoverFraction,
                               int recommendedRedstonePower, int upwardThrusterCount, double liftMargin,
                               double currentVerticalFraction, double recommendedOutputPerThruster) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, SetupPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.controllerId.getMostSignificantBits());
                    buf.writeLong(p.controllerId.getLeastSignificantBits());
                    buf.writeDouble(p.mass); buf.writeDouble(p.envelopeDiameter); buf.writeDouble(p.envelopeHeight);
                    buf.writeDouble(p.weightForce); buf.writeDouble(p.verticalMaxThrust); buf.writeDouble(p.hoverFraction);
                    buf.writeInt(p.recommendedRedstonePower); buf.writeInt(p.upwardThrusterCount);
                    buf.writeDouble(p.liftMargin); buf.writeDouble(p.currentVerticalFraction);
                    buf.writeDouble(p.recommendedOutputPerThruster);
                },
                buf -> new SetupPayload(
                        new UUID(buf.readLong(), buf.readLong()),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION).playToClient(TYPE, SetupPayload.STREAM_CODEC, FlightSetupTelemetryNetwork::handle);
        }
    }

    private static void handle(SetupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.flightcomputer.client.FlightSetupTelemetryClient.accept(payload));
    }

    public static void send(FlightControllerBlockEntity controller, VehicleState state, ThrusterRegistry registry) {
        if (controller == null || state == null || registry == null || !(controller.getLevel() instanceof ServerLevel level)) return;

        double mass = Math.max(0.001D, state.mass);
        // Create Aeronautics/Sable uses g = 11 m/s² in its kpg/pN physics scale.
        double weight = mass * 11.0D;
        double diameter = Math.max(2.0D, state.boundingRadius * 2.0D);
        double height = Math.max(2.0D, state.boundingHalfHeight * 2.0D);
        Quaterniond rotation = new Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll);

        Map<String, ThrusterLink> unique = new LinkedHashMap<>();
        for (ThrusterLink link : registry.getAllLinks(FlightMode.STABILIZE)) {
            if (link != null && link.source != null) unique.putIfAbsent(link.source.getId(), link);
        }

        double verticalMax = 0.0D;
        double currentVertical = 0.0D;
        int upward = 0;
        for (ThrusterLink link : unique.values()) {
            Vector3d force = new Vector3d(link.direction.x(), link.direction.y(), link.direction.z())
                    .mul(Math.max(0.0D, link.source.getMaxThrust()) * link.polarity);
            rotation.transform(force);
            if (force.y > 1.0e-6) {
                upward++;
                verticalMax += force.y;
                currentVertical += Math.max(0.0D, link.source.getCurrentThrust())
                        * Math.max(0.0D, force.y / Math.max(link.source.getMaxThrust(), 1.0e-6));
            }
        }

        double hoverFraction = verticalMax <= 1.0e-6 ? Double.POSITIVE_INFINITY : weight / verticalMax;
        double finiteFraction = Double.isFinite(hoverFraction) ? hoverFraction : 1.0D;
        int redstone = (int) Math.round(clamp(finiteFraction, 0.0D, 1.0D) * 15.0D);
        double margin = weight <= 1.0e-6 ? 0.0D : verticalMax / weight - 1.0D;
        double currentFraction = verticalMax <= 1.0e-6 ? 0.0D : clamp(currentVertical / verticalMax, 0.0D, 1.0D);
        // Equal allocation is the safe baseline when all lift thrusters have the same capacity.
        // The allocator subsequently scales each actuator against its actual max thrust.
        double requiredPerThruster = upward <= 0 ? Double.POSITIVE_INFINITY : weight / upward;

        SetupPayload payload = new SetupPayload(controller.getControllerId(), mass, diameter, height, weight,
                verticalMax, hoverFraction, redstone, upward, margin, currentFraction, requiredPerThruster);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(controller.getBlockPos().getX() + 0.5D,
                    controller.getBlockPos().getY() + 0.5D,
                    controller.getBlockPos().getZ() + 0.5D) <= 128.0D * 128.0D) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
