package com.flightcomputer;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tunable controller power and thermal values. */
public final class FlightComputerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue ENERGY_INPUT_PER_TICK;
    public static final ModConfigSpec.IntValue BASE_OPERATION_COST;
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
        ENERGY_CAPACITY = builder.comment("Maximum FE stored by one Flight Controller.").defineInRange("energyCapacity", 100000, 1, Integer.MAX_VALUE);
        ENERGY_INPUT_PER_TICK = builder.comment("Maximum FE received per tick.").defineInRange("energyInputPerTick", 2000, 1, Integer.MAX_VALUE);
        BASE_OPERATION_COST = builder.comment("Base FE consumed per tick while the controller is operating.").defineInRange("baseOperationCost", 40, 0, Integer.MAX_VALUE);
        ADVANCED_COOLING_EXTRA_COST = builder.comment("Additional FE/t consumed by Advanced Cooling while operating.").defineInRange("advancedCoolingExtraCost", 160, 0, Integer.MAX_VALUE);
        MEDIUM_THRESHOLD = builder.comment("Percent energy remaining at which the Medium warning begins.").defineInRange("mediumThresholdPercent", 50, 1, 99);
        LOW_THRESHOLD = builder.comment("Percent energy remaining at which Low power begins.").defineInRange("lowThresholdPercent", 25, 1, 98);
        CRITICAL_THRESHOLD = builder.comment("Percent energy remaining at which Critical power begins.").defineInRange("criticalThresholdPercent", 10, 1, 97);
        HEAT_CAPACITY = builder.comment("Maximum operating temperature before thermal shutdown.").defineInRange("heatCapacity", 100.0D, 1.0D, 1000000.0D);
        BASE_HEAT_PER_TICK = builder.comment("Heat generated per operating tick before cooling modifiers.").defineInRange("baseHeatPerTick", 0.08D, 0.0D, 10000.0D);
        COOLING_PER_TICK = builder.comment("Heat removed per tick by the base cooling system.").defineInRange("coolingPerTick", 0.04D, 0.0D, 10000.0D);
        BASIC_COOLING_MODIFIER = builder.comment("Cooling multiplier for Basic Cooling.").defineInRange("basicCoolingModifier", 1.75D, 1.0D, 1000.0D);
        IMPROVED_COOLING_MODIFIER = builder.comment("Cooling multiplier for Improved Cooling.").defineInRange("improvedCoolingModifier", 3.0D, 1.0D, 1000.0D);
        ADVANCED_COOLING_MODIFIER = builder.comment("Cooling multiplier for Advanced Cooling.").defineInRange("advancedCoolingModifier", 8.0D, 1.0D, 1000.0D);
        THERMAL_WARM_THRESHOLD = builder.comment("Temperature fraction at which the controller becomes Warm.").defineInRange("thermalWarmThreshold", 0.50D, 0.0D, 1.0D);
        THERMAL_WARNING_THRESHOLD = builder.comment("Temperature fraction at which the overheat warning begins.").defineInRange("thermalWarningThreshold", 0.75D, 0.0D, 1.0D);
        THERMAL_SHUTDOWN_THRESHOLD = builder.comment("Temperature fraction at which operation shuts down.").defineInRange("thermalShutdownThreshold", 1.0D, 0.01D, 1.0D);
        THERMAL_RECOVERY_THRESHOLD = builder.comment("Temperature fraction below which thermal shutdown recovers.").defineInRange("thermalRecoveryThreshold", 0.25D, 0.0D, 0.99D);
        ADVANCED_COOLING_MAX_TEMPERATURE = builder.comment("Maximum temperature fraction allowed while Advanced Cooling is active.").defineInRange("advancedCoolingMaxTemperature", 0.70D, 0.0D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightComputerConfig() {}
}
