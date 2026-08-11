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

    /** command: 0 identity, 1 combat, 2 landing, 3 docking, 4 hold, 5 profile, 6 map visibility, 7 emergency. */
    public record OperationsPayload(BlockPos controllerPos, int command, int value, String text, UUID contact) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, OperationsPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            BlockPos.STREAM_CODEC.encode(buf, p.controllerPos());
            ByteBufCodecs.VAR_INT.encode(buf, p.command());
            ByteBufCodecs.VAR_INT.encode(buf, p.value());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.text() == null ? "" : p.text());
            buf.writeBoolean(p.contact() != null);
            if (p.contact() != null) {
                buf.writeLong(p.contact().getMostSignificantBits());
                buf.writeLong(p.contact().getLeastSignificantBits());
            }
        }, buf -> {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            int command = ByteBufCodecs.VAR_INT.decode(buf);
            int value = ByteBufCodecs.VAR_INT.decode(buf);
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
            boolean changed = switch (payload.command()) {
                case 0 -> applyIdentity(state, payload.value(), text);
                case 1 -> applyCombat(state, payload.value(), text);
                case 2 -> applyLanding(state, payload.value());
                case 3 -> applyDocking(state, payload.value());
                case 4 -> applyHold(state, payload.value(), text);
                case 5 -> applyProfile(state, payload.value());
                case 6 -> { state.setMapContactVisible(payload.value() != 0); yield true; }
                case 7 -> {
                    boolean enabled = payload.value() != 0;
                    state.setEmergencyReturn(enabled);
                    if (enabled) state.setProfile(FlightControlProfile.EMERGENCY);
                    yield true;
                }
                default -> false;
            };

            if (changed) {
                controller.setChanged();
                player.level().sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
            }
        });
    }

    private static boolean applyIdentity(FlightOperationsState state, int action, String text) {
        if (action == 0) state.setShipName(text);
        else if (action == 1) state.setCallsign(text);
        else return false;
        return true;
    }

    private static boolean applyCombat(FlightOperationsState state, int action, String text) {
        if (action >= 0 && action < CombatMode.values().length) {
            state.setCombatMode(CombatMode.values()[action]);
            return true;
        }
        return switch (action) {
            case 10 -> { state.setDefensiveHome(text); yield true; }
            case 11 -> { state.setOffensiveCallsign(text); yield true; }
            case 12 -> { state.setProfile(FlightControlProfile.COMBAT); state.setCombatAssist(true); yield true; }
            case 13 -> { state.setCombatAssist(false); state.clearTrackedContact(); yield true; }
            default -> false;
        };
    }

    private static boolean applyLanding(FlightOperationsState state, int action) {
        if (action >= 0 && action < LandingMode.values().length) {
            state.setLandingMode(LandingMode.values()[action]);
            return true;
        }
        return switch (action) {
            case 10 -> { state.setProfile(FlightControlProfile.LANDING); state.setLandingAssist(true); yield true; }
            case 11 -> { state.setLandingAssist(false); yield true; }
            default -> false;
        };
    }

    private static boolean applyDocking(FlightOperationsState state, int action) {
        return switch (action) {
            case 0 -> { state.setProfile(FlightControlProfile.LANDING); state.setDockingOverride(false); state.setAutoDocking(true); state.setDockingState(DockingState.SCANNING); yield true; }
            case 1 -> { state.setDockingOverride(true); yield true; }
            case 2 -> { state.setDockingOverride(false); state.setAutoDocking(false); state.setDockingState(DockingState.IDLE); yield true; }
            default -> false;
        };
    }

    private static boolean applyHold(FlightOperationsState state, int action, String text) {
        if (action < 0 || action >= FlightHold.values().length) return false;
        state.setHold(FlightHold.values()[action], "ON".equalsIgnoreCase(text));
        return true;
    }

    private static boolean applyProfile(FlightOperationsState state, int action) {
        if (action < 0 || action >= FlightControlProfile.values().length) return false;
        state.setProfile(FlightControlProfile.values()[action]);
        return true;
    }

    private static boolean near(ServerPlayer player, BlockPos pos, double distance) {
        return player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= distance * distance;
    }

    public static void sendIdentity(BlockPos pos, boolean callsign, String text) { send(pos, 0, callsign ? 1 : 0, text, null); }
    public static void sendCombatMode(BlockPos pos, CombatMode mode) { if (mode != null) send(pos, 1, mode.ordinal(), "", null); }
    public static void sendCombatHome(BlockPos pos, String text) { send(pos, 1, 10, text, null); }
    public static void sendCombatTarget(BlockPos pos, String text) { send(pos, 1, 11, text, null); }
    public static void engageCombat(BlockPos pos) { send(pos, 1, 12, "", null); }
    public static void abortCombat(BlockPos pos) { send(pos, 1, 13, "", null); }
    public static void sendLandingMode(BlockPos pos, LandingMode mode) { if (mode != null) send(pos, 2, mode.ordinal(), "", null); }
    public static void engageLanding(BlockPos pos) { send(pos, 2, 10, "", null); }
    public static void abortLanding(BlockPos pos) { send(pos, 2, 11, "", null); }
    public static void engageDocking(BlockPos pos) { send(pos, 3, 0, "", null); }
    public static void overrideDocking(BlockPos pos) { send(pos, 3, 1, "", null); }
    public static void clearDocking(BlockPos pos) { send(pos, 3, 2, "", null); }
    public static void setHold(BlockPos pos, FlightHold hold, boolean enabled) { if (hold != null) send(pos, 4, hold.ordinal(), enabled ? "ON" : "OFF", null); }
    public static void setProfile(BlockPos pos, FlightControlProfile profile) { if (profile != null) send(pos, 5, profile.ordinal(), "", null); }
    public static void setMapVisibility(BlockPos pos, boolean visible) { send(pos, 6, visible ? 1 : 0, "", null); }
    public static void setEmergencyReturn(BlockPos pos, boolean enabled) { send(pos, 7, enabled ? 1 : 0, "", null); }

    private static void send(BlockPos pos, int command, int value, String text, UUID contact) {
        PacketDistributor.sendToServer(new OperationsPayload(pos, command, value, text == null ? "" : text, contact));
    }

    private FlightOperationsNetwork() { }
}
