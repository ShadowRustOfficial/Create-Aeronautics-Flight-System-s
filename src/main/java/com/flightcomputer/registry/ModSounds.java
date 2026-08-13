package com.flightcomputer.registry;

import com.flightcomputer.FlightComputer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, FlightComputer.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> STABILISER_AMBIENT =
            REGISTRY.register("stabiliser_ambient", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "stabiliser_ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_HEAT_CRITICAL =
            REGISTRY.register("engine_heat_critical", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "engine_heat_critical")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_OVERHEAT =
            REGISTRY.register("engine_overheat", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "engine_overheat")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SYSTEMS_ACTIVE =
            REGISTRY.register("fire_systems_active", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "fire_systems_active")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EMERGENCY_SHUTDOWN =
            REGISTRY.register("emergency_shutdown", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FlightComputer.MOD_ID, "emergency_shutdown")));

    private ModSounds() { }
}
