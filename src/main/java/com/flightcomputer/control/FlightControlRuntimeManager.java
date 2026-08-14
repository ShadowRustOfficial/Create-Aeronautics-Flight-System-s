package com.flightcomputer.control;

import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.network.FlightSetupTelemetryNetwork;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative bridge between Sable state, MPC/PID guidance and physical thruster banks. */
public final class FlightControlRuntimeManager {
    private FlightControlRuntimeManager() { }
    private static final Map<UUID, Runtime> RUNTIMES = new HashMap<>();

    public static synchronized Runtime runtime(FlightControllerBlockEntity controller) {
        return RUNTIMES.computeIfAbsent(controller.getControllerId(), id -> new Runtime());
    }

    public static void tick(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        Runtime runtime = runtime(controller);
        FlightOperationsRuntimeBridge.reconcile(controller);
        runtime.update(controller);
        runtime.control(controller);
    }

    public static void setTarget(FlightControllerBlockEntity controller, Vec3 target, String name) {
        if (controller == null || target == null || !finite(target)) return;
        Runtime runtime = runtime(controller);
        runtime.target = target;
        runtime.targetName = name == null || name.isBlank() ? "NAVIGATION TARGET" : name.trim();
        runtime.targetActive = true;
    }

    public static void clearTarget(FlightControllerBlockEntity controller) {
        if (controller == null) return;
        Runtime runtime = runtime(controller);
        runtime.target = null;
        runtime.targetName = "";
        runtime.targetActive = false;
        runtime.lastNavigatorTarget = null;
    }

    /** Sets an explicit world Y level used by the altitude-hold PID. */
    public static void setAltitudeHoldTarget(FlightControllerBlockEntity controller, double y) {
        if (controller == null || !Double.isFinite(y)) return;
        Runtime runtime = runtime(controller);
        runtime.altitudeHoldTargetY = y;
        if (runtime.computer != null) runtime.computer.setAltitudeHoldTarget(y);
    }

    public static double altitudeHoldTarget(FlightControllerBlockEntity controller) {
        return runtime(controller).altitudeHoldTargetY;
    }

    public static Vec3 target(FlightControllerBlockEntity controller) { return runtime(controller).target; }
    public static String targetName(FlightControllerBlockEntity controller) { return runtime(controller).targetName; }
    public static boolean hasTarget(FlightControllerBlockEntity controller) {
        Runtime runtime = runtime(controller);
        return runtime.targetActive && runtime.target != null;
    }

    public static void sendTelemetry(FlightControllerBlockEntity controller) {
        if (controller == null || !(controller.getLevel() instanceof ServerLevel level)) return;
        Runtime runtime = runtime(controller);
        VehicleState state = runtime.snapshot;
        if (state == null) return;

        Vec3 current = new Vec3(state.x, state.y, state.z);
        Vec3 target = runtime.targetActive ? runtime.target : null;
        double distance = target == null ? -1.0D : target.distanceTo(current);
        double heading = normalizeDegrees(Math.toDegrees(state.bodyBackYaw()));
        double speed = safeSpeed(state.vx, state.vy, state.vz);

        ThrusterRegistry registry = runtime.computer == null ? null : runtime.computer.getRegistry();
        double[] stabiliser = vectorOutputs(registry, FlightMode.STABILIZE);
        double[] autopilot = vectorOutputs(registry, FlightMode.CRUISE);

        FlightComputerNetwork.TelemetryPayload payload = new FlightComputerNetwork.TelemetryPayload(
                controller.getControllerId(), state.x, state.y, state.z, speed, heading,
                Math.toDegrees(state.pitch), Math.toDegrees(state.roll),
                target != null, target == null ? 0.0D : target.x, target == null ? 0.0D : target.y,
                target == null ? 0.0D : target.z, runtime.targetName, distance,
                controller.getTemperature(), controller.getMaxTemperature(), controller.getThermalState().ordinal(),
                controller.getThermalCooldownTicksRemaining(), controller.getEnergyStorage().getEnergyStored(),
                controller.getEnergyStorage().getMaxEnergyStored(), controller.getCoolingTier().ordinal(),
                stabiliser[0], stabiliser[1], stabiliser[2], stabiliser[3], stabiliser[4], stabiliser[5],
                autopilot[0], autopilot[1], autopilot[2], autopilot[3], autopilot[4], autopilot[5]);

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(controller.getBlockPos().getX() + 0.5D,
                    controller.getBlockPos().getY() + 0.5D,
                    controller.getBlockPos().getZ() + 0.5D) <= 128.0D * 128.0D)
                FlightComputerNetwork.sendTelemetry(player, payload);
        }
    }

    private static double[] vectorOutputs(ThrusterRegistry registry, FlightMode mode) {
        double[] result = new double[6];
        if (registry == null) return result;
        VectorDirection[] dirs = VectorDirection.values();
        for (int i = 0; i < dirs.length; i++) {
            double max = 0.0D, current = 0.0D;
            for (ThrusterLink link : registry.getLinks(mode, dirs[i])) {
                max += Math.max(0.0D, link.source.getMaxThrust());
                current += Math.max(0.0D, link.source.getCurrentThrust());
            }
            result[i] = max <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, current / max));
        }
        return result;
    }

    public static synchronized void remove(FlightControllerBlockEntity controller) {
        if (controller != null) RUNTIMES.remove(controller.getControllerId());
    }

    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static boolean finite(double value) { return Double.isFinite(value); }
    private static double safeSpeed(double x, double y, double z) {
        double speed = Math.sqrt(x * x + y * y + z * z);
        return Double.isFinite(speed) ? speed : 0.0D;
    }
    private static double normalizeDegrees(double degrees) { double v = degrees % 360.0D; return v < 0 ? v + 360.0D : v; }

    public static final class Runtime {
        private VehicleState snapshot;
        private Vec3 previousPosition;
        private double previousYaw, previousPitch, previousRoll;
        private double filteredExternalForceX, filteredExternalForceY, filteredExternalForceZ;
        private double filteredExternalTorqueX, filteredExternalTorqueY, filteredExternalTorqueZ;
        private boolean externalForceInitialised;
        private Vec3 target;
        private String targetName = "";
        private boolean targetActive;
        private double altitudeHoldTargetY = Double.NaN;
        private FlightComputer computer;
        private Vec3 lastNavigatorTarget;
        private Object helper;
        private Method getContainingBlockEntity, getContainingPosition, projectOut;
        private boolean initialized, available;
        private int setupTelemetryTicker;

        public void update(FlightControllerBlockEntity controller) {
            if (controller == null || controller.getLevel() == null) return;
            Level level = controller.getLevel();
            Vec3 local = Vec3.atCenterOf(controller.getBlockPos());

            VehicleState previousSnapshot = snapshot == null ? null : snapshot.copy();
            VehicleState state = snapshot == null ? new VehicleState() : snapshot.copy();

            Object subLevel = containing(level, controller, local);
            Vec3 world = project(subLevel, level, local);
            state.x = world.x; state.y = world.y; state.z = world.z;
            state.bodyBackYawOffset = controllerBackYawOffset(controller);

            boolean physicalVelocity = false;
            boolean physicalInertia = false;
            if (subLevel != null) try {
                Object pose = subLevel.getClass().getMethod("logicalPose").invoke(subLevel);
                Object orientation = pose.getClass().getMethod("orientation").invoke(pose);
                if (orientation instanceof Quaterniondc q) {
                    double w=q.w(), x=q.x(), y=q.y(), z=q.z();
                    state.yaw=Math.atan2(2*(w*y+x*z),1-2*(y*y+z*z));
                    state.pitch=Math.asin(Math.max(-1,Math.min(1,2*(w*x-y*z))));
                    state.roll=Math.atan2(2*(w*z+x*y),1-2*(x*x+y*y));
                }

                Vector3d linear = readVectorField(subLevel, "latestLinearVelocity");
                Vector3d angular = readVectorField(subLevel, "latestAngularVelocity");
                if (linear != null && finite(linear)) {
                    state.vx=linear.x; state.vy=linear.y; state.vz=linear.z;
                    physicalVelocity=true;
                }
                if (angular != null && finite(angular)) {
                    state.pitchRate=angular.x; state.yawRate=angular.y; state.rollRate=angular.z;
                }

                Object tracker=invokeNoArg(subLevel,"getMassTracker","massTracker");
                Object mass=tracker==null?null:invokeNoArg(tracker,"getMass","mass");
                if(mass instanceof Number n&&n.doubleValue()>0)state.mass=n.doubleValue();
                double[] inertia=readInertia(tracker); if(inertia==null)inertia=readInertia(subLevel);
                if(inertia!=null){state.inertiaPitch=Math.max(1,inertia[0]);state.inertiaRoll=Math.max(1,inertia[1]);state.inertiaYaw=Math.max(1,inertia[2]);physicalInertia=true;}
                double radius=readDouble(subLevel,"getBoundingRadius","boundingRadius","getRadius"); if(radius>0)state.boundingRadius=Math.max(1,radius);
                double halfHeight=readDouble(subLevel,"getBoundingHalfHeight","boundingHalfHeight","getHalfHeight"); if(halfHeight>0)state.boundingHalfHeight=Math.max(1,halfHeight);
            } catch (ReflectiveOperationException|RuntimeException ignored) { }

            // Sable's actual MassData is authoritative. This provides the merged mass, COM and
            // full inertia tensor, including merged/kinematic masses, without compile-time Sable imports.
            if (subLevel != null && SableDynamicsReader.readMassData(subLevel, state, local)) {
                physicalInertia = true;
            }

            if(!physicalVelocity&&previousPosition!=null){
                state.vx=(world.x-previousPosition.x)*20;
                state.vy=(world.y-previousPosition.y)*20;
                state.vz=(world.z-previousPosition.z)*20;
            } else if(!physicalVelocity){state.vx=state.vy=state.vz=0;}
            if(!physicalInertia)estimateInertiaFromVehicleEnvelope(state,computer==null?null:computer.getRegistry());
            if(!physicalVelocity&&previousPosition!=null){
                state.yawRate=angleDelta(state.yaw,previousYaw)*20;
                state.pitchRate=angleDelta(state.pitch,previousPitch)*20;
                state.rollRate=angleDelta(state.roll,previousRoll)*20;
            }

            // Derive acceleration from the authoritative Sable velocity stream. Subtract the
            // previous controller-applied wrench so the remainder represents Gravity, Drag, Lift,
            // Levitation, Balloon Lift, Magnetic, Impact/Recoil and other non-controller forces.
            if (previousSnapshot != null) {
                state.ax = finite((state.vx - previousSnapshot.vx) * 20.0D) ? (state.vx - previousSnapshot.vx) * 20.0D : 0.0D;
                state.ay = finite((state.vy - previousSnapshot.vy) * 20.0D) ? (state.vy - previousSnapshot.vy) * 20.0D : 0.0D;
                state.az = finite((state.vz - previousSnapshot.vz) * 20.0D) ? (state.vz - previousSnapshot.vz) * 20.0D : 0.0D;

                double commandedFx = computer == null ? 0.0D : computer.getAllocator().getLastWorldForceX();
                double commandedFy = computer == null ? 0.0D : computer.getAllocator().getLastWorldForceY();
                double commandedFz = computer == null ? 0.0D : computer.getAllocator().getLastWorldForceZ();
                double commandedTx = computer == null ? 0.0D : computer.getAllocator().getLastWorldTorqueX();
                double commandedTy = computer == null ? 0.0D : computer.getAllocator().getLastWorldTorqueY();
                double commandedTz = computer == null ? 0.0D : computer.getAllocator().getLastWorldTorqueZ();

                double externalFx = state.mass * state.ax - commandedFx;
                double externalFy = state.mass * state.ay - commandedFy;
                double externalFz = state.mass * state.az - commandedFz;

                double alphaX = (state.pitchRate - previousSnapshot.pitchRate) * 20.0D;
                double alphaY = (state.yawRate - previousSnapshot.yawRate) * 20.0D;
                double alphaZ = (state.rollRate - previousSnapshot.rollRate) * 20.0D;
                Vector3d totalBodyTorque = state.bodyTorqueForAngularAcceleration(alphaX, alphaY, alphaZ);
                Vector3d commandedBodyTorque = new Vector3d(commandedTx, commandedTy, commandedTz);
                new org.joml.Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll).conjugate().transform(commandedBodyTorque);
                Vector3d externalBodyTorque = totalBodyTorque.sub(commandedBodyTorque);
                Vector3d externalWorldTorque = new org.joml.Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll).transform(externalBodyTorque);

                final double alpha = 0.18D;
                if (!externalForceInitialised) {
                    filteredExternalForceX=externalFx; filteredExternalForceY=externalFy; filteredExternalForceZ=externalFz;
                    filteredExternalTorqueX=externalWorldTorque.x; filteredExternalTorqueY=externalWorldTorque.y; filteredExternalTorqueZ=externalWorldTorque.z;
                    externalForceInitialised=true;
                } else {
                    filteredExternalForceX += alpha * (externalFx - filteredExternalForceX);
                    filteredExternalForceY += alpha * (externalFy - filteredExternalForceY);
                    filteredExternalForceZ += alpha * (externalFz - filteredExternalForceZ);
                    filteredExternalTorqueX += alpha * (externalWorldTorque.x - filteredExternalTorqueX);
                    filteredExternalTorqueY += alpha * (externalWorldTorque.y - filteredExternalTorqueY);
                    filteredExternalTorqueZ += alpha * (externalWorldTorque.z - filteredExternalTorqueZ);
                }
                state.externalForceX=filteredExternalForceX; state.externalForceY=filteredExternalForceY; state.externalForceZ=filteredExternalForceZ;
                state.externalTorqueX=filteredExternalTorqueX; state.externalTorqueY=filteredExternalTorqueY; state.externalTorqueZ=filteredExternalTorqueZ;
            }

            state.timestampNanos=System.nanoTime();
            previousPosition=world; previousYaw=state.yaw; previousPitch=state.pitch; previousRoll=state.roll;
            snapshot=state.copy();
        }

        private void control(FlightControllerBlockEntity controller) {
            if(controller==null||controller.getLevel()==null||controller.getLevel().isClientSide()||snapshot==null)return;
            if(computer==null)computer=new FlightComputer(()->snapshot);
            computer.setAltitudeHoldTarget(altitudeHoldTargetY);
            ThrusterRegistry registry=computer.getRegistry();
            registry.refresh(controller.getLevel(),controller.getBlockPos(),controller.getVectorLinks(FlightMode.STABILIZE),controller.getVectorLinks(FlightMode.CRUISE),controller.getLevel().getGameTime());
            if(targetActive&&target!=null){if(lastNavigatorTarget==null||!target.equals(lastNavigatorTarget)){computer.getNavigator().setTarget(target.x,target.y,target.z);lastNavigatorTarget=target;}}
            else if(computer.getNavigator().hasTarget()){computer.getNavigator().clearTarget();lastNavigatorTarget=null;}
            FlightControllerState state=controller.getControllerState();
            boolean powered=controller.isEngaged()&&!controller.isThermalLockout()&&controller.getEnergyStorage().getEnergyStored()>0;
            boolean stabiliserEnabled=powered&&controller.isStabiliser();
            boolean autopilotEnabled=powered&&state.flightMode()==com.flightcomputer.avionics.FlightMode.AUTOPILOT&&targetActive&&target!=null;
            computer.syncHolds(state, snapshot);
            computer.tick(1.0D/20.0D,stabiliserEnabled,autopilotEnabled);
            if (powered && ++setupTelemetryTicker >= 5) {
                setupTelemetryTicker = 0;
                FlightSetupTelemetryNetwork.send(controller, snapshot, registry);
            }
            if(powered)controller.addControlThermalLoad(computer.getAllocator().getLastThermalLoad());
        }

        private static double controllerBackYawOffset(FlightControllerBlockEntity controller){
            try {
                Direction facing = controller.getBlockState().getValue(FlightControllerBlock.FACING);
                Direction back = facing.getOpposite();
                return Math.atan2(back.getStepX(), back.getStepZ());
            } catch (RuntimeException ignored) {
                return Math.PI;
            }
        }

        private static double[] readInertia(Object target){if(target==null)return null;for(String n:new String[]{"getMomentOfInertia","getInertia","momentOfInertia","inertia"}){double[] p=parseVector(invokeNoArg(target,n));if(p!=null)return p;}return null;}
        private static double[] parseVector(Object v){if(v instanceof Vector3d x)return new double[]{Math.abs(x.x),Math.abs(x.y),Math.abs(x.z)};if(v instanceof Vec3 x)return new double[]{Math.abs(x.x),Math.abs(x.y),Math.abs(x.z)};if(v instanceof double[] a&&a.length>=3)return new double[]{Math.abs(a[0]),Math.abs(a[1]),Math.abs(a[2])};return null;}
        private static double readDouble(Object target,String...names){if(target==null)return -1;for(String n:names){Object v=invokeNoArg(target,n);if(v instanceof Number x&&x.doubleValue()>0)return x.doubleValue();}return -1;}

        private Vec3 project(Object subLevel, Level level, Vec3 local) {
            if (subLevel != null) {
                try {
                    Object pose = subLevel.getClass().getMethod("logicalPose").invoke(subLevel);
                    Object result = pose.getClass().getMethod("transformPosition", Vec3.class).invoke(pose, local);
                    if (result instanceof Vec3 vec) return vec;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (ensure()) {
                try { Object result=projectOut.invoke(helper,level,local); if(result instanceof Vec3 vec)return vec; }
                catch (ReflectiveOperationException|RuntimeException ignored) { }
            }
            return local;
        }

        private Object containing(Level level, FlightControllerBlockEntity controller, Vec3 local) {
            if (!ensure()) return null;
            try {
                if (getContainingBlockEntity != null) {
                    Object result=getContainingBlockEntity.invoke(helper,controller);
                    if(result!=null)return result;
                }
                if (getContainingPosition != null) return getContainingPosition.invoke(helper,level,local);
            } catch (ReflectiveOperationException|RuntimeException ignored) { }
            return null;
        }

        private boolean ensure(){
            if(initialized)return available;
            initialized=true;
            try{
                Class<?> sable=Class.forName("dev.ryanhcode.sable.companion.SableCompanion",false,getClass().getClassLoader());
                helper=sable.getField("INSTANCE").get(null);
                if(helper==null)throw new IllegalStateException("SableCompanion.INSTANCE is null");
                try { getContainingBlockEntity=helper.getClass().getMethod("getContaining",BlockEntity.class); }
                catch (ReflectiveOperationException ignored) { getContainingBlockEntity=null; }
                try { getContainingPosition=helper.getClass().getMethod("getContaining",Level.class,Vec3.class); }
                catch (ReflectiveOperationException ignored) { getContainingPosition=null; }
                try { projectOut=helper.getClass().getMethod("projectOutOfSubLevel",Level.class,Vec3.class); }
                catch (ReflectiveOperationException ignored) { projectOut=null; }
                available=getContainingBlockEntity!=null||getContainingPosition!=null||projectOut!=null;
            }catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){available=false;}
            return available;
        }

        private static Object invokeNoArg(Object target,String...names){if(target==null)return null;for(String n:names)try{return target.getClass().getMethod(n).invoke(target);}catch(ReflectiveOperationException|RuntimeException ignored){}return null;}
        private static Vector3d readVectorField(Object target,String name){try{Field f=target.getClass().getField(name);Object v=f.get(target);return v instanceof Vector3d x?new Vector3d(x):null;}catch(ReflectiveOperationException|RuntimeException ignored){return null;}}
        private static boolean finite(Vector3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
        private static void estimateInertiaFromVehicleEnvelope(VehicleState state,ThrusterRegistry registry){double halfX=1,halfY=Math.max(1,state.boundingHalfHeight),halfZ=Math.max(1,state.boundingRadius);if(registry!=null)for(ThrusterLink link:registry.getAllLinks()){if(link==null||link.source==null)continue;double[] r=link.source.getMountOffset();if(r==null||r.length<3)continue;halfX=Math.max(halfX,Math.abs(r[0]));halfY=Math.max(halfY,Math.abs(r[1]));halfZ=Math.max(halfZ,Math.abs(r[2]));}state.inertiaPitch=Math.max(1,state.mass*(halfY*halfY+halfZ*halfZ)/3);state.inertiaRoll=Math.max(1,state.mass*(halfX*halfX+halfY*halfY)/3);state.inertiaYaw=Math.max(1,state.mass*(halfX*halfX+halfZ*halfZ)/3);state.boundingRadius=Math.max(state.boundingRadius,Math.max(halfX,halfZ));state.boundingHalfHeight=Math.max(state.boundingHalfHeight,halfY);}
        private static double angleDelta(double current,double previous){double d=current-previous;while(d>Math.PI)d-=Math.PI*2;while(d<-Math.PI)d+=Math.PI*2;return d;}
    }
}