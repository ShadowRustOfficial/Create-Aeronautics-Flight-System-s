package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.avionics.CombatMode;
import com.flightcomputer.avionics.DockingState;
import com.flightcomputer.avionics.FlightControlProfile;
import com.flightcomputer.avionics.FlightHold;
import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.avionics.FlightOperationsState;
import com.flightcomputer.avionics.LandingMode;
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

import java.util.UUID;

/** Server-authoritative network contract for the Phase 5.2 operations layer. */
public final class FlightOperationsNetwork {
    private static final String VERSION = "5.2";
    private static final CustomPacketPayload.Type<OperationsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "operations"));

    /** command: 0 identity, 1 combat, 2 landing, 3 docking, 4 hold, 5 profile, 6 map visibility, 7 emergency */
    public record OperationsPayload(BlockPos controllerPos, int command, int value, String text, UUID contact) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, OperationsPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            BlockPos.STREAM_CODEC.encode(buf, p.controllerPos());
            buf.writeVarInt(p.command());
            buf.writeVarInt(p.value());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.text() == null ? "" : p.text());
            buf.writeBoolean(p.contact() != null);
            if (p.contact() != null) { buf.writeLong(p.contact().getMostSignificantBits()); buf.writeLong(p.contact().getLeastSignificantBits()); }
        }, buf -> {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            int command = buf.readVarInt();
            int value = buf.readVarInt();
            String text = ByteBufCodecs.STRING_UTF8.decode(buf);
            UUID contact = buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
            return new OperationsPayload(pos, command, value, text, contact);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar(VERSION).playToServer(TYPE, OperationsPayload.STREAM_CODEC, FlightOperationsNetwork::handle);
        }
    }

    private static void handle(OperationsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !near(player, payload.controllerPos(), 64)) return;
            BlockEntity be = player.level().getBlockEntity(payload.controllerPos());
            if (!(be instanceof FlightControllerBlockEntity controller) || !(controller instanceof FlightOperationsHolder holder)) return;
            FlightOperationsState state = holder.getFlightOperations();
            String text = payload.text() == null ? "" : payload.text().trim();
            switch (payload.command()) {
                case 0 -> { // identity: value 0 name, 1 callsign
                    if (payload.value() == 0) state.setShipName(text); else state.setCallsign(text);
                }
                case 1 -> { // combat: value 0 mode, 1 home, 2 target, 3 engage, 4 abort
                    if (payload.value() == 0) state.setCombatMode(CombatMode.values()[safeOrdinal(payload.value(), CombatMode.values().length)]);
                    else if (payload.value() == 1) state.setDefensiveHome(text);
                    else if (payload.value() == 2) state.setOffensiveCallsign(text);
                    else if (payload.value() == 3) { state.setProfile(FlightControlProfile.COMBAT); state.setCombatAssist(true); }
                    else if (payload.value() == 4) { state.setCombatAssist(false); state.clearTrackedContact(); }
                }
                case 2 -> { // landing: value 0 mode, 1 engage, 2 abort
                    if (payload.value() == 0) state.setLandingMode(LandingMode.values()[safeOrdinal(payload.value(), LandingMode.values().length)]);
                    else if (payload.value() == 1) { state.setProfile(FlightControlProfile.LANDING); state.setLandingAssist(true); }
                    else if (payload.value() == 2) state.setLandingAssist(false);
                }
                case 3 -> { // docking: value 0 engage, 1 override, 2 clear
                    if (payload.value() == 0) { state.setProfile(FlightControlProfile.LANDING); state.setAutoDocking(true); state.setDockingState(DockingState.SCANNING); }
                    else if (payload.value() == 1) state.setDockingOverride(true);
                    else if (payload.value() == 2) { state.setDockingOverride(false); state.setAutoDocking(false); state.setDockingState(DockingState.IDLE); }
                }
                case 4 -> { // hold: value is FlightHold ordinal, text is ON/OFF
                    if (payload.value() >= 0 && payload.value() < FlightHold.values().length)
                        state.setHold(FlightHold.values()[payload.value()], "ON".equalsIgnoreCase(text));
                }
                case 5 -> { // profile
                    if (payload.value() >= 0 && payload.value() < FlightControlProfile.values().length)
                        state.setProfile(FlightControlProfile.values()[payload.value()]);
                }
                case 6 -> state.setMapContactVisible(payload.value() != 0);
                case 7 -> { state.setEmergencyReturn(payload.value() != 0); state.setProfile(FlightControlProfile.EMERGENCY); }
                default -> { return; }
            }
            controller.setChanged();
            player.level().sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
        });
    }

    private static int safeOrdinal(int value, int size) { return Math.max(0, Math.min(size - 1, value)); }
    private static boolean near(ServerPlayer player, BlockPos pos, double distance) {
        return player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= distance * distance;
    }

    public static void sendIdentity(BlockPos pos, boolean callsign, String text) { send(pos, 0, callsign ? 1 : 0, text, null); }
    public static void sendCombatMode(BlockPos pos, CombatMode mode) { send(pos, 1, mode.ordinal(), "", null); }
    public static void sendCombatHome(BlockPos pos, String text) { send(pos, 1, 1, text, null); }
    public static void sendCombatTarget(BlockPos pos, String text) { send(pos, 1, 2, text, null); }
    public static void engageCombat(BlockPos pos) { send(pos, 1, 3, "", null); }
    public static void abortCombat(BlockPos pos) { send(pos, 1, 4, "", null); }
    public static void sendLandingMode(BlockPos pos, LandingMode mode) { send(pos, 2, mode.ordinal(), "", null); }
    public static void engageLanding(BlockPos pos) { send(pos, 2, 1, "", null); }
    public static void abortLanding(BlockPos pos) { send(pos, 2, 2, "", null); }
    public static void engageDocking(BlockPos pos) { send(pos, 3, 0, "", null); }
    public static void overrideDocking(BlockPos pos) { send(pos, 3, 1, "", null); }
    public static void clearDocking(BlockPos pos) { send(pos, 3, 2, "", null); }
    public static void setHold(BlockPos pos, FlightHold hold, boolean enabled) { send(pos, 4, hold.ordinal(), enabled ? "ON" : "OFF", null); }
    public static void setProfile(BlockPos pos, FlightControlProfile profile) { send(pos, 5, profile.ordinal(), "", null); }
    public static void setMapVisibility(BlockPos pos, boolean visible) { send(pos, 6, visible ? 1 : 0, "", null); }
    public static void setEmergencyReturn(BlockPos pos, boolean enabled) { send(pos, 7, enabled ? 1 : 0, "", null); }
    private static void send(BlockPos pos, int command, int value, String text, UUID contact) {
        PacketDistributor.sendToServer(new OperationsPayload(pos, command, value, text, contact));
    }
    private FlightOperationsNetwork() { }
}
