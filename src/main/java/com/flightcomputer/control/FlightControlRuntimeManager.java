package com.flightcomputer.control;

import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative bridge between Sable state, MCP/PID guidance and physical thruster banks. */
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
        Vec3 target = runtime.targetActive ? runtime.target : null;
        double distance = target == null ? -1.0D : target.distanceTo(new Vec3(state.x, state.y, state.z));
        double heading = normalizeDegrees(Math.toDegrees(state.yaw));
        double speed = safeSpeed(state.vx, state.vy, state.vz);
        FlightComputerNetwork.TelemetryPayload payload = new FlightComputerNetwork.TelemetryPayload(
                controller.getControllerId(), state.x, state.y, state.z, speed, heading,
                Math.toDegrees(state.pitch), Math.toDegrees(state.roll),
                target != null, target == null ? 0.0D : target.x, target == null ? 0.0D : target.y,
                target == null ? 0.0D : target.z, runtime.targetName, distance,
                controller.getTemperature(), controller.getMaxTemperature(), controller.getThermalState().ordinal(),
                controller.getThermalCooldownTicksRemaining(), controller.getEnergyStorage().getEnergyStored(),
                controller.getEnergyStorage().getMaxEnergyStored(), controller.getCoolingTier().ordinal(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(controller.getBlockPos().getX() + 0.5D,
                    controller.getBlockPos().getY() + 0.5D,
                    controller.getBlockPos().getZ() + 0.5D) <= 128.0D * 128.0D)
                FlightComputerNetwork.sendTelemetry(player, payload);
        }
    }

    public static synchronized void remove(FlightControllerBlockEntity controller) {
        if (controller != null) RUNTIMES.remove(controller.getControllerId());
    }

    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static double safeSpeed(double x, double y, double z) {
        double speed = Math.sqrt(x * x + y * y + z * z);
        return Double.isFinite(speed) ? speed : 0.0D;
    }
    private static double normalizeDegrees(double degrees) { double v = degrees % 360.0D; return v < 0 ? v + 360.0D : v; }

    public static final class Runtime {
        private VehicleState snapshot;
        private Vec3 previousPosition;
        private double previousYaw, previousPitch, previousRoll;
        private Vec3 target;
        private String targetName = "";
        private boolean targetActive;
        private FlightComputer computer;
        private Vec3 lastNavigatorTarget;
        private Object helper;
        private Method getContaining, projectOut;
        private boolean initialized, available;

        public void update(FlightControllerBlockEntity controller) {
            if (controller == null || controller.getLevel() == null) return;
            Level level = controller.getLevel();
            Vec3 local = Vec3.atCenterOf(controller.getBlockPos());
            Vec3 world = project(level, local);
            VehicleState state = snapshot == null ? new VehicleState() : snapshot;
            state.x = world.x; state.y = world.y; state.z = world.z;

            Object subLevel = containing(level, local);
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
                    state.vx=linear.x; state.vy=linear.y; state.vz=linear.z; physicalVelocity=true;
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

            if(!physicalVelocity&&previousPosition!=null){state.vx=(world.x-previousPosition.x)*20;state.vy=(world.y-previousPosition.y)*20;state.vz=(world.z-previousPosition.z)*20;}
            else if(!physicalVelocity){state.vx=state.vy=state.vz=0;}
            if(!physicalInertia)estimateInertiaFromVehicleEnvelope(state,computer==null?null:computer.getRegistry());
            if(!physicalVelocity&&previousPosition!=null){state.yawRate=angleDelta(state.yaw,previousYaw)*20;state.pitchRate=angleDelta(state.pitch,previousPitch)*20;state.rollRate=angleDelta(state.roll,previousRoll)*20;}
            state.timestampNanos=System.nanoTime(); previousPosition=world;previousYaw=state.yaw;previousPitch=state.pitch;previousRoll=state.roll;snapshot=state.copy();
        }

        private void control(FlightControllerBlockEntity controller) {
            if(controller==null||controller.getLevel()==null||controller.getLevel().isClientSide()||snapshot==null)return;
            if(computer==null)computer=new FlightComputer(()->snapshot);
            ThrusterRegistry registry=computer.getRegistry();
            registry.refresh(controller.getLevel(),controller.getBlockPos(),controller.getVectorLinks(FlightMode.STABILIZE),controller.getVectorLinks(FlightMode.CRUISE),controller.getLevel().getGameTime());
            if(targetActive&&target!=null){if(lastNavigatorTarget==null||!target.equals(lastNavigatorTarget)){computer.getNavigator().setTarget(target.x,target.y,target.z);lastNavigatorTarget=target;}}
            else if(computer.getNavigator().hasTarget()){computer.getNavigator().clearTarget();lastNavigatorTarget=null;}
            FlightControllerState state=controller.getControllerState();
            boolean powered=controller.isEngaged()&&!controller.isThermalLockout()&&controller.getEnergyStorage().getEnergyStored()>0;
            boolean stabiliserEnabled=powered&&controller.isStabiliser();
            boolean autopilotEnabled=powered&&state.flightMode()==com.flightcomputer.avionics.FlightMode.AUTOPILOT&&targetActive&&target!=null;
            computer.tick(1.0D/20.0D,stabiliserEnabled,autopilotEnabled);
            if(powered)controller.addControlThermalLoad(computer.getAllocator().getLastThermalLoad());
        }

        private static void estimateInertiaFromVehicleEnvelope(VehicleState state,ThrusterRegistry registry){
            double halfX=1,halfY=Math.max(1,state.boundingHalfHeight),halfZ=Math.max(1,state.boundingRadius);
            if(registry!=null)for(ThrusterLink link:registry.getAllLinks()){if(link==null||link.source==null)continue;double[] r=link.source.getMountOffset();if(r==null||r.length<3)continue;halfX=Math.max(halfX,Math.abs(r[0]));halfY=Math.max(halfY,Math.abs(r[1]));halfZ=Math.max(halfZ,Math.abs(r[2]));}
            state.inertiaPitch=Math.max(1,state.mass*(halfY*halfY+halfZ*halfZ)/3);state.inertiaRoll=Math.max(1,state.mass*(halfX*halfX+halfY*halfY)/3);state.inertiaYaw=Math.max(1,state.mass*(halfX*halfX+halfZ*halfZ)/3);state.boundingRadius=Math.max(state.boundingRadius,Math.max(halfX,halfZ));state.boundingHalfHeight=Math.max(state.boundingHalfHeight,halfY);
        }
        private static double[] readInertia(Object target){if(target==null)return null;for(String n:new String[]{"getMomentOfInertia","getInertia","momentOfInertia","inertia"}){double[] p=parseVector(invokeNoArg(target,n));if(p!=null)return p;}return null;}
        private static double[] parseVector(Object v){if(v instanceof Vector3d x)return new double[]{Math.abs(x.x),Math.abs(x.y),Math.abs(x.z)};if(v instanceof Vec3 x)return new double[]{Math.abs(x.x),Math.abs(x.y),Math.abs(x.z)};if(v instanceof double[] a&&a.length>=3)return new double[]{Math.abs(a[0]),Math.abs(a[1]),Math.abs(a[2])};return null;}
        private static double readDouble(Object target,String...names){if(target==null)return -1;for(String n:names){Object v=invokeNoArg(target,n);if(v instanceof Number x&&x.doubleValue()>0)return x.doubleValue();}return -1;}
        private Vec3 project(Level level,Vec3 local){if(!ensure())return local;try{Object v=projectOut.invoke(helper,level,local);return v instanceof Vec3 x?x:local;}catch(ReflectiveOperationException|RuntimeException ignored){return local;}}
        private Object containing(Level level,Vec3 local){if(!ensure())return null;try{return getContaining.invoke(helper,level,local);}catch(ReflectiveOperationException|RuntimeException ignored){return null;}}
        private boolean ensure(){if(initialized)return available;initialized=true;try{Class<?> sable=Class.forName("dev.ryanhcode.sable.companion.SableCompanion",false,getClass().getClassLoader());helper=sable.getField("INSTANCE").get(null);getContaining=helper.getClass().getMethod("getContaining",Level.class,Vec3.class);projectOut=helper.getClass().getMethod("projectOutOfSubLevel",Level.class,Vec3.class);available=true;}catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){available=false;}return available;}
        private static Object invokeNoArg(Object target,String...names){if(target==null)return null;for(String n:names)try{return target.getClass().getMethod(n).invoke(target);}catch(ReflectiveOperationException|RuntimeException ignored){}return null;}
        private static Vector3d readVectorField(Object target,String name){try{Field f=target.getClass().getField(name);Object v=f.get(target);return v instanceof Vector3d x?new Vector3d(x):null;}catch(ReflectiveOperationException|RuntimeException ignored){return null;}}
        private static boolean finite(Vector3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
        private static double angleDelta(double current,double previous){double d=current-previous;while(d>Math.PI)d-=Math.PI*2;while(d<-Math.PI)d+=Math.PI*2;return d;}
    }
}
