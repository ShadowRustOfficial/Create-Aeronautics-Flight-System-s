package com.flightcomputer.client;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.integration.SoundPhysicsCompat;
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

/**
 * Client-side ship-wide stabiliser ambience and per-controller mute state.
 *
 * The sound is intentionally a normal, positional BLOCKS source rather than a
 * player-relative UI sound. This lets vanilla provide the fallback attenuation
 * and allows Sound Physics Remastered / Sound Physics: Aeronautics to perform
 * occlusion, absorption, reverberation and supported Doppler processing.
 */
public final class FlightComputerSoundClient {
    private static final Map<UUID, Boolean> MUTED = new HashMap<>();
    private static AmbientLoop activeLoop;
    private static UUID activeController;
    private static String lastLoggedMode = "";

    private FlightComputerSoundClient() { }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            stopAmbient();
            return;
        }

        if (SoundPhysicsCompat.isLoaded()) {
            String mode = SoundPhysicsCompat.mode();
            if (!mode.equals(lastLoggedMode)) {
                lastLoggedMode = mode;
            }
        } else {
            lastLoggedMode = "VANILLA_POSITIONAL";
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
            activeLoop = new AmbientLoop(ModSounds.AMBIENT_SHIP.get(), best);
            minecraft.getSoundManager().play(activeLoop);
        } else {
            activeLoop.setTelemetry(best);
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
        private FlightComputerNetwork.TelemetryPayload telemetry;

        private AmbientLoop(SoundEvent sound, FlightComputerNetwork.TelemetryPayload telemetry) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.telemetry = telemetry;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.35F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            updatePosition();
        }

        @Override public void tick() {
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.isRemoved()) {
                stop();
                return;
            }
            updatePosition();
        }

        private void setTelemetry(FlightComputerNetwork.TelemetryPayload telemetry) {
            if (telemetry != null) this.telemetry = telemetry;
        }

        private void updatePosition() {
            if (telemetry == null) {
                stop();
                return;
            }
            this.x = telemetry.x();
            this.y = telemetry.y();
            this.z = telemetry.z();
        }

        private void stopNow() { stop(); }
    }
}
