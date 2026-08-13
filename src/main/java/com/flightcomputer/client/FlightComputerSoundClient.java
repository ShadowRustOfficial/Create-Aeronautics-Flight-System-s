package com.flightcomputer.client;

import com.flightcomputer.integration.SoundPhysicsCompat;
import com.flightcomputer.integration.soundphysics.SableAcousticCache;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side ship-wide flight ambience and per-controller mute state. */
public final class FlightComputerSoundClient {
    private static final Map<UUID, Boolean> MUTED = new HashMap<>();
    private static AmbientLoop activeDrone;
    private static AmbientLoop activePropulsion;
    private static UUID activeController;
    private static String lastLoggedMode = "";

    private static final double MAX_AMBIENT_SOURCE_DISTANCE = 32.0D;
    private static final double PROPULSION_START_SPEED = 0.15D;
    private static final double PROPULSION_FULL_SPEED = 1.60D;
    private static final float DRONE_VOLUME = 0.17F;
    private static final float PROPULSION_MAX_VOLUME = 0.40F;

    private FlightComputerSoundClient() { }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            stopAmbient();
            SableAcousticCache.tick(null);
            return;
        }

        String mode = SoundPhysicsCompat.mode();
        if (!mode.equals(lastLoggedMode)) lastLoggedMode = mode;

        LocalPlayer player = minecraft.player;
        FlightComputerNetwork.TelemetryPayload best = null;
        double bestDistance = Double.MAX_VALUE;

        for (FlightComputerNetwork.TelemetryPayload payload : FlightComputerTelemetryClient.snapshots()) {
            if (!FlightRouteTelemetryClient.isStabiliserActive(payload.controllerId())) continue;
            if (isMuted(payload.controllerId())) continue;

            double distance = player.distanceToSqr(payload.x(), payload.y(), payload.z());
            if (distance < bestDistance && distance <= MAX_AMBIENT_SOURCE_DISTANCE * MAX_AMBIENT_SOURCE_DISTANCE) {
                bestDistance = distance;
                best = payload;
            }
        }

        if (best == null) {
            stopAmbient();
            SableAcousticCache.tick(minecraft);
            return;
        }

        UUID id = best.controllerId();
        if (activeController == null || !id.equals(activeController)) {
            stopAmbient();
            activeController = id;
        }

        SableAcousticCache.registerSource(new net.minecraft.world.phys.Vec3(best.x(), best.y(), best.z()));

        if (activeDrone == null || activeDrone.isStopped()) {
            activeDrone = new AmbientLoop(ModSounds.AMBIENT_DRONE.get(), best, DRONE_VOLUME, false);
            minecraft.getSoundManager().play(activeDrone);
        } else {
            activeDrone.setTelemetry(best);
            activeDrone.setBaseVolume(DRONE_VOLUME);
        }

        double speed = safeSpeed(best.speed());
        double intensity = smoothStep(PROPULSION_START_SPEED, PROPULSION_FULL_SPEED, speed);

        if (intensity <= 0.001D) {
            stopPropulsion();
        } else if (activePropulsion == null || activePropulsion.isStopped()) {
            activePropulsion = new AmbientLoop(ModSounds.AMBIENT_FLIGHT.get(), best, PROPULSION_MAX_VOLUME, true);
            activePropulsion.setIntensity((float) intensity);
            minecraft.getSoundManager().play(activePropulsion);
        } else {
            activePropulsion.setTelemetry(best);
            activePropulsion.setIntensity((float) intensity);
        }

        // Refresh all prepared Sable acoustic data only on the client thread.
        SableAcousticCache.tick(minecraft);
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
        if (activeDrone != null) activeDrone.stopNow();
        if (activePropulsion != null) activePropulsion.stopNow();
        activeDrone = null;
        activePropulsion = null;
        activeController = null;
    }

    private static void stopPropulsion() {
        if (activePropulsion != null) activePropulsion.stopNow();
        activePropulsion = null;
    }

    private static double safeSpeed(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static double smoothStep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) return value >= edge1 ? 1.0D : 0.0D;
        double x = Math.max(0.0D, Math.min(1.0D, (value - edge0) / (edge1 - edge0)));
        return x * x * (3.0D - 2.0D * x);
    }

    private static final class AmbientLoop extends AbstractTickableSoundInstance {
        private FlightComputerNetwork.TelemetryPayload telemetry;
        private final float maxVolume;
        private final boolean intensityScaled;
        private float intensity = 1.0F;

        private AmbientLoop(SoundEvent sound, FlightComputerNetwork.TelemetryPayload telemetry,
                            float maxVolume, boolean intensityScaled) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.telemetry = telemetry;
            this.maxVolume = maxVolume;
            this.intensityScaled = intensityScaled;
            this.looping = true;
            this.delay = 0;
            this.volume = maxVolume;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            updatePosition();
        }

        @Override public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.player.isRemoved()) {
                stop();
                return;
            }
            if (!FlightRouteTelemetryClient.isStabiliserActive(activeController)) {
                stop();
                return;
            }
            updatePosition();
            float targetVolume = intensityScaled
                    ? maxVolume * Math.max(0.0F, Math.min(1.0F, intensity))
                    : maxVolume;
            this.volume += (targetVolume - this.volume) * 0.12F;
            if (intensityScaled) {
                this.pitch = 0.92F + 0.16F * Math.max(0.0F, Math.min(1.0F, intensity));
            }
        }

        private void setTelemetry(FlightComputerNetwork.TelemetryPayload telemetry) {
            if (telemetry != null) this.telemetry = telemetry;
        }

        private void setBaseVolume(float volume) {
            if (!intensityScaled) this.volume += (volume - this.volume) * 0.12F;
        }

        private void setIntensity(float intensity) {
            this.intensity = intensity;
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
