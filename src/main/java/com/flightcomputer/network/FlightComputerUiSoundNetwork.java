package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.registry.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-authoritative Flight Computer UI audio.
 * Every UI sound uses the same world/block playback path as Emergency Shutdown.
 */
public final class FlightComputerUiSoundNetwork {
    private static final String VERSION = "1";
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "ui_block_sound");
    public static final CustomPacketPayload.Type<UiSoundPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    private FlightComputerUiSoundNetwork() { }

    public record UiSoundPayload(BlockPos controllerPos, int soundId) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, UiSoundPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, UiSoundPayload::controllerPos,
                ByteBufCodecs.VAR_INT, UiSoundPayload::soundId,
                UiSoundPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** RegisterPayloadHandlersEvent is a mod-bus event; FlightComputer registers this listener directly. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(TYPE, UiSoundPayload.STREAM_CODEC, FlightComputerUiSoundNetwork::handle);
    }

    public static void request(BlockPos controllerPos, int soundId) {
        if (controllerPos == null || soundId < 0 || soundId > 4) return;
        PacketDistributor.sendToServer(new UiSoundPayload(controllerPos, soundId));
    }

    private static void handle(UiSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!near(player, payload.controllerPos(), 64.0D)) return;

            SoundEvent sound = soundFor(payload.soundId());
            if (sound == null) return;

            // EXACTLY the same playback mechanism as FlightControllerBlockEntity's Emergency Shutdown.
            player.level().playSound(
                    null,
                    payload.controllerPos(),
                    sound,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
        });
    }

    private static boolean near(ServerPlayer player, BlockPos pos, double radius) {
        return player != null && pos != null
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radius * radius;
    }

    private static SoundEvent soundFor(int id) {
        return switch (id) {
            case 0 -> ModSounds.UI_TOGGLE_ON.get();
            case 1 -> ModSounds.UI_TOGGLE_OFF.get();
            case 2 -> ModSounds.UI_OPEN.get();
            case 3 -> ModSounds.UI_INTERACT.get();
            case 4 -> ModSounds.UI_DISCOVER.get();
            default -> null;
        };
    }
}
