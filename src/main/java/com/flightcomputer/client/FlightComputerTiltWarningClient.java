package com.flightcomputer.client;

import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/** Client-side warning audio for excessive aircraft attitude. */
public final class FlightComputerTiltWarningClient {
    private static final double EXCESSIVE_TILT_DEGREES = 35.0D;
    private static final int WARNING_COOLDOWN_TICKS = 40;
    private static int cooldown;

    private FlightComputerTiltWarningClient() { }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) { cooldown = 0; return; }
        if (cooldown > 0) cooldown--;

        FlightComputerNetwork.TelemetryPayload warning = null;
        double nearest = Double.MAX_VALUE;
        LocalPlayer player = mc.player;
        for (FlightComputerNetwork.TelemetryPayload payload : FlightComputerTelemetryClient.snapshots()) {
            if (!FlightRouteTelemetryClient.isFlightControlActive(payload.controllerId())) continue;
            double tilt = Math.max(Math.abs(payload.pitch()), Math.abs(payload.roll()));
            if (tilt < EXCESSIVE_TILT_DEGREES) continue;
            double distance = player.distanceToSqr(payload.x(), payload.y(), payload.z());
            if (distance < nearest) { nearest = distance; warning = payload; }
        }

        if (warning == null || cooldown > 0) return;
        cooldown = WARNING_COOLDOWN_TICKS;
        mc.level.playLocalSound(warning.x(), warning.y(), warning.z(), ModSounds.TILT_WARNING.get(),
                SoundSource.BLOCKS, 1.0F, 1.0F, false);
    }
}
