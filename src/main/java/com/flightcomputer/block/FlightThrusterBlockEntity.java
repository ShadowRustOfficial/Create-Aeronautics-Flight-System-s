package com.flightcomputer.block;

import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.lang.reflect.Method;

/**
 * Native flight-system actuator. The controller allocator commands throttle; this block then
 * applies the corresponding physical force to the containing Sable vehicle at its mount point.
 */
public final class FlightThrusterBlockEntity extends BlockEntity {
    public static final double MAX_THRUST = 600.0D;
    private static final double MIN_COMMAND = 0.01D;
    private static final double MAX_RESPONSE_STEP = 0.12D;

    private double throttle;
    private double appliedThrottle;
    private boolean enabled = true;
    private long lastAppliedTick = Long.MIN_VALUE;

    public FlightThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLIGHT_THRUSTER.get(), pos, state);
    }

    public Direction getFacing() { return getBlockState().getValue(FlightThrusterBlock.FACING); }
    public Direction getDirection() { return getFacing(); }
    public double getMaxThrust() { return MAX_THRUST; }
    public double getThrust() { return appliedThrottle * MAX_THRUST; }
    public double getCurrentThrust() { return getThrust(); }
    public double getThrottle() { return appliedThrottle; }
    public boolean isEnabled() { return enabled; }
    public boolean isOperational() { return level != null; }
    public boolean hasPower() { return true; }
    public boolean isCreative() { return true; }
    public double getAvailableThrust() { return enabled && isOperational() ? MAX_THRUST : 0.0D; }
    public double[] getForceDirection() {
        Direction d = getFacing();
        return new double[]{d.getStepX(), d.getStepY(), d.getStepZ()};
    }
    public double[] getMountOffset() {
        if (level == null) return new double[]{0.0D, 0.0D, 0.0D};
        return new double[]{
                getBlockPos().getX() + 0.5D - (getBlockPos().getX() + 0.5D),
                getBlockPos().getY() + 0.5D - (getBlockPos().getY() + 0.5D),
                getBlockPos().getZ() + 0.5D - (getBlockPos().getZ() + 0.5D)
        };
    }

    /** Called by the allocator through the generic propulsion adapter. */
    public void setThrottle(double value) {
        throttle = clamp(value, 0.0D, 1.0D);
        setChanged();
    }

    public void setThrust(double value) { setThrottle(value / MAX_THRUST); }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) throttle = 0.0D;
        setChanged();
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || lastAppliedTick == level.getGameTime()) return;
        lastAppliedTick = level.getGameTime();
        double target = enabled ? throttle : 0.0D;
        // Give the actuator a small physical response time instead of an infinitely rigid control.
        double delta = clamp(target - appliedThrottle, -MAX_RESPONSE_STEP, MAX_RESPONSE_STEP);
        appliedThrottle += delta;
        if (Math.abs(appliedThrottle) < MIN_COMMAND) appliedThrottle = 0.0D;
        applyToContainingVehicle(appliedThrottle);
    }

    private void applyToContainingVehicle(double fraction) {
        if (fraction <= 0.0D || level == null) return;
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false, getClass().getClassLoader());
            Object helper = sable.getField("INSTANCE").get(null);
            if (helper == null) return;
            Object subLevel = null;
            try { subLevel = helper.getClass().getMethod("getContaining", BlockEntity.class).invoke(helper, this); }
            catch (ReflectiveOperationException ignored) { }
            if (subLevel == null) return;

            double thrust = fraction * MAX_THRUST;
            Direction d = getFacing();
            double x = d.getStepX() * thrust;
            double y = d.getStepY() * thrust;
            double z = d.getStepZ() * thrust;

            // Prefer the same body-space force path used by CC:VS-style flight control. Fallbacks
            // keep this integration tolerant of Sable API changes without making Sable a hard dep.
            if (invoke(subLevel, "applyRotDependentForce", x, y, z)) return;
            if (invoke(subLevel, "applyBodyForce", x, y, z)) return;
            if (invoke(subLevel, "applyForce", x, y, z)) return;
            invoke(subLevel, "addForce", x, y, z);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
    }

    private static boolean invoke(Object target, String name, double x, double y, double z) {
        try {
            Method method = target.getClass().getMethod(name, double.class, double.class, double.class);
            method.invoke(target, x, y, z);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Throttle", throttle);
        tag.putBoolean("Enabled", enabled);
    }

    @Override protected void loadAdditional(CompoundTag tag) {
        super.loadAdditional(tag);
        throttle = clamp(tag.getDouble("Throttle"), 0.0D, 1.0D);
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }
}
