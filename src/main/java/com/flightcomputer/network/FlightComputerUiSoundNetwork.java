package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Server-authoritative UI audio. Sounds are emitted from the Flight Computer block so nearby players hear them. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FlightComputerUiSoundNetwork {
    private static final String VERSION = "1";
    private static final double MAX_DISTANCE = 64.0D;
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

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(TYPE, UiSoundPayload.STREAM_CODEC, FlightComputerUiSoundNetwork::handle);
    }

    /** Client-side entry point. The actual sound is never played locally. */
    public static void request(BlockPos controllerPos, int soundId) {
        if (controllerPos == null || soundId < 0 || soundId > 4) return;
        PacketDistributor.sendToServer(new UiSoundPayload(controllerPos, soundId));
    }

    private static void handle(UiSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!near(player, payload.controllerPos(), MAX_DISTANCE)) return;

            BlockEntity blockEntity = player.level().getBlockEntity(payload.controllerPos());
            if (!(blockEntity instanceof FlightControllerBlockEntity)) return;

            SoundEvent sound = soundFor(payload.soundId());
            if (sound == null) return;

            Vec3 soundPosition = resolvePhysicalPosition(player.level(), payload.controllerPos());
            player.level().playSound(
                    null,
                    soundPosition.x,
                    soundPosition.y,
                    soundPosition.z,
                    sound,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
        });
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

    private static boolean near(ServerPlayer player, BlockPos pos, double radius) {
        return pos != null && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radius * radius;
    }

    /**
     * Sable stores sub-level blocks in plot coordinates while rendering them at their dynamic world pose.
     * Resolve that pose before emitting the sound so the sound originates from the physical vessel.
     * If Sable is absent or reflection changes, fall back to the block position.
     */
    private static Vec3 resolvePhysicalPosition(Level level, BlockPos pos) {
        Vec3 fallback = Vec3.atCenterOf(pos);
        try {
            Class<?> companionType = Class.forName(
                    "dev.ryanhcode.sable.companion.SableCompanion",
                    false,
                    FlightComputerUiSoundNetwork.class.getClassLoader()
            );
            Field instanceField = companionType.getField("INSTANCE");
            Object companion = instanceField.get(null);
            if (companion == null) return fallback;

            Method project = companion.getClass().getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            Object result = project.invoke(companion, level, fallback);
            return result instanceof Vec3 vec ? vec : fallback;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return fallback;
        }
    }
}
