package com.flightcomputer.control;

import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Submits native flight-system actuator impulses after the guidance allocator has commanded them. */
public final class NativeThrusterPhysics {
    private static final Map<UUID, ThrusterRegistry> REGISTRIES = new HashMap<>();

    private NativeThrusterPhysics() { }

    public static synchronized void tick(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        Level level = controller.getLevel();
        Object subLevel = resolveSubLevel(controller);
        if (subLevel == null) return;

        ThrusterRegistry registry = REGISTRIES.computeIfAbsent(controller.getControllerId(), id -> new ThrusterRegistry());
        registry.refresh(level, controller.getBlockPos(), controller.getVectorLinks(FlightMode.STABILIZE), controller.getVectorLinks(FlightMode.CRUISE), level.getGameTime(), subLevel);
        for (ThrusterLink link : registry.getAllLinks()) {
            if (link == null || link.source == null) continue;
            link.source.applyPhysicsImpulse(subLevel, 1.0D / 20.0D);
        }
    }

    public static synchronized void remove(FlightControllerBlockEntity controller) {
        if (controller != null) REGISTRIES.remove(controller.getControllerId());
    }

    private static Object resolveSubLevel(FlightControllerBlockEntity controller) {
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false, NativeThrusterPhysics.class.getClassLoader());
            Object helper = sable.getField("INSTANCE").get(null);
            if (helper == null) return null;
            try {
                return helper.getClass().getMethod("getContaining", net.minecraft.world.level.block.entity.BlockEntity.class).invoke(helper, controller);
            } catch (NoSuchMethodException ignored) { }
            try {
                return helper.getClass().getMethod("getContaining", Level.class, net.minecraft.world.phys.Vec3.class)
                        .invoke(helper, controller.getLevel(), net.minecraft.world.phys.Vec3.atCenterOf(controller.getBlockPos()));
            } catch (NoSuchMethodException ignored) { }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return null;
    }
}
