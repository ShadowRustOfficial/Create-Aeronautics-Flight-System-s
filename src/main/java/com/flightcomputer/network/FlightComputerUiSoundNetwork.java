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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

/** Server-authoritative UI audio. Sounds are emitted using the same block sound path as Emergency Shutdown. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
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

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(TYPE, UiSoundPayload.STREAM_CODEC, FlightComputerUiSoundNetwork::handle);
    }

    /** Client-side entry point. Vanilla button audio remains muted; the server emits the block sound. */
    public static void request(BlockPos controllerPos, int soundId) {
        if (controllerPos == null || soundId < 0 || soundId > 4) return;
        PacketDistributor.sendToServer(new UiSoundPayload(controllerPos, soundId));
    }

    private static void handle(UiSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            FlightControllerBlockEntity controller = resolveController(player, payload.controllerPos());
            if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;

            SoundEvent sound = soundFor(payload.soundId());
            if (sound == null) return;

            // Deliberately use the exact same server-side block sound mechanism as
            // FlightControllerBlockEntity.applyAction(EMERGENCY_SHUTDOWN). This means the
            // client never plays a local UI sound and all nearby players hear the same sound.
            controller.getLevel().playSound(
                    null,
                    controller.getBlockPos(),
                    sound,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
        });
    }

    private static FlightControllerBlockEntity resolveController(ServerPlayer player, BlockPos pos) {
        BlockEntity root = player.level().getBlockEntity(pos);
        if (root instanceof FlightControllerBlockEntity controller) return controller;

        try {
            Class<?> companionType = Class.forName(
                    "dev.ryanhcode.sable.companion.SableCompanion",
                    false,
                    FlightComputerUiSoundNetwork.class.getClassLoader()
            );
            Object companion = companionType.getField("INSTANCE").get(null);
            if (companion == null) return null;

            Method tracking = findMethod(companion.getClass(), "getTrackingSubLevel", Entity.class);
            if (tracking != null) {
                Object subLevel = tracking.invoke(companion, player);
                FlightControllerBlockEntity controller = embeddedController(subLevel, pos);
                if (controller != null) return controller;
            }

            Method containing = findMethod(companion.getClass(), "getContaining", Level.class, Vec3.class);
            if (containing != null) {
                Object subLevel = containing.invoke(companion, player.level(), Vec3.atCenterOf(pos));
                FlightControllerBlockEntity controller = embeddedController(subLevel, pos);
                if (controller != null) return controller;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        return null;
    }

    private static FlightControllerBlockEntity embeddedController(Object subLevel, BlockPos pos) {
        if (subLevel == null) return null;
        try {
            Method accessorMethod = findMethod(subLevel.getClass(), "getEmbeddedLevelAccessor");
            if (accessorMethod == null) return null;
            Object accessor = accessorMethod.invoke(subLevel);
            if (accessor instanceof Level level) {
                BlockEntity be = level.getBlockEntity(pos);
                return be instanceof FlightControllerBlockEntity controller ? controller : null;
            }
            Method getBlockEntity = findMethod(accessor.getClass(), "getBlockEntity", BlockPos.class);
            if (getBlockEntity == null) return null;
            Object be = getBlockEntity.invoke(accessor, pos);
            return be instanceof FlightControllerBlockEntity controller ? controller : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
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

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
