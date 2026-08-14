package com.flightcomputer.control;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime adapter for propulsion mods that are intentionally not compile-time dependencies. */
public final class ReflectivePropulsionSource implements PropulsionSource {
    private static final double SIMULATED_STANDARD_MAX_THRUST = 600.0D;
    private static final double SIMULATED_CREATIVE_MAX_THRUST_FALLBACK = 10000.0D;
    private static final String SIMULATED_THRUSTER_PACKAGE = "dev.createpropulsionsimulated.content.thruster.";
    private static final Map<Class<?>, Accessor> ACCESSORS = new ConcurrentHashMap<>();

    private final BlockEntity blockEntity;
    private final VectorDirection direction;
    private final double[] mountOffset;
    private final Accessor accessor;
    private final PropulsionType type;

    private ReflectivePropulsionSource(BlockEntity blockEntity, VectorDirection direction, double[] mountOffset,
                                       Accessor accessor, PropulsionType type) {
        this.blockEntity = blockEntity;
        this.direction = direction;
        this.mountOffset = mountOffset.clone();
        this.accessor = accessor;
        this.type = type;
    }

    public static PropulsionSource tryCreate(BlockEntity blockEntity, VectorDirection direction, double[] mountOffset) {
        if (blockEntity == null || direction == null) return null;
        Accessor accessor = ACCESSORS.computeIfAbsent(blockEntity.getClass(), Accessor::inspect);
        if (!accessor.compatible) return null;

        String className = blockEntity.getClass().getName();
        String blockId = String.valueOf(blockEntity.getBlockState().getBlock().builtInRegistryHolder().key().location());
        boolean simulated = className.startsWith(SIMULATED_THRUSTER_PACKAGE)
                || blockId.contains("createpropulsionsimulated")
                || blockId.contains("propulsion")
                || blockId.contains("thruster");
        boolean aeronautics = className.toLowerCase().contains("aeronautics") || blockId.contains("aeronautics");
        PropulsionType type = simulated ? PropulsionType.CREATE_PROPULSION_SIMULATED
                : aeronautics ? PropulsionType.AERONAUTICS : PropulsionType.GENERIC;
        return new ReflectivePropulsionSource(blockEntity, direction, mountOffset, accessor, type);
    }

    public VectorDirection getPhysicalDirection() {
        Object value = invoke(accessor.facing);
        if (!(value instanceof Direction facing)) return null;
        return switch (facing) {
            case NORTH -> VectorDirection.NORTH;
            case SOUTH -> VectorDirection.SOUTH;
            case EAST -> VectorDirection.EAST;
            case WEST -> VectorDirection.WEST;
            case UP -> VectorDirection.UP;
            case DOWN -> VectorDirection.DOWN;
        };
    }

    @Override public String getId() {
        return blockEntity.getBlockPos().asLong() + "@" + blockEntity.getLevel().dimension().location();
    }

    @Override public PropulsionType getType() { return type; }
    @Override public VectorDirection getDirection() { return direction; }

    @Override public double getMaxThrust() {
        if (accessor.creative(blockEntity)) {
            double configuredMaximum = readCreativeMaximumThrust();
            return configuredMaximum > 0.0D ? configuredMaximum : SIMULATED_CREATIVE_MAX_THRUST_FALLBACK;
        }

        Object max = invoke(accessor.maxThrust);
        if (max instanceof Number number && number.doubleValue() > 0.0D) return number.doubleValue();
        if (type == PropulsionType.CREATE_PROPULSION_SIMULATED) return SIMULATED_STANDARD_MAX_THRUST;

        Object current = invoke(accessor.currentThrust);
        if (current instanceof Number number && number.doubleValue() > 0.0D) return number.doubleValue();
        return 0.0D;
    }

    @Override public double getAvailableThrust() {
        if (!isEnabled() || !isOperational()) return 0.0D;

        // IMPORTANT: redstone/control input is the command we are trying to generate, not a
        // prerequisite for actuator discovery. Treating getThrottle()==0 as unavailable creates
        // a deadlock where the allocator can never command a newly discovered thruster.
        if (!accessor.creative(blockEntity) && accessor.requiresFuel(blockEntity) && !hasPower()) return 0.0D;
        return Math.max(0.0D, getMaxThrust());
    }

    @Override public double getCurrentThrust() {
        Object value = invoke(accessor.currentThrust);
        return value instanceof Number number ? Math.max(0.0D, number.doubleValue()) : 0.0D;
    }

    @Override public boolean isEnabled() {
        Object value = invoke(accessor.enabled);
        return !(value instanceof Boolean bool) || bool;
    }

    @Override public boolean isOperational() {
        Object value = invoke(accessor.operational);
        return !(value instanceof Boolean bool) || bool;
    }

    @Override public boolean hasPower() {
        if (accessor.creative(blockEntity)) return true;
        if (accessor.fuelAmount != null && accessor.fuelCapacity != null) {
            Object amount = invoke(accessor.fuelAmount);
            Object capacity = invoke(accessor.fuelCapacity);
            if (amount instanceof Number a && capacity instanceof Number c && c.doubleValue() > 0.0D) {
                return a.doubleValue() > 0.0D;
            }
        }
        Object powered = invoke(accessor.powered);
        return !(powered instanceof Boolean bool) || bool;
    }

    @Override public double[] getMountOffset() { return mountOffset.clone(); }

    @Override public void applyThrust(double signedFraction) {
        if (!isEnabled() || !isOperational()) {
            invokeInt(accessor.redstonePower, 0);
            invokeNumber(accessor.digitalInput, 0.0D);
            invokeNumber(accessor.thrustSetter, 0.0D);
            invokeNumber(accessor.throttleSetter, 0.0D);
            return;
        }

        double fraction = Math.max(0.0D, Math.min(1.0D, signedFraction));

        if (!accessor.creative(blockEntity)
                && accessor.fuelAmount != null
                && accessor.fuelCapacity != null
                && fraction > 0.0D
                && !hasPower()) {
            invokeInt(accessor.redstonePower, 0);
            invokeNumber(accessor.digitalInput, 0.0D);
            invokeNumber(accessor.thrustSetter, 0.0D);
            invokeNumber(accessor.throttleSetter, 0.0D);
            return;
        }

        // Create Propulsion: Simulated standard thrusters use redstone input as their throttle.
        if (accessor.redstonePower != null) {
            invokeInt(accessor.redstonePower, (int) Math.round(fraction * 15.0D));
        }
        if (accessor.digitalInput != null) {
            invokeNumber(accessor.digitalInput, fraction);
        }
        if (accessor.thrustSetter != null) {
            invokeNumber(accessor.thrustSetter, fraction * getMaxThrust());
        } else if (accessor.throttleSetter != null) {
            invokeNumber(accessor.throttleSetter, fraction);
        }

        // Creative Propulsion: Simulated has no thrust setter on the block entity. Its actual
        // configured pN value is held by CreativeThrusterPowerScrollValueBehaviour.
        if (accessor.creative(blockEntity)) {
            setCreativeTargetThrust(fraction * getMaxThrust());
            if (accessor.redstonePower != null) {
                invokeInt(accessor.redstonePower, fraction <= 0.0D ? 0 : 15);
            }
        }
    }

    private double readCreativeMaximumThrust() {
        Object behaviour = readField(accessor.creativePowerBehaviour, blockEntity);
        if (behaviour == null) return SIMULATED_CREATIVE_MAX_THRUST_FALLBACK;
        Object maximum = readField(accessor.behaviourMaxThrust, behaviour);
        return maximum instanceof Number number ? Math.max(10.0D, number.doubleValue()) : SIMULATED_CREATIVE_MAX_THRUST_FALLBACK;
    }

    private void setCreativeTargetThrust(double targetThrust) {
        Object behaviour = readField(accessor.creativePowerBehaviour, blockEntity);
        if (behaviour == null || accessor.setTargetThrust == null) return;
        int maximum = (int) Math.round(readCreativeMaximumThrust());
        int clamped = (int) Math.round(Math.max(1.0D, Math.min(maximum, targetThrust)));
        try {
            accessor.setTargetThrust.invoke(behaviour, clamped);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static Object readField(Field field, Object instance) {
        if (field == null || instance == null) return null;
        try { return field.get(instance); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private Object invoke(Method method) {
        if (method == null) return null;
        try { return method.invoke(blockEntity); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private void invokeInt(Method method, int value) {
        if (method == null) return;
        try { method.invoke(blockEntity, value); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private void invokeNumber(Method method, double value) {
        if (method == null) return;
        try {
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter == float.class || parameter == Float.class) method.invoke(blockEntity, (float) value);
            else if (parameter == int.class || parameter == Integer.class) method.invoke(blockEntity, (int) Math.round(value));
            else method.invoke(blockEntity, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static Method findNoArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers())) return method;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static Method findSetter(Class<?> type, String... names) {
        for (String name : names) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1 && !Modifier.isStatic(method.getModifiers())) return method;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethodOnFieldType(Class<?> type, String fieldName, String methodName, Class<?> parameterType) {
        Field field = findField(type, fieldName);
        if (field == null) return null;
        try {
            Method method = field.getType().getMethod(methodName, parameterType);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static final class Accessor {
        final Method facing;
        final Method currentThrust;
        final Method maxThrust;
        final Method throttle;
        final Method fuelAmount;
        final Method fuelCapacity;
        final Method enabled;
        final Method operational;
        final Method powered;
        final Method creativeMethod;
        final Method redstonePower;
        final Method digitalInput;
        final Method thrustSetter;
        final Method throttleSetter;
        final Field creativePowerBehaviour;
        final Field behaviourMaxThrust;
        final Method setTargetThrust;
        final boolean compatible;

        private Accessor(Class<?> type) {
            facing = findNoArg(type, "getFacing", "getDirection", "getPropulsionDirection");
            currentThrust = findNoArg(type, "getCurrentThrust", "getThrust", "getOutputThrust");
            maxThrust = findNoArg(type, "getMaxThrust", "getMaximumThrust", "getThrustCapacity");
            throttle = findNoArg(type, "getThrottle", "getPower", "getOutputLevel");
            fuelAmount = findNoArg(type, "getFuelAmountMb", "getFuelAmount");
            fuelCapacity = findNoArg(type, "getFuelCapacityMb", "getFuelCapacity");
            enabled = findNoArg(type, "isEnabled");
            operational = findNoArg(type, "isOperational", "isFunctional");
            powered = findNoArg(type, "hasPower", "isPowered");
            creativeMethod = findNoArg(type, "isCreative");
            redstonePower = findSetter(type, "setRedstonePower", "setRedstoneInput");
            digitalInput = findSetter(type, "setDigitalInput");
            thrustSetter = findSetter(type, "setThrust", "setOutputThrust");
            throttleSetter = findSetter(type, "setThrottle", "setPower");
            creativePowerBehaviour = findField(type, "creativePowerBehaviour");
            behaviourMaxThrust = creativePowerBehaviour == null ? null : findField(creativePowerBehaviour.getType(), "maxThrust");
            setTargetThrust = findMethodOnFieldType(type, "creativePowerBehaviour", "setTargetThrust", int.class);
            compatible = facing != null && (currentThrust != null || maxThrust != null || redstonePower != null || digitalInput != null
                    || thrustSetter != null || throttleSetter != null);
        }

        boolean creative(BlockEntity blockEntity) {
            if (creativeMethod == null) return false;
            try { return Boolean.TRUE.equals(creativeMethod.invoke(blockEntity)); }
            catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
        }

        boolean requiresFuel(BlockEntity blockEntity) {
            if (creative(blockEntity)) return false;
            return fuelAmount != null && fuelCapacity != null;
        }

        static Accessor inspect(Class<?> type) { return new Accessor(type); }
    }
}
