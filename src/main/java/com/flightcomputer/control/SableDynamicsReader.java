package com.flightcomputer.control;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Server-side reflective bridge to Sable's authoritative MassData and queued-force APIs.
 * Reflection is intentional so the controller remains resilient to minor Sable API movement.
 */
public final class SableDynamicsReader {
    private SableDynamicsReader() { }

    public static boolean readMassData(Object subLevel, VehicleState state, Vec3 controllerLocalCenter) {
        if (subLevel == null || state == null) return false;
        try {
            Method getMassTracker = findMethod(subLevel.getClass(), "getMassTracker");
            if (getMassTracker == null) return false;
            Object massData = getMassTracker.invoke(subLevel);
            if (massData == null) return false;

            boolean changed = false;
            Object mass = invokeNoArg(massData, "getMass");
            if (mass instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() > 0.0D) {
                state.mass = n.doubleValue();
                changed = true;
            }

            Object center = invokeNoArg(massData, "getCenterOfMass");
            if (center instanceof Vector3dc com) {
                double cx = com.x() - (controllerLocalCenter == null ? 0.0D : controllerLocalCenter.x);
                double cy = com.y() - (controllerLocalCenter == null ? 0.0D : controllerLocalCenter.y);
                double cz = com.z() - (controllerLocalCenter == null ? 0.0D : controllerLocalCenter.z);
                if (finite(cx) && finite(cy) && finite(cz)) {
                    state.comX = cx; state.comY = cy; state.comZ = cz;
                    changed = true;
                }
            }

            Object inertia = invokeNoArg(massData, "getInertiaTensor");
            if (inertia instanceof Matrix3dc matrix) {
                copyMatrix(matrix, state);
                state.inertiaPitch = Math.max(1.0e-3D, Math.abs(matrix.m00()));
                state.inertiaRoll = Math.max(1.0e-3D, Math.abs(matrix.m22()));
                state.inertiaYaw = Math.max(1.0e-3D, Math.abs(matrix.m11()));
                changed = true;
            }
            return changed;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Reads currently queued Sable force totals when available; returns world/body-local Sable units as supplied by Sable. */
    public static double[] queuedForceAndTorque(Object subLevel) {
        double[] result = new double[6];
        if (subLevel == null) return result;
        try {
            Method method = findMethod(subLevel.getClass(), "getQueuedForceGroups");
            if (method == null) return result;
            Object groups = method.invoke(subLevel);
            if (!(groups instanceof Map<?, ?> map)) return result;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object queued = entry.getValue();
                Object total = invokeNoArg(queued, "getForceTotal");
                if (total == null) continue;
                Object force = invokeNoArg(total, "getLocalForce");
                Object torque = invokeNoArg(total, "getLocalTorque");
                if (force instanceof Vector3dc f) {
                    result[0] += f.x(); result[1] += f.y(); result[2] += f.z();
                }
                if (torque instanceof Vector3dc t) {
                    result[3] += t.x(); result[4] += t.y(); result[5] += t.z();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return result;
    }

    private static void copyMatrix(Matrix3dc matrix, VehicleState state) {
        state.i00 = finite(matrix.m00()) ? matrix.m00() : 1.0D;
        state.i01 = finite(matrix.m01()) ? matrix.m01() : 0.0D;
        state.i02 = finite(matrix.m02()) ? matrix.m02() : 0.0D;
        state.i10 = finite(matrix.m10()) ? matrix.m10() : 0.0D;
        state.i11 = finite(matrix.m11()) ? matrix.m11() : 1.0D;
        state.i12 = finite(matrix.m12()) ? matrix.m12() : 0.0D;
        state.i20 = finite(matrix.m20()) ? matrix.m20() : 0.0D;
        state.i21 = finite(matrix.m21()) ? matrix.m21() : 0.0D;
        state.i22 = finite(matrix.m22()) ? matrix.m22() : 1.0D;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException ignored) { cursor = cursor.getSuperclass(); }
        }
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        Method method = findMethod(target.getClass(), name);
        if (method == null) return null;
        try { return method.invoke(target); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
