package com.flightcomputer.control;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-controller runtime owner. Terrain/map code is deliberately outside this package. */
public final class FlightControlRuntimeManager {
    private static final Map<UUID, Runtime> RUNTIMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Target> TARGETS = new ConcurrentHashMap<>();
    private FlightControlRuntimeManager() { }
    public static void tick(FlightControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        RUNTIMES.computeIfAbsent(controller.getControllerId(), id -> new Runtime()).tick(controller);
    }
    public static void setTarget(FlightControllerBlockEntity controller, Vec3 target, String name) { if (controller != null && target != null) TARGETS.put(controller.getControllerId(), new Target(target, name == null ? "TARGET" : name)); }
    public static void clearTarget(FlightControllerBlockEntity controller) { if (controller != null) TARGETS.remove(controller.getControllerId()); }
    public static void remove(UUID controllerId) { RUNTIMES.remove(controllerId); TARGETS.remove(controllerId); }

    private static final class Runtime {
        private final SableVehicleStateProvider stateProvider = new SableVehicleStateProvider();
        private final FlightComputer computer = new FlightComputer(stateProvider);
        private long lastLinkRefresh = Long.MIN_VALUE;
        private long lastTelemetry = Long.MIN_VALUE;
        void tick(FlightControllerBlockEntity controller) {
            stateProvider.update(controller, computer.getRegistry());
            Level level = controller.getLevel(); long time = level.getGameTime();
            if (time - lastLinkRefresh >= 5) {
                computer.getRegistry().refresh(level, controller.getBlockPos(), controller.getVectorLinks(FlightMode.STABILIZE), controller.getVectorLinks(FlightMode.CRUISE), time);
                lastLinkRefresh = time;
            }
            Target target = TARGETS.get(controller.getControllerId());
            if (target != null) computer.getNavigator().setTarget(target.position.x, target.position.y, target.position.z); else computer.getNavigator().clearTarget();
            if (controller.isEngaged() && !controller.isThermalLockout()) {
                computer.tick(0.05D);
                controller.addControlThermalLoad(computer.getAllocator().getLastThermalLoad());
            }
            if (time - lastTelemetry >= 5) { lastTelemetry = time; sendTelemetry(controller, target); }
        }
        private void sendTelemetry(FlightControllerBlockEntity controller, Target target) {
            VehicleState state = stateProvider.getState(); if (state == null) return;
            double distance = target == null ? -1.0D : Math.sqrt(Math.pow(target.position.x - state.x, 2) + Math.pow(target.position.y - state.y, 2) + Math.pow(target.position.z - state.z, 2));
            double[] s = authorities(computer.getRegistry(), FlightMode.STABILIZE), a = authorities(computer.getRegistry(), FlightMode.CRUISE);
            for (Player player : controller.getLevel().players()) {
                if (!(player instanceof ServerPlayer serverPlayer)) continue;
                if (serverPlayer.distanceToSqr(controller.getBlockPos().getX()+0.5D, controller.getBlockPos().getY()+0.5D, controller.getBlockPos().getZ()+0.5D) > 4096.0D) continue;
                FlightComputerNetwork.sendTelemetry(serverPlayer, new FlightComputerNetwork.TelemetryPayload(controller.getControllerId(), state.x,state.y,state.z,
                        Math.sqrt(state.vx*state.vx+state.vy*state.vy+state.vz*state.vz), Math.toDegrees(state.yaw),Math.toDegrees(state.pitch),Math.toDegrees(state.roll),
                        target != null,target == null?0:target.position.x,target == null?0:target.position.y,target == null?0:target.position.z,target == null?"":target.name,distance,
                        controller.getTemperature(),controller.getMaxTemperature(),controller.getThermalState().ordinal(),controller.getThermalCooldownTicksRemaining(),controller.getEnergyStorage().getEnergyStored(),controller.getEnergyStorage().getMaxEnergyStored(),controller.getCoolingTier().ordinal(),
                        s[0],s[1],s[2],s[3],s[4],s[5],a[0],a[1],a[2],a[3],a[4],a[5]));
            }
        }
        private double[] authorities(ThrusterRegistry registry, FlightMode mode) { double[] values=new double[VectorDirection.values().length]; for(VectorDirection d:VectorDirection.values()) values[d.ordinal()]=registry.getVectorAuthority(mode,d); return values; }
    }
    private record Target(Vec3 position, String name) { }

    private static final class SableVehicleStateProvider implements VehicleStateProvider {
        private Object helper; private Method getContaining; private Method projectOut; private boolean initialized; private boolean available;
        private Vec3 previousPosition; private double previousPitch,previousRoll,previousYaw; private VehicleState snapshot;
        @Override public VehicleState getState() { return snapshot; }

        void update(FlightControllerBlockEntity controller, ThrusterRegistry registry) {
            if(controller==null||controller.getLevel()==null)return;
            Level level=controller.getLevel(); Vec3 local=Vec3.atCenterOf(controller.getBlockPos()); Vec3 world=project(level,local);
            VehicleState state=snapshot==null?new VehicleState():snapshot; state.x=world.x;state.y=world.y;state.z=world.z;
            if(previousPosition!=null){state.vx=(world.x-previousPosition.x)*20.0D;state.vy=(world.y-previousPosition.y)*20.0D;state.vz=(world.z-previousPosition.z)*20.0D;}
            Object subLevel=containing(level,controller);
            if(subLevel!=null)try{
                Object pose=subLevel.getClass().getMethod("logicalPose").invoke(subLevel);
                Object orientation=pose.getClass().getMethod("orientation").invoke(pose);
                if(orientation instanceof Quaterniondc q){double w=q.w(),x=q.x(),y=q.y(),z=q.z();state.yaw=Math.atan2(2*(w*y+x*z),1-2*(y*y+z*z));state.pitch=Math.asin(Math.max(-1,Math.min(1,2*(w*x-y*z))));state.roll=Math.atan2(2*(w*z+x*y),1-2*(x*x+y*y));}
                Object tracker=invokeNoArg(subLevel,"getMassTracker","massTracker");
                Object mass=tracker==null?null:invokeNoArg(tracker,"getMass","mass");
                if(mass instanceof Number n&&n.doubleValue()>0)state.mass=n.doubleValue();
                double[] inertia = readInertia(tracker);
                if(inertia == null) inertia = readInertia(subLevel);
                if(inertia != null) {
                    state.inertiaPitch = Math.max(1.0D, inertia[0]);
                    state.inertiaRoll = Math.max(1.0D, inertia[1]);
                    state.inertiaYaw = Math.max(1.0D, inertia[2]);
                }
                double radius = readDouble(subLevel,"getBoundingRadius","boundingRadius","getRadius");
                if(radius > 0) state.boundingRadius=Math.max(1.0D,radius);
                double halfHeight = readDouble(subLevel,"getBoundingHalfHeight","boundingHalfHeight","getHalfHeight");
                if(halfHeight > 0) state.boundingHalfHeight=Math.max(1.0D,halfHeight);
            }catch(ReflectiveOperationException|RuntimeException ignored){}

            // Universal fallback: when the vehicle API does not expose inertia, use its
            // measured mass and physical control envelope rather than the old fixed 1 kg·m².
            // This keeps small craft responsive and prevents large craft from over-commanding.
            if(state.inertiaPitch <= 1.0D || state.inertiaRoll <= 1.0D || state.inertiaYaw <= 1.0D) {
                double radius = Math.max(1.0D, state.boundingRadius);
                double vertical = Math.max(1.0D, state.boundingHalfHeight);
                double transverse = Math.max(1.0D, radius);
                double boxPitch = state.mass * (transverse*transverse + vertical*vertical) / 3.0D;
                double boxRoll = state.mass * (transverse*transverse + vertical*vertical) / 3.0D;
                double boxYaw = state.mass * (transverse*transverse) / 2.0D;
                if(state.inertiaPitch <= 1.0D) state.inertiaPitch=Math.max(1.0D,boxPitch);
                if(state.inertiaRoll <= 1.0D) state.inertiaRoll=Math.max(1.0D,boxRoll);
                if(state.inertiaYaw <= 1.0D) state.inertiaYaw=Math.max(1.0D,boxYaw);
            }
            if(previousPosition!=null){state.yawRate=angleDelta(state.yaw,previousYaw)*20;state.pitchRate=angleDelta(state.pitch,previousPitch)*20;state.rollRate=angleDelta(state.roll,previousRoll)*20;}
            state.timestampNanos=System.nanoTime();previousPosition=world;previousYaw=state.yaw;previousPitch=state.pitch;previousRoll=state.roll;snapshot=state.copy();
        }

        private static double[] readInertia(Object target){
            if(target==null)return null;
            for(String name:new String[]{"getMomentOfInertia","getInertia","momentOfInertia","inertia"}){
                Object value=invokeNoArg(target,name);
                double[] parsed=parseVector(value);
                if(parsed!=null)return parsed;
            }
            return null;
        }
        private static double[] parseVector(Object value){
            if(value instanceof Vector3d v)return new double[]{v.x,v.y,v.z};
            if(value instanceof Vec3 v)return new double[]{Math.abs(v.x),Math.abs(v.y),Math.abs(v.z)};
            if(value instanceof double[] a&&a.length>=3)return new double[]{Math.abs(a[0]),Math.abs(a[1]),Math.abs(a[2])};
            return null;
        }
        private static double readDouble(Object target,String...names){for(String name:names){Object v=invokeNoArg(target,name);if(v instanceof Number n&&n.doubleValue()>0)return n.doubleValue();}return -1;}
        private Vec3 project(Level level,Vec3 local){if(!ensure())return local;try{Object value=projectOut.invoke(helper,level,local);return value instanceof Vec3 vec?vec:local;}catch(ReflectiveOperationException|RuntimeException ignored){return local;}}
        private Object containing(Level level,FlightControllerBlockEntity controller){if(!ensure())return null;try{return getContaining.invoke(helper,controller);}catch(ReflectiveOperationException|RuntimeException ignored){return null;}}
        private boolean ensure(){if(initialized)return available;initialized=true;try{Class<?> sable=Class.forName("dev.ryanhcode.sable.Sable",false,getClass().getClassLoader());helper=sable.getField("HELPER").get(null);getContaining=helper.getClass().getMethod("getContaining",net.minecraft.world.level.block.entity.BlockEntity.class);projectOut=helper.getClass().getMethod("projectOutOfSubLevel",Level.class,net.minecraft.world.phys.Vec3.class);available=true;}catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){available=false;}return available;}
        private static Object invokeNoArg(Object target,String...names){for(String name:names)try{return target.getClass().getMethod(name).invoke(target);}catch(ReflectiveOperationException|RuntimeException ignored){}return null;}
        private static double angleDelta(double current,double previous){double d=current-previous;while(d>Math.PI)d-=Math.PI*2;while(d<-Math.PI)d+=Math.PI*2;return d;}
    }
}
