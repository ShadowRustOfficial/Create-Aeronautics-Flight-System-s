package com.flightcomputer;

import net.neoforged.neoforge.common.ModConfigSpec;

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
    public static final ModConfigSpec.DoubleValue THERMAL_HOT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue THERMAL_CRITICAL_THRESHOLD;
    public static final ModConfigSpec.DoubleValue THERMAL_SHUTDOWN_THRESHOLD;
    public static final ModConfigSpec.DoubleValue THERMAL_RECOVERY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue ADVANCED_COOLING_MAX_TEMPERATURE;
    public static final ModConfigSpec.IntValue THERMAL_COOLDOWN_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("flight_controller");
        ENERGY_CAPACITY = b.comment("Maximum FE stored by one Flight Controller. Default: 20,000,000 FE.").defineInRange("energyCapacityFe", 20000000, 1, Integer.MAX_VALUE);
        ENERGY_INPUT_PER_TICK = b.defineInRange("energyInputFePerTick", 12000, 1, Integer.MAX_VALUE);
        IDLE_OPERATION_COST = b.defineInRange("idleOperationCostFePerTick", 1250, 1, Integer.MAX_VALUE);
        BASE_OPERATION_COST = b.defineInRange("baseOperationCostFePerTick", 750, 0, Integer.MAX_VALUE);
        TERRAIN_OPERATION_COST = b.defineInRange("terrainOperationCostFePerTick", 500, 0, Integer.MAX_VALUE);
        ADVANCED_COOLING_EXTRA_COST = b.defineInRange("advancedCoolingExtraCostFePerTick", 2500, 0, Integer.MAX_VALUE);
        MEDIUM_THRESHOLD = b.defineInRange("mediumThresholdPercent", 50, 1, 99);
        LOW_THRESHOLD = b.defineInRange("lowThresholdPercent", 25, 1, 98);
        CRITICAL_THRESHOLD = b.defineInRange("criticalThresholdPercent", 10, 1, 97);
        HEAT_CAPACITY = b.defineInRange("heatCapacity", 100.0D, 1.0D, 1000000.0D);
        BASE_HEAT_PER_TICK = b.defineInRange("baseHeatPerTick", 0.08D, 0.0D, 10000.0D);
        COOLING_PER_TICK = b.defineInRange("coolingPerTick", 0.04D, 0.0D, 10000.0D);
        BASIC_COOLING_MODIFIER = b.defineInRange("basicCoolingModifier", 1.75D, 1.0D, 1000.0D);
        IMPROVED_COOLING_MODIFIER = b.defineInRange("improvedCoolingModifier", 3.0D, 1.0D, 1000.0D);
        ADVANCED_COOLING_MODIFIER = b.defineInRange("advancedCoolingModifier", 8.0D, 1.0D, 1000.0D);
        THERMAL_WARM_THRESHOLD = b.defineInRange("thermalWarmThreshold", 0.50D, 0.0D, 1.0D);
        THERMAL_HOT_THRESHOLD = b.defineInRange("thermalHotThreshold", 0.65D, 0.0D, 1.0D);
        THERMAL_CRITICAL_THRESHOLD = b.defineInRange("thermalCriticalThreshold", 0.85D, 0.0D, 1.0D);
        THERMAL_SHUTDOWN_THRESHOLD = b.defineInRange("thermalShutdownThreshold", 1.0D, 0.01D, 1.0D);
        THERMAL_RECOVERY_THRESHOLD = b.defineInRange("thermalRecoveryThreshold", 0.25D, 0.0D, 0.99D);
        ADVANCED_COOLING_MAX_TEMPERATURE = b.defineInRange("advancedCoolingMaxTemperature", 0.70D, 0.0D, 1.0D);
        THERMAL_COOLDOWN_TICKS = b.defineInRange("thermalCooldownTicks", 12000, 1, Integer.MAX_VALUE);
        b.pop();
        SPEC = b.build();
    }
    private FlightComputerConfig() {}
}
