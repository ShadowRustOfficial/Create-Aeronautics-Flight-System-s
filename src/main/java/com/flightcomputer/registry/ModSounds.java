package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, FlightComputer.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_SHIP = register("ambient_ship");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_HEAT_CRITICAL = register("engine_heat_critical");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_ENGINE_OVERHEAT = register("warning_engine_overheat");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SYSTEMS_ACTIVE = register("fire_systems_active");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_NEUTRALISED = register("fire_neutralised");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMERGENCY_SHUTDOWN = register("emergency_shutdown");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, name)));
    }

    private ModSounds() { }
}
