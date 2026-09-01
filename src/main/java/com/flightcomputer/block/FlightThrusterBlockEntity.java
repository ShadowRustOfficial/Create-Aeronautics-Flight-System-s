package com.flightcomputer.block;

import com.flightcomputer.control.PropulsionSource;
import com.flightcomputer.control.PropulsionType;
import com.flightcomputer.control.VectorDirection;
import com.flightcomputer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/** Native flight-system actuator backed by Sable's queued propulsion force group. */
public final class FlightThrusterBlockEntity extends BlockEntity implements PropulsionSource {
    public static final double MAX_THRUST = 600_000.0D;
    private static final double MIN_COMMAND = 0.01D;
    private static final double MAX_RESPONSE_STEP = 0.12D;
    private static final double THRUST_UNITS_PER_KN = 1000.0D;

    private double throttle;
    private double appliedThrottle;
    private boolean enabled = true;
    private long lastAppliedTick = Long.MIN_VALUE;

    public FlightThrusterBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.FLIGHT_THRUSTER.get(), pos, state); }
    public Direction getFacing() { return getBlockState().getValue(FlightThrusterBlock.FACING); }
    public Direction getDirection() { return getFacing(); }
    @Override public String getId() { return "native:" + getBlockPos().asLong(); }
    @Override public PropulsionType getType() { return PropulsionType.GENERIC; }
    @Override public VectorDirection getDirection() {
        return switch (getFacing()) {
            case UP -> VectorDirection.UP;
            case DOWN -> VectorDirection.DOWN;
            case NORTH -> VectorDirection.NORTH;
            case SOUTH -> VectorDirection.SOUTH;
            case EAST -> VectorDirection.EAST;
            case WEST -> VectorDirection.WEST;
        };
    }
    @Override public double getMaxThrust() { return MAX_THRUST; }
    @Override public double getThrust() { return appliedThrottle * MAX_THRUST; }
    @Override public double getCurrentThrust() { return getThrust(); }
    @Override public double getThrottle() { return appliedThrottle; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isOperational() { return level != null; }
    @Override public boolean hasPower() { return true; }
    public boolean isCreative() { return true; }
    @Override public double getAvailableThrust() { return enabled && isOperational() ? MAX_THRUST : 0.0D; }
    @Override public double[] getForceDirection() {
        Direction d = getFacing();
        return new double[]{d.getStepX(), d.getStepY(), d.getStepZ()};
    }
    @Override public double[] getMountOffset() {
        return new double[]{getBlockPos().getX() + 0.5D, getBlockPos().getY() + 0.5D, getBlockPos().getZ() + 0.5D};
    }
    @Override public void applyThrust(double signedFraction) {
        setThrottle(Math.max(0.0D, signedFraction));
    }

    public void setThrottle(double value) { throttle = clamp(value, 0.0D, 1.0D); setChanged(); }
    public void setThrust(double value) { setThrottle(value / MAX_THRUST); }
    public void toggleEnabled() { enabled = !enabled; if (!enabled) throttle = 0.0D; setChanged(); }

    public void serverTick() {
        if (level == null || level.isClientSide() || lastAppliedTick == level.getGameTime()) return;
        lastAppliedTick = level.getGameTime();
        double target = enabled ? throttle : 0.0D;
        appliedThrottle += clamp(target - appliedThrottle, -MAX_RESPONSE_STEP, MAX_RESPONSE_STEP);
        if (Math.abs(appliedThrottle) < MIN_COMMAND) appliedThrottle = 0.0D;
    }

    public void applyPhysicsImpulse(Object subLevel, double timeStep) {
        if (subLevel == null || level == null || appliedThrottle <= 0.0D || !enabled) return;
        double thrust = appliedThrottle * MAX_THRUST;
        Direction d = getFacing();
        Vector3d impulse = new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ()).mul(thrust * timeStep / THRUST_UNITS_PER_KN);
        Vector3d point = new Vector3d(getBlockPos().getX() + 0.5D, getBlockPos().getY() + 0.5D, getBlockPos().getZ() + 0.5D);
        try {
            Object forceGroup = getOrCreatePropulsionForceGroup(subLevel);
            if (forceGroup == null) return;
            Method apply = forceGroup.getClass().getMethod("applyAndRecordPointForce", Vector3d.class, Vector3d.class);
            apply.invoke(forceGroup, point, impulse);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
    }

    private static Object getOrCreatePropulsionForceGroup(Object subLevel) {
        try {
            Class<?> forceGroups = Class.forName("dev.ryanhcode.sable.api.physics.force.ForceGroups", false, FlightThrusterBlockEntity.class.getClassLoader());
            Object holder = forceGroups.getField("PROPULSION").get(null);
            Object group = holder instanceof Supplier<?> supplier ? supplier.get() : holder.getClass().getMethod("get").invoke(holder);
            for (Method method : subLevel.getClass().getMethods()) {
                if (!method.getName().equals("getOrCreateQueuedForceGroup") || method.getParameterCount() != 1) continue;
                if (method.getParameterTypes()[0].isInstance(group)) return method.invoke(subLevel, group);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putDouble("Throttle", throttle); tag.putBoolean("Enabled", enabled);
    }
    @Override protected void loadAdditional(CompoundTag tag) {
        super.loadAdditional(tag); throttle = clamp(tag.getDouble("Throttle"), 0.0D, 1.0D); enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }
}
