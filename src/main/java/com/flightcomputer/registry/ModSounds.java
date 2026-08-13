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

    /** Legacy quiet drone registration retained for resource compatibility. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_DRONE = register("ambient_drone_quiet");
    /** Legacy deep propulsion registration retained for resource compatibility. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_FLIGHT = register("ambient_flight");
    /** Legacy ambient alias retained for resource compatibility. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_SHIP = register("ambient_ship");

    /** Primary supplied integrated takeoff recording. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TAKEOFF_INTEGRATED = register("takeoff_integrated");
    /** Primary supplied integrated continuous flight recording. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLIGHT_LOOP_INTEGRATED = register("flight_loop_integrated");
    /** Additional supplied ambient flight layer (Ghost 2). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_FLIGHT_GHOST_2 = register("ambient_flight_ghost_2");

    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_HEAT_CRITICAL = register("engine_heat_critical");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_ENGINE_OVERHEAT = register("warning_engine_overheat");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SYSTEMS_ACTIVE = register("fire_systems_active");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_NEUTRALISED = register("fire_neutralised");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMERGENCY_SHUTDOWN = register("emergency_shutdown");
    /** Engine spool-down sound played with a guarded emergency shutdown transition. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_POWERING_DOWN = register("engine_powering_down");
    /** High-speed exterior pass; deliberately separate from the continuous flight ambience. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYBY_AIRCRAFT = register("flyby_aircraft");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, name)));
    }

    private ModSounds() { }
}
