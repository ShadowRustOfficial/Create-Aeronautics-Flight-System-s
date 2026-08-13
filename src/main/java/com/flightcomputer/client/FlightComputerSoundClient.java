package com.flightcomputer.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side ship-wide stabiliser ambience and per-controller mute state. */
public final class FlightComputerSoundClient {
    private static final Map<UUID, Boolean> MUTED = new HashMap<>();
    private static AmbientLoop activeLoop;
    private static UUID activeController;

    private FlightComputerSoundClient() { }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            stopAmbient();
            return;
        }

        LocalPlayer player = minecraft.player;
        FlightComputerNetwork.TelemetryPayload best = null;
        double bestDistance = Double.MAX_VALUE;

        for (FlightComputerNetwork.TelemetryPayload payload : FlightComputerTelemetryClient.snapshots()) {
            BlockPos pos = BlockPos.containing(payload.x(), payload.y(), payload.z());
            BlockEntity be = minecraft.level.getBlockEntity(pos);
            if (!(be instanceof FlightControllerBlockEntity controller)) continue;
            if (!controller.getControllerState().stabiliser()) continue;
            if (isMuted(controller.getControllerId())) continue;

            double distance = player.distanceToSqr(payload.x(), payload.y(), payload.z());
            if (distance < bestDistance && distance <= 128.0D * 128.0D) {
                bestDistance = distance;
                best = payload;
            }
        }

        if (best == null) {
            stopAmbient();
            return;
        }

        UUID id = best.controllerId();
        if (activeLoop == null || activeLoop.isStopped() || !id.equals(activeController)) {
            stopAmbient();
            activeController = id;
            activeLoop = new AmbientLoop(player, ModSounds.AMBIENT_SHIP.get());
            minecraft.getSoundManager().play(activeLoop);
        }
    }

    public static boolean isMuted(UUID controllerId) {
        return controllerId != null && MUTED.getOrDefault(controllerId, false);
    }

    public static boolean toggleMuted(UUID controllerId) {
        if (controllerId == null) return false;
        boolean muted = !isMuted(controllerId);
        MUTED.put(controllerId, muted);
        if (muted && controllerId.equals(activeController)) stopAmbient();
        return muted;
    }

    private static void stopAmbient() {
        if (activeLoop != null) activeLoop.stopNow();
        activeLoop = null;
        activeController = null;
    }

    private static final class AmbientLoop extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        private AmbientLoop(LocalPlayer player, SoundEvent sound) {
            super(sound, SoundSource.AMBIENT, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.35F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            updatePosition();
        }

        @Override public void tick() {
            if (player.isRemoved()) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        private void stopNow() { stop(); }
    }
}
