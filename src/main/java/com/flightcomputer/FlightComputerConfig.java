package com.flightcomputer;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tunable controller power and thermal values. */
public final class FlightComputerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue ENERGY_INPUT_PER_TICK;
    public static final ModConfigSpec.IntValue IDLE_OPERATION_COST;
    public static final ModConfigSpec.IntValue BASE_OPERATION_COST;
    public static final ModConfigSpec.IntValue TERRAIN_OPERATION_COST;
    public static final ModConfigSpec.IntValue ADVANCED_COOLING_EXTRA_COST;
    public static final ModConfigSpec.IntValue MEDIUM_THRESHOLD;
    public static final ModConfigSpec.IntValue LOW_THRESHOLD;
    public static final ModConfigSpec.IntValue CRITICAL_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HEAT_CAPACITY;
    public static final ModConfigSpec.DoubleValue BASE_HEAT_PER_TICK;
    public static final ModConfigSpec.DoubleValue COOLING_PER_TICK;
    public static final ModConfigSpec.DoubleValue BASIC_COOLING_MODIFIER;
    public static final ModConfigSpec.DoubleValue IMPROVED_COOLING_MODIFIER;
    public static final ModConfigSpec.DoubleValue ADVANCED_COOLING_MODIFIER;
    public static final ModConfigSpec.DoubleValue THERMAL_WARM_THRESHOLD;
    public static final ModConfigSpec.DoubleValue THERMAL_WARNING_THRESHOLD;
    public static final ModConfigSpec.DoubleValue THERMAL_SHUTDOWN_THRESHOLD;
    public static final ModConfigSpec.DoubleValue THERMAL_RECOVERY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue ADVANCED_COOLING_MAX_TEMPERATURE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("flight_controller");

        // The renamed FE keys intentionally avoid silently reusing older 0.6.x configs
        // that could still contain the original 100,000 FE capacity / zero-consumption values.
        ENERGY_CAPACITY = builder.comment("Maximum FE stored by one Flight Controller. Default: 5,000,000 FE.")
                .defineInRange("energyCapacityFe", 5000000, 1, Integer.MAX_VALUE);
        ENERGY_INPUT_PER_TICK = builder.comment("Maximum FE received per tick from the rear-face energy capability.")
                .defineInRange("energyInputFePerTick", 12000, 1, Integer.MAX_VALUE);

        // 1,250 FE/t is approximately the output of ten Mekanism Advanced Solar Generators
        // at their documented 300 J/t output, using Mekanism's 1 FE = 2.5 J conversion.
        // Additional operating and terrain loads scale the demand upward from there.
        IDLE_OPERATION_COST = builder.comment("FE/t consumed continuously while the controller is powered. Default: 1,250 FE/t.")
                .defineInRange("idleOperationCostFePerTick", 1250, 1, Integer.MAX_VALUE);
        BASE_OPERATION_COST = builder.comment("Additional FE/t consumed while the controller is actively engaged.")
                .defineInRange("baseOperationCostFePerTick", 750, 0, Integer.MAX_VALUE);
        TERRAIN_OPERATION_COST = builder.comment("Additional FE/t consumed while MAP: ON terrain rendering is enabled. MAP: OFF removes this load.")
                .defineInRange("terrainOperationCostFePerTick", 500, 0, Integer.MAX_VALUE);
        ADVANCED_COOLING_EXTRA_COST = builder.comment("Additional FE/t consumed by Advanced Cooling while operating continuously.")
                .defineInRange("advancedCoolingExtraCostFePerTick", 2500, 0, Integer.MAX_VALUE);

        MEDIUM_THRESHOLD = builder.comment("Percent energy remaining at which the Medium warning begins.")
                .defineInRange("mediumThresholdPercent", 50, 1, 99);
        LOW_THRESHOLD = builder.comment("Percent energy remaining at which Low power begins.")
                .defineInRange("lowThresholdPercent", 25, 1, 98);
        CRITICAL_THRESHOLD = builder.comment("Percent energy remaining at which Critical power begins.")
                .defineInRange("criticalThresholdPercent", 10, 1, 97);

        HEAT_CAPACITY = builder.comment("Maximum operating temperature before thermal shutdown.")
                .defineInRange("heatCapacity", 100.0D, 1.0D, 1000000.0D);
        BASE_HEAT_PER_TICK = builder.comment("Heat generated per operating tick before cooling modifiers.")
                .defineInRange("baseHeatPerTick", 0.08D, 0.0D, 10000.0D);
        COOLING_PER_TICK = builder.comment("Heat removed per tick by the base cooling system.")
                .defineInRange("coolingPerTick", 0.04D, 0.0D, 10000.0D);
        BASIC_COOLING_MODIFIER = builder.comment("Cooling multiplier for Basic Cooling.")
                .defineInRange("basicCoolingModifier", 1.75D, 1.0D, 1000.0D);
        IMPROVED_COOLING_MODIFIER = builder.comment("Cooling multiplier for Improved Cooling.")
                .defineInRange("improvedCoolingModifier", 3.0D, 1.0D, 1000.0D);
        ADVANCED_COOLING_MODIFIER = builder.comment("Cooling multiplier for Advanced Cooling.")
                .defineInRange("advancedCoolingModifier", 8.0D, 1.0D, 1000.0D);
        THERMAL_WARM_THRESHOLD = builder.comment("Temperature fraction at which the controller becomes Warm.")
                .defineInRange("thermalWarmThreshold", 0.50D, 0.0D, 1.0D);
        THERMAL_WARNING_THRESHOLD = builder.comment("Temperature fraction at which the overheat warning begins.")
                .defineInRange("thermalWarningThreshold", 0.75D, 0.0D, 1.0D);
        THERMAL_SHUTDOWN_THRESHOLD = builder.comment("Temperature fraction at which operation shuts down.")
                .defineInRange("thermalShutdownThreshold", 1.0D, 0.01D, 1.0D);
        THERMAL_RECOVERY_THRESHOLD = builder.comment("Temperature fraction below which thermal shutdown recovers.")
                .defineInRange("thermalRecoveryThreshold", 0.25D, 0.0D, 0.99D);
        ADVANCED_COOLING_MAX_TEMPERATURE = builder.comment("Maximum temperature fraction allowed while Advanced Cooling is active.")
                .defineInRange("advancedCoolingMaxTemperature", 0.70D, 0.0D, 1.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private FlightComputerConfig() {}
}
