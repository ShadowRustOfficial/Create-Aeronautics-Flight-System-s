package com.flightcomputer.network;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.item.CoolingUpgradeItem;
import com.flightcomputer.registry.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative audio for the dedicated Cooling inventory actions. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID)
public final class CoolingSoundNetwork {
    private static final String VERSION = "1";
    private static final CustomPacketPayload.Type<CoolingSoundPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "cooling_sound"));

    private CoolingSoundNetwork() { }

    public record CoolingSoundPayload(BlockPos controllerPos, int slot, int action) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, CoolingSoundPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CoolingSoundPayload::controllerPos,
                ByteBufCodecs.VAR_INT, CoolingSoundPayload::slot,
                ByteBufCodecs.VAR_INT, CoolingSoundPayload::action,
                CoolingSoundPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToServer(TYPE, CoolingSoundPayload.STREAM_CODEC, CoolingSoundNetwork::handle);
    }

    public static void request(BlockPos controllerPos, int slot, int action) {
        if (controllerPos == null || slot < 0 || slot >= 3 || (action != 0 && action != 1)) return;
        PacketDistributor.sendToServer(new CoolingSoundPayload(controllerPos, slot, action));
    }

    private static void handle(CoolingSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !near(player, payload.controllerPos(), 64.0D)) return;
            if (!(player.level().getBlockEntity(payload.controllerPos()) instanceof FlightControllerBlockEntity controller)) return;
            if (controller.isThermalLockout() || payload.slot() < 0 || payload.slot() >= 3) return;

            var handler = controller.getUpgradeHandler();
            if (payload.action() == 0) {
                var hand = player.getMainHandItem();
                if (hand.isEmpty() || !(hand.getItem() instanceof CoolingUpgradeItem)) return;
                if (!handler.insertItem(payload.slot(), hand.copyWithCount(1), true).isEmpty()) return;
                controller.getLevel().playSound(null, controller.getBlockPos(), ModSounds.COOLING_INSERT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                if (handler.getStackInSlot(payload.slot()).isEmpty()) return;
                controller.getLevel().playSound(null, controller.getBlockPos(), ModSounds.COOLING_REMOVE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        });
    }

    private static boolean near(ServerPlayer player, BlockPos pos, double radius) {
        return player != null && pos != null && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radius * radius;
    }
}
