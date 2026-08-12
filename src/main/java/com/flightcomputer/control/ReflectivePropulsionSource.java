package com.flightcomputer.control;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime adapter for propulsion mods that are intentionally not compile-time dependencies. */
public final class ReflectivePropulsionSource implements PropulsionSource {
    private static final double SIMULATED_STANDARD_MAX_THRUST = 600.0D;
    private static final String SIMULATED_THRUSTER = "dev.createpropulsionsimulated.content.thruster.ThrusterBlockEntity";
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
        boolean simulated = className.equals(SIMULATED_THRUSTER)
                || className.contains("createpropulsionsimulated")
                || blockId.contains("propulsion") || blockId.contains("thruster");
        boolean aeronautics = className.toLowerCase().contains("aeronautics") || blockId.contains("aeronautics");
        PropulsionType type = simulated ? PropulsionType.CREATE_PROPULSION_SIMULATED
                : aeronautics ? PropulsionType.AERONAUTICS : PropulsionType.GENERIC;
        return new ReflectivePropulsionSource(blockEntity, direction, mountOffset, accessor, type);
    }

    public VectorDirection getPhysicalDirection() {
        if (accessor.facing == null) return null;
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

    @Override public String getId() { return blockEntity.getBlockPos().asLong() + "@" + blockEntity.getLevel().dimension().location(); }
    @Override public PropulsionType getType() { return type; }
    @Override public VectorDirection getDirection() { return direction; }

    @Override public double getMaxThrust() {
        Object creative = invoke(accessor.creativeTargetThrust);
        if (creative instanceof Number number && number.doubleValue() > 0.0D) return number.doubleValue();
        Object max = invoke(accessor.maxThrust);
        if (max instanceof Number number && number.doubleValue() > 0.0D) return number.doubleValue();
        if (type == PropulsionType.CREATE_PROPULSION_SIMULATED) return SIMULATED_STANDARD_MAX_THRUST;
        Object current = invoke(accessor.currentThrust);
        if (current instanceof Number number && number.doubleValue() > 0.0D) return number.doubleValue();
        return 0.0D;
    }

    @Override public double getAvailableThrust() {
        if (!isEnabled() || !isOperational()) return 0.0D;

        // Create: Propulsion Simulated Creative Thrusters inherit the normal fuel accessors,
        // but their tank is intentionally disabled. The inherited getFuelAmountMb() therefore
        // returns zero even though the Creative Thruster is fully usable. Treat an explicit
        // isCreative() actuator as infinite-resource rather than gating it on the empty tank.
        if (!accessor.creative(blockEntity)) {
            if (accessor.fuelAmount != null && accessor.fuelCapacity != null) {
                Object amount = invoke(accessor.fuelAmount);
                Object capacity = invoke(accessor.fuelCapacity);
                if (amount instanceof Number a && capacity instanceof Number c && c.doubleValue() > 0.0D
                        && a.doubleValue() <= 0.0D) return 0.0D;
            } else if (accessor.redstonePower == null && accessor.thrustSetter == null
                    && accessor.throttleSetter == null && !hasPower()) {
                return 0.0D;
            }
        }
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
            if (amount instanceof Number a && capacity instanceof Number c && c.doubleValue() > 0.0D) return a.doubleValue() > 0.0D;
        }
        Object powered = invoke(accessor.powered);
        return !(powered instanceof Boolean bool) || bool;
    }

    @Override public double[] getMountOffset() { return mountOffset.clone(); }

    @Override public void applyThrust(double signedFraction) {
        if (!isEnabled() || !isOperational()) {
            invokeInt(accessor.redstonePower, 0);
            return;
        }

        if (!accessor.creative(blockEntity)
                && accessor.fuelAmount != null && accessor.fuelCapacity != null && !hasPower()) {
            invokeInt(accessor.redstonePower, 0);
            if (accessor.thrustSetter != null) invokeNumber(accessor.thrustSetter, 0.0D);
            else if (accessor.throttleSetter != null) invokeNumber(accessor.throttleSetter, 0.0D);
            return;
        }

        double fraction = Math.max(0.0D, Math.min(1.0D, signedFraction));
        if (accessor.redstonePower != null) {
            invokeInt(accessor.redstonePower, (int) Math.round(fraction * 15.0D));
            return;
        }
        if (accessor.thrustSetter != null) invokeNumber(accessor.thrustSetter, fraction * getMaxThrust());
        else if (accessor.throttleSetter != null) invokeNumber(accessor.throttleSetter, fraction);
    }

    private Object invoke(Method method) {
        if (method == null) return null;
        try { return method.invoke(blockEntity); } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
    private void invokeInt(Method method, int value) {
        if (method == null) return;
        try { method.invoke(blockEntity, value); } catch (ReflectiveOperationException | RuntimeException ignored) { }
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

    private static final class Accessor {
        final Method facing;
        final Method currentThrust;
        final Method creativeTargetThrust;
        final Method maxThrust;
        final Method throttle;
        final Method fuelAmount;
        final Method fuelCapacity;
        final Method enabled;
        final Method operational;
        final Method powered;
        final Method creativeMethod;
        final Method redstonePower;
        final Method thrustSetter;
        final Method throttleSetter;
        final boolean compatible;

        private Accessor(Class<?> type) {
            facing = findNoArg(type, "getFacing", "getDirection", "getPropulsionDirection");
            currentThrust = findNoArg(type, "getCurrentThrust", "getThrust", "getOutputThrust");
            creativeTargetThrust = findNoArg(type, "getCreativeTargetThrust");
            maxThrust = findNoArg(type, "getMaxThrust", "getMaximumThrust", "getThrustCapacity");
            throttle = findNoArg(type, "getThrottle", "getPower", "getOutputLevel");
            fuelAmount = findNoArg(type, "getFuelAmountMb", "getFuelAmount");
            fuelCapacity = findNoArg(type, "getFuelCapacityMb", "getFuelCapacity");
            enabled = findNoArg(type, "isEnabled");
            operational = findNoArg(type, "isOperational", "isFunctional");
            powered = findNoArg(type, "hasPower", "isPowered");
            creativeMethod = findNoArg(type, "isCreative");
            redstonePower = findSetter(type, "setRedstonePower");
            thrustSetter = findSetter(type, "setThrust", "setOutputThrust");
            throttleSetter = findSetter(type, "setThrottle", "setPower");
            compatible = currentThrust != null || creativeTargetThrust != null || maxThrust != null
                    || redstonePower != null || thrustSetter != null || throttleSetter != null;
        }

        boolean creative(BlockEntity blockEntity) {
            if (creativeMethod == null) return false;
            try { return Boolean.TRUE.equals(creativeMethod.invoke(blockEntity)); }
            catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
        }

        static Accessor inspect(Class<?> type) { return new Accessor(type); }
    }
}
