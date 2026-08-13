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
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Runtime bridge for per-aircraft PID profiles. Calibration is never started implicitly. */
public final class AutoTuneRuntimeBridge {
    private AutoTuneRuntimeBridge() { }

    /** Apply a persisted profile only when its fingerprint still matches the live vehicle. */
    public static void tick(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        try {
            FlightControlRuntimeManager.Runtime runtime = FlightControlRuntimeManager.runtime(controller);
            VehicleState state = (VehicleState) field(runtime, "snapshot");
            FlightComputer computer = (FlightComputer) field(runtime, "computer");
            if (state == null || computer == null) return;
            RuntimeDynamicsProvider provider = new RuntimeDynamicsProvider(state, computer.getRegistry());
            long fingerprint = new PIDAutoTuner(controller.getControllerId(), provider).fingerprint();
            TuningResult saved = PIDAutoTuneStore.get(controller.getControllerId());
            if (saved != null && saved.fingerprint() == fingerprint) {
                computer.getStabilizeStabilizer().applyProfile(saved);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Profiles are optional. Never allow tuning/telemetry support to break flight control.
        }
    }

    public static void apply(FlightControllerBlockEntity controller, TuningResult result) {
        if (controller == null || result == null) return;
        try {
            FlightControlRuntimeManager.Runtime runtime = FlightControlRuntimeManager.runtime(controller);
            FlightComputer computer = (FlightComputer) field(runtime, "computer");
            if (computer != null) computer.getStabilizeStabilizer().applyProfile(result);
            PIDAutoTuneStore.put(controller.getControllerId(), result);
            controller.setChanged();
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    public static void remove(FlightControllerBlockEntity controller) { }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Adapter over the existing server snapshot and real linked thrusters. */
    public static final class RuntimeDynamicsProvider implements ShipDynamicsProvider {
        private final VehicleState state;
        private final ThrusterRegistry registry;
        private final List<Thruster> thrusters;

        public RuntimeDynamicsProvider(VehicleState state, ThrusterRegistry registry) {
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
                Vector3d r = t.mountOffset();
                Vector3d f = new Vector3d(t.direction()).mul(t.maxThrust());
                tx += Math.abs(r.y * f.z - r.z * f.y);
                ty += Math.abs(r.z * f.x - r.x * f.z);
                tz += Math.abs(r.x * f.y - r.y * f.x);
            }
            return new Vec3(
                    tx / Math.max(state.inertiaPitch, 1.0e-3),
                    ty / Math.max(state.inertiaYaw, 1.0e-3),
                    tz / Math.max(state.inertiaRoll, 1.0e-3));
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
                    result.add(new Thruster(link.source.getId(), new Vector3d(direction.x(), direction.y(), direction.z()), new Vector3d(link.source.getMountOffset()), link.source.getMaxThrust()));
                }
            }
            return List.copyOf(result);
        }
    }
}
