package com.flightcomputer.client;

import com.flightcomputer.integration.soundphysics.SableAcousticCache;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.network.FlightRouteTelemetryNetwork;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

/**
 * Legacy ship ambient layer. It runs independently of the quiet stabiliser drone so the supplied
 * legacy ambience is audible whenever stabiliser OR autopilot is active.
 */
@EventBusSubscriber(modid = com.flightcomputer.FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class LegacyFlightAmbientClient {
    private static final double MAX_SOURCE_DISTANCE = 32.0D;
    private static final float MAX_VOLUME = 0.22F;
    private static final float FADE_STEP = 0.08F;
    private static final int SABLE_REFRESH_INTERVAL_TICKS = 5;

    private static LegacyAmbientLoop activeLoop;
    private static UUID activeController;
    private static int sableRefreshCooldown;

    private LegacyFlightAmbientClient() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            stop();
            sableRefreshCooldown = 0;
            SableAcousticCache.tick(null);
            return;
        }

        FlightComputerNetwork.TelemetryPayload best = null;
        double bestDistance = Double.MAX_VALUE;
        for (FlightComputerNetwork.TelemetryPayload payload : FlightComputerTelemetryClient.snapshots()) {
            FlightRouteTelemetryNetwork.RouteStatePayload state = FlightRouteTelemetryClient.get(payload.controllerId());
            if (!isStabiliserOrAutopilot(state)) continue;
            double distance = minecraft.player.distanceToSqr(payload.x(), payload.y(), payload.z());
            if (distance <= MAX_SOURCE_DISTANCE * MAX_SOURCE_DISTANCE && distance < bestDistance) {
                bestDistance = distance;
                best = payload;
            }
        }

        if (best == null) {
            stop();
            sableRefreshCooldown = 0;
            SableAcousticCache.tick(null);
            return;
        }

        if (activeController == null || !activeController.equals(best.controllerId()) || activeLoop == null || activeLoop.isStopped()) {
            stop();
            activeController = best.controllerId();
            activeLoop = new LegacyAmbientLoop(ModSounds.AMBIENT_SHIP.get(), best);
            minecraft.getSoundManager().play(activeLoop);
            sableRefreshCooldown = 0;
        } else {
            activeLoop.setTelemetry(best);
        }

        SableAcousticCache.registerSource(new net.minecraft.world.phys.Vec3(best.x(), best.y(), best.z()));
        if (sableRefreshCooldown <= 0) {
            SableAcousticCache.tick(minecraft);
            sableRefreshCooldown = SABLE_REFRESH_INTERVAL_TICKS;
        } else {
            sableRefreshCooldown--;
        }
    }

    private static boolean isStabiliserOrAutopilot(FlightRouteTelemetryNetwork.RouteStatePayload state) {
        return state != null && (state.stabiliser()
                || state.mode() == com.flightcomputer.control.FlightMode.AUTOPILOT.ordinal());
    }

    private static void stop() {
        if (activeLoop != null) activeLoop.stopNow();
        activeLoop = null;
        activeController = null;
    }

    private static final class LegacyAmbientLoop extends AbstractTickableSoundInstance {
        private FlightComputerNetwork.TelemetryPayload telemetry;
        private boolean fadingOut;

        private LegacyAmbientLoop(SoundEvent sound, FlightComputerNetwork.TelemetryPayload telemetry) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.telemetry = telemetry;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            updatePosition();
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.player.isRemoved()) {
                stop();
                return;
            }
            FlightRouteTelemetryNetwork.RouteStatePayload state = FlightRouteTelemetryClient.get(activeController);
            if (!isStabiliserOrAutopilot(state)) {
                fadingOut = true;
            }
            updatePosition();
            if (fadingOut) {
                volume = Math.max(0.0F, volume - FADE_STEP);
                if (volume <= 0.001F) stop();
            } else {
                volume += (MAX_VOLUME - volume) * 0.08F;
            }
        }

        private void setTelemetry(FlightComputerNetwork.TelemetryPayload telemetry) {
            if (telemetry != null) {
                this.telemetry = telemetry;
                this.fadingOut = false;
            }
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
