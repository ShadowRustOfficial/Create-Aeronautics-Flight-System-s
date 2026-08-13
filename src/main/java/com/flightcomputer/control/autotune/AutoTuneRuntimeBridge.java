package com.flightcomputer.control.autotune;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.ControlAxis;
import com.flightcomputer.control.FlightComputer;
import com.flightcomputer.control.FlightControlRuntimeManager;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.control.ThrusterLink;
import com.flightcomputer.control.ThrusterRegistry;
import com.flightcomputer.control.VectorDirection;
import com.flightcomputer.control.VehicleState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bridges the model-based tuner into the existing server runtime without replacing MPC/PID control. */
public final class AutoTuneRuntimeBridge {
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private AutoTuneRuntimeBridge() { }

    public static void tick(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        try {
            FlightControlRuntimeManager.Runtime runtime = FlightControlRuntimeManager.runtime(controller);
            VehicleState state = (VehicleState) field(runtime, "snapshot");
            FlightComputer computer = (FlightComputer) field(runtime, "computer");
            if (state == null || computer == null) return;
            ThrusterRegistry registry = computer.getRegistry();
            ShipDynamicsProvider provider = new RuntimeDynamicsProvider(state, registry);
            long fingerprint = new PIDAutoTuner(controller.getControllerId(), provider).fingerprint();

            Entry entry = ENTRIES.computeIfAbsent(controller.getControllerId(), id -> new Entry());
            if (entry.appliedFingerprint == fingerprint) return;

            TuningResult saved = PIDAutoTuneStore.get(controller.getControllerId());
            if (saved != null && saved.fingerprint() == fingerprint) {
                computer.getStabilizeStabilizer().applyProfile(saved);
                entry.appliedFingerprint = fingerprint;
                return;
            }

            if (entry.tuner == null || entry.tuner.fingerprint() != fingerprint || entry.tuner.isComplete()) {
                entry.tuner = new PIDAutoTuner(controller.getControllerId(), provider);
                entry.tuner.begin();
            }
            entry.tuner.tick();
            if (entry.tuner.state() == TuningState.COMPLETE && entry.tuner.result() != null) {
                computer.getStabilizeStabilizer().applyProfile(entry.tuner.result());
                entry.appliedFingerprint = entry.tuner.result().fingerprint();
                controller.setChanged();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Auto-tune is an enhancement. A failure must never take the Flight Computer control loop down.
        }
    }

    public static synchronized void remove(FlightControllerBlockEntity controller) {
        if (controller != null) ENTRIES.remove(controller.getControllerId());
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static final class Entry {
        private PIDAutoTuner tuner;
        private long appliedFingerprint = Long.MIN_VALUE;
    }

    private static final class RuntimeDynamicsProvider implements ShipDynamicsProvider {
        private final VehicleState state;
        private final ThrusterRegistry registry;
        private final List<Thruster> thrusters;

        private RuntimeDynamicsProvider(VehicleState state, ThrusterRegistry registry) {
            this.state = state;
            this.registry = registry;
            this.thrusters = collect(registry);
        }

        @Override public double getMass() { return Math.max(1.0e-3, state.mass); }
        @Override public Vec3 getCenterOfMass() { return Vec3.ZERO; }
        @Override public Vec3 getMaxLinearAcceleration() {
            double mass = getMass();
            return new Vec3(registry.getAxisAuthority(FlightMode.STABILIZE, ControlAxis.LATERAL) / mass,
                    registry.getAxisAuthority(FlightMode.STABILIZE, ControlAxis.VERTICAL) / mass,
                    registry.getAxisAuthority(FlightMode.STABILIZE, ControlAxis.LONGITUDINAL) / mass);
        }
        @Override public Vec3 getMaxAngularAcceleration() {
            double tx = 0, ty = 0, tz = 0;
            for (Thruster t : thrusters) {
                Vec3 r = t.mountOffset();
                Vec3 f = t.direction().mul(t.maxThrust());
                tx += Math.abs(r.y * f.z - r.z * f.y);
                ty += Math.abs(r.z * f.x - r.x * f.z);
                tz += Math.abs(r.x * f.y - r.y * f.x);
            }
            return new Vec3(tx / state.inertiaPitch, ty / state.inertiaYaw, tz / state.inertiaRoll);
        }
        @Override public InertiaTensor getInertiaTensor() { return new InertiaTensor(state.inertiaPitch, state.inertiaRoll, state.inertiaYaw); }
        @Override public List<Thruster> getThrusters() { return thrusters; }
        @Override public Vec3 getVelocity() { return new Vec3(state.vx, state.vy, state.vz); }
        @Override public Vec3 getAngularVelocity() { return new Vec3(state.pitchRate, state.yawRate, state.rollRate); }
        @Override public Vec3 getOrientationError() { return Vec3.ZERO; }

        private static List<Thruster> collect(ThrusterRegistry registry) {
            List<Thruster> result = new ArrayList<>();
            for (VectorDirection direction : VectorDirection.values()) {
                for (ThrusterLink link : registry.getLinks(FlightMode.STABILIZE, direction)) {
                    result.add(new Thruster(link.source.getId(), new Vector3d(direction.x(), direction.y(), direction.z()),
                            new Vector3d(link.source.getMountOffset()), link.source.getMaxThrust()));
                }
            }
            return List.copyOf(result);
        }
    }
}
