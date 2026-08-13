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

    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_DRONE = register("ambient_drone_quiet");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_FLIGHT = register("ambient_flight");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_SHIP = register("ambient_ship");

    public static final DeferredHolder<SoundEvent, SoundEvent> TAKEOFF_INTEGRATED = register("takeoff_integrated");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLIGHT_LOOP_INTEGRATED = register("flight_loop_integrated");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_FLIGHT_GHOST_2 = register("ambient_flight_ghost_2");

    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_HEAT_CRITICAL = register("engine_heat_critical");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_ENGINE_OVERHEAT = register("warning_engine_overheat");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SYSTEMS_ACTIVE = register("fire_systems_active");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_NEUTRALISED = register("fire_neutralised");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMERGENCY_SHUTDOWN = register("emergency_shutdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_POWERING_DOWN = register("engine_powering_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYBY_AIRCRAFT = register("flyby_aircraft");

    /** UI toggle state sounds: repeatable, non-looping. */
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_TOGGLE_ON = register("ui_toggle_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_TOGGLE_OFF = register("ui_toggle_off");
    /** UI tab/open and general interaction sounds. */
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_OPEN = register("ui_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_INTERACT = register("ui_interact");
    /** Panel discovery/opening sound. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCOVER = register("discover");
    /** One-shot excessive tilt warning. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TILT_WARNING = register("tilt_warning");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, name)));
    }

    private ModSounds() { }
}
