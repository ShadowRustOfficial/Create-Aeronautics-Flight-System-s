package com.flightcomputer.client;

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

public final class FlightComputerSoundClient {
    private static final Map<UUID, Boolean> MUTED = new HashMap<>();
    private static AmbientLoop activeFlightLoop, activeGhostAmbient;
    private static OneShotLoop activeTakeoff;
    private static UUID activeController;
    private static int activeTicks;
    private static boolean takeoffStarted;
    private static final double MAX_AMBIENT_SOURCE_DISTANCE = 32.0D;
    private static final int TAKEOFF_HANDOFF_TICKS = 440;
    private static final int TAKEOFF_CROSSFADE_TICKS = 20;
    private static final float FLIGHT_LOOP_VOLUME = 0.32F;
    private static final float GHOST_AMBIENT_VOLUME = 0.075F;
    private static final int SABLE_REFRESH_INTERVAL_TICKS = 5;
    private static int sableRefreshCooldown;

    private FlightComputerSoundClient() { }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            stopAmbient();
            sableRefreshCooldown = 0;
            SableAcousticCache.tick(null);
            return;
        }

        LocalPlayer player = mc.player;
        FlightComputerNetwork.TelemetryPayload best = null;
        double bestDistance = Double.MAX_VALUE;
        for (FlightComputerNetwork.TelemetryPayload payload : FlightComputerTelemetryClient.snapshots()) {
            if (!FlightRouteTelemetryClient.isFlightControlActive(payload.controllerId()) || isMuted(payload.controllerId())) continue;
            double d = player.distanceToSqr(payload.x(), payload.y(), payload.z());
            if (d < bestDistance && d <= MAX_AMBIENT_SOURCE_DISTANCE * MAX_AMBIENT_SOURCE_DISTANCE) {
                bestDistance = d;
                best = payload;
            }
        }

        if (best == null) {
            stopAmbient();
            sableRefreshCooldown = 0;
            SableAcousticCache.tick(null);
            return;
        }

        UUID id = best.controllerId();
        if (activeController == null || !id.equals(activeController)) {
            stopAmbient();
            activeController = id;
            activeTicks = 0;
            takeoffStarted = false;
            sableRefreshCooldown = 0;
        }

        activeTicks++;
        SableAcousticCache.registerSource(new net.minecraft.world.phys.Vec3(best.x(), best.y(), best.z()));

        // Start the supplied integrated takeoff sound once per active controller. It must
        // never restart simply because a non-looping instance naturally reaches its end.
        if (!takeoffStarted) {
            takeoffStarted = true;
            activeTakeoff = new OneShotLoop(ModSounds.TAKEOFF_INTEGRATED.get(), best, FLIGHT_LOOP_VOLUME);
            mc.getSoundManager().play(activeTakeoff);
        } else if (activeTakeoff != null && !activeTakeoff.isStopped()) {
            activeTakeoff.setTelemetry(best);
        }

        if (activeTicks >= TAKEOFF_HANDOFF_TICKS) {
            float f = (float) Math.max(0.0D, Math.min(1.0D,
                    (activeTicks - TAKEOFF_HANDOFF_TICKS) / (double) TAKEOFF_CROSSFADE_TICKS));
            if (activeFlightLoop == null || activeFlightLoop.isStopped()) {
                activeFlightLoop = new AmbientLoop(ModSounds.FLIGHT_LOOP_INTEGRATED.get(), best, FLIGHT_LOOP_VOLUME);
                activeFlightLoop.setFade(f);
                mc.getSoundManager().play(activeFlightLoop);
            } else {
                activeFlightLoop.setTelemetry(best);
                activeFlightLoop.setFade(f);
            }
            if (activeTakeoff != null && !activeTakeoff.isStopped()) {
                activeTakeoff.setFade(1.0F - f);
                if (f >= 1.0F) activeTakeoff.stopNow();
            }
        }

        if (activeGhostAmbient == null || activeGhostAmbient.isStopped()) {
            activeGhostAmbient = new AmbientLoop(ModSounds.AMBIENT_FLIGHT_GHOST_2.get(), best, GHOST_AMBIENT_VOLUME);
            mc.getSoundManager().play(activeGhostAmbient);
        } else {
            activeGhostAmbient.setTelemetry(best);
            activeGhostAmbient.setFade(1.0F);
        }

        if (sableRefreshCooldown <= 0) {
            SableAcousticCache.tick(mc);
            sableRefreshCooldown = SABLE_REFRESH_INTERVAL_TICKS;
        } else {
            sableRefreshCooldown--;
        }
    }

    public static boolean isMuted(UUID id) {
        return id != null && MUTED.getOrDefault(id, false);
    }

    public static boolean toggleMuted(UUID id) {
        if (id == null) return false;
        boolean muted = !isMuted(id);
        MUTED.put(id, muted);
        if (muted && id.equals(activeController)) stopAmbient();
        return muted;
    }

    private static void stopAmbient() {
        if (activeTakeoff != null) activeTakeoff.stopNow();
        if (activeFlightLoop != null) activeFlightLoop.stopNow();
        if (activeGhostAmbient != null) activeGhostAmbient.stopNow();
        activeTakeoff = null;
        activeFlightLoop = null;
        activeGhostAmbient = null;
        activeController = null;
        activeTicks = 0;
        takeoffStarted = false;
    }

    private static abstract class PositionalLoop extends AbstractTickableSoundInstance {
        private FlightComputerNetwork.TelemetryPayload telemetry;
        private final float maxVolume;
        private float fade = 1.0F;

        private PositionalLoop(SoundEvent sound, FlightComputerNetwork.TelemetryPayload telemetry,
                               float maxVolume, boolean looping) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.telemetry = telemetry;
            this.maxVolume = maxVolume;
            this.looping = looping;
            this.delay = 0;
            this.volume = maxVolume;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            updatePosition();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.isRemoved()) {
                stop();
                return;
            }
            if (!FlightRouteTelemetryClient.isFlightControlActive(activeController)) {
                stop();
                return;
            }
            updatePosition();
            this.volume += ((maxVolume * fade) - this.volume) * 0.12F;
        }

        protected final void setTelemetry(FlightComputerNetwork.TelemetryPayload telemetry) {
            if (telemetry != null) this.telemetry = telemetry;
        }

        protected final void setFade(float fade) {
            this.fade = Math.max(0.0F, Math.min(1.0F, fade));
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

        protected final void stopNow() {
            stop();
        }
    }

    private static final class AmbientLoop extends PositionalLoop {
        private AmbientLoop(SoundEvent sound, FlightComputerNetwork.TelemetryPayload telemetry, float volume) {
            super(sound, telemetry, volume, true);
        }
    }

    private static final class OneShotLoop extends PositionalLoop {
        private OneShotLoop(SoundEvent sound, FlightComputerNetwork.TelemetryPayload telemetry, float volume) {
            super(sound, telemetry, volume, false);
        }
    }
}
