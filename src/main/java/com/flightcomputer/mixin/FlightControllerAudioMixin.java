package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.ThermalState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Edge-triggered controller audio. Keeps warning sounds out of the per-tick control loop. */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerAudioMixin {
    private static final int FIRE_DELAY_TICKS = 100;
    private static final int EMERGENCY_SOUND_COOLDOWN = 200;
    private static final Map<UUID, Integer> FIRE_PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_EMERGENCY_SOUND = new ConcurrentHashMap<>();

    @Inject(method = "applyAction", at = @At("HEAD"))
    private void flightcomputer$emergencySound(FlightControllerAction action,
                                                CallbackInfoReturnable<?> cir) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (action != FlightControllerAction.EMERGENCY_SHUTDOWN || controller.getLevel() == null
                || controller.getLevel().isClientSide() || !controller.isEngaged()) return;

        long now = controller.getLevel().getGameTime();
        long previous = LAST_EMERGENCY_SOUND.getOrDefault(controller.getControllerId(), Long.MIN_VALUE);
        if (now - previous < EMERGENCY_SOUND_COOLDOWN) return;

        LAST_EMERGENCY_SOUND.put(controller.getControllerId(), now);
        controller.getLevel().playSound(null,
                controller.getBlockPos(), ModSounds.EMERGENCY_SHUTDOWN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Inject(method = "onThermalStateChanged", at = @At("HEAD"))
    private void flightcomputer$thermalTransition(ThermalState previous, ThermalState current, CallbackInfo ci) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || previous == current) return;

        if (current == ThermalState.CRITICAL) {
            controller.getLevel().playSound(null, controller.getBlockPos(),
                    ModSounds.ENGINE_HEAT_CRITICAL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (current == ThermalState.THERMAL_SHUTDOWN && previous != ThermalState.THERMAL_SHUTDOWN) {
            controller.getLevel().playSound(null, controller.getBlockPos(),
                    ModSounds.WARNING_ENGINE_OVERHEAT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            FIRE_PENDING.put(controller.getControllerId(), FIRE_DELAY_TICKS);
        }

        if (previous == ThermalState.THERMAL_SHUTDOWN && current != ThermalState.THERMAL_SHUTDOWN) {
            FIRE_PENDING.remove(controller.getControllerId());
            // The fire-neutralised asset is intentionally called only on the recovery edge.
            controller.getLevel().playSound(null, controller.getBlockPos(),
                    ModSounds.FIRE_NEUTRALISED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void flightcomputer$fireTimer(CallbackInfo ci) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (controller.getLevel() == null || controller.getLevel().isClientSide()) return;

        UUID id = controller.getControllerId();
        Integer remaining = FIRE_PENDING.get(id);
        if (remaining == null) return;
        if (!controller.isThermalShutdown()) {
            FIRE_PENDING.remove(id);
            return;
        }
        if (remaining > 1) {
            FIRE_PENDING.put(id, remaining - 1);
            return;
        }

        FIRE_PENDING.remove(id);
        controller.getLevel().playSound(null, controller.getBlockPos(),
                ModSounds.FIRE_SYSTEMS_ACTIVE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
