package com.flightcomputer.control;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/** Server-side reflective bridge to Sable's authoritative mass, inertia and force state. */
public final class SableDynamicsReader {
    private SableDynamicsReader() { }

    public static boolean readMassData(Object subLevel, VehicleState state, Vec3 controllerLocalCenter) {
        if(subLevel==null||state==null)return false;
        try{
            Method getMassTracker=findMethod(subLevel.getClass(),"getMassTracker"); if(getMassTracker==null)return false;
            Object massData=getMassTracker.invoke(subLevel); if(massData==null)return false;
            boolean changed=false;
            Object mass=invokeNoArg(massData,"getMass");
            if(mass instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>0){state.mass=n.doubleValue();changed=true;}
            Object center=invokeNoArg(massData,"getCenterOfMass");
            if(center instanceof Vector3dc com){
                double cx=com.x()-(controllerLocalCenter==null?0:controllerLocalCenter.x),cy=com.y()-(controllerLocalCenter==null?0:controllerLocalCenter.y),cz=com.z()-(controllerLocalCenter==null?0:controllerLocalCenter.z);
                if(finite(cx)&&finite(cy)&&finite(cz)){state.comX=cx;state.comY=cy;state.comZ=cz;changed=true;}
            }
            Object inertia=invokeNoArg(massData,"getInertiaTensor");
            if(inertia instanceof Matrix3dc matrix){
                copyMatrix(matrix,state);state.inertiaPitch=Math.max(1e-3,Math.abs(matrix.m00()));state.inertiaRoll=Math.max(1e-3,Math.abs(matrix.m22()));state.inertiaYaw=Math.max(1e-3,Math.abs(matrix.m11()));changed=true;
            }
            return changed;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){return false;}
    }

    /** Reads Sable's named physics contributions when the current API exposes them. */
    public static void readPhysicsPanel(Object subLevel, VehicleState state) {
        if(subLevel==null||state==null)return;
        state.gravityAcceleration=readScalar(subLevel,"getGravityAcceleration","getGravity","gravity","getGravityScale");
        double[] v;
        v=readVector(subLevel,"getDragForce","getDrag","dragForce"); state.dragForceX=v[0];state.dragForceY=v[1];state.dragForceZ=v[2];
        v=readVector(subLevel,"getLevitationForce","getLevitation","levitationForce"); state.levitationForceX=v[0];state.levitationForceY=v[1];state.levitationForceZ=v[2];
        v=readVector(subLevel,"getBalloonLiftForce","getBalloonLift","balloonLiftForce"); state.balloonLiftForceX=v[0];state.balloonLiftForceY=v[1];state.balloonLiftForceZ=v[2];
        v=readVector(subLevel,"getPropulsionForce","getPropulsion","propulsionForce"); state.propulsionForceX=v[0];state.propulsionForceY=v[1];state.propulsionForceZ=v[2];
        v=readVector(subLevel,"getLiftForce","getLift","liftForce"); state.liftForceX=v[0];state.liftForceY=v[1];state.liftForceZ=v[2];
        v=readVector(subLevel,"getMagneticForce","getMagnetic","magneticForce"); state.magneticForceX=v[0];state.magneticForceY=v[1];state.magneticForceZ=v[2];
        v=readVector(subLevel,"getRecoilForce","getRecoil","recoilForce"); state.recoilForceX=v[0];state.recoilForceY=v[1];state.recoilForceZ=v[2];
        v=readVector(subLevel,"getImpactForce","getImpact","impactForce"); state.impactForceX=v[0];state.impactForceY=v[1];state.impactForceZ=v[2];

        // Some Sable builds expose the panel as a map/physics-state object rather than direct
        // methods. Try that form as a compatibility fallback.
        Object panel=invokeNoArg(subLevel,"getPhysicsState","getPhysics","getForces","getForceState");
        if(panel!=null){
            state.gravityAcceleration=prefer(readScalar(panel,"getGravityAcceleration","getGravity","gravity","getGravityScale"),state.gravityAcceleration);
            double[] p;
            p=prefer(readVector(panel,"getDragForce","getDrag","dragForce"),state.dragForceX,state.dragForceY,state.dragForceZ);state.dragForceX=p[0];state.dragForceY=p[1];state.dragForceZ=p[2];
            p=prefer(readVector(panel,"getLevitationForce","getLevitation","levitationForce"),state.levitationForceX,state.levitationForceY,state.levitationForceZ);state.levitationForceX=p[0];state.levitationForceY=p[1];state.levitationForceZ=p[2];
            p=prefer(readVector(panel,"getBalloonLiftForce","getBalloonLift","balloonLiftForce"),state.balloonLiftForceX,state.balloonLiftForceY,state.balloonLiftForceZ);state.balloonLiftForceX=p[0];state.balloonLiftForceY=p[1];state.balloonLiftForceZ=p[2];
            p=prefer(readVector(panel,"getPropulsionForce","getPropulsion","propulsionForce"),state.propulsionForceX,state.propulsionForceY,state.propulsionForceZ);state.propulsionForceX=p[0];state.propulsionForceY=p[1];state.propulsionForceZ=p[2];
            p=prefer(readVector(panel,"getLiftForce","getLift","liftForce"),state.liftForceX,state.liftForceY,state.liftForceZ);state.liftForceX=p[0];state.liftForceY=p[1];state.liftForceZ=p[2];
            p=prefer(readVector(panel,"getMagneticForce","getMagnetic","magneticForce"),state.magneticForceX,state.magneticForceY,state.magneticForceZ);state.magneticForceX=p[0];state.magneticForceY=p[1];state.magneticForceZ=p[2];
            p=prefer(readVector(panel,"getRecoilForce","getRecoil","recoilForce"),state.recoilForceX,state.recoilForceY,state.recoilForceZ);state.recoilForceX=p[0];state.recoilForceY=p[1];state.recoilForceZ=p[2];
            p=prefer(readVector(panel,"getImpactForce","getImpact","impactForce"),state.impactForceX,state.impactForceY,state.impactForceZ);state.impactForceX=p[0];state.impactForceY=p[1];state.impactForceZ=p[2];
        }
    }

    public static double[] queuedForceAndTorque(Object subLevel) {
        double[] result=new double[6]; if(subLevel==null)return result;
        try{
            Method method=findMethod(subLevel.getClass(),"getQueuedForceGroups");if(method==null)return result;Object groups=method.invoke(subLevel);if(!(groups instanceof Map<?,?> map))return result;
            for(Map.Entry<?,?> entry:map.entrySet()){
                Object queued=entry.getValue(),total=invokeNoArg(queued,"getForceTotal");if(total==null)continue;
                Object force=invokeNoArg(total,"getLocalForce"),torque=invokeNoArg(total,"getLocalTorque");
                if(force instanceof Vector3dc f){result[0]+=f.x();result[1]+=f.y();result[2]+=f.z();}
                if(torque instanceof Vector3dc t){result[3]+=t.x();result[4]+=t.y();result[5]+=t.z();}
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        return result;
    }

    private static double readScalar(Object target,String... names){
        for(String name:names){Object value=invokeNoArg(target,name);if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return n.doubleValue();}
        return 0.0D;
    }
    private static double prefer(double value,double previous){return Math.abs(value)>1e-12?value:previous;}
    private static double[] prefer(double[] value,double x,double y,double z){return (Math.abs(value[0])+Math.abs(value[1])+Math.abs(value[2]))>1e-12?value:new double[]{x,y,z};}
    private static double[] readVector(Object target,String... names){
        for(String name:names){
            Object value=invokeNoArg(target,name);
            double[] vector=toVector(value); if(vector!=null)return vector;
        }
        return new double[3];
    }
    private static double[] toVector(Object value){
        if(value instanceof Vector3dc v)return new double[]{v.x(),v.y(),v.z()};
        if(value instanceof Vec3 v)return new double[]{v.x,v.y,v.z};
        if(value instanceof Number n)return new double[]{0,n.doubleValue(),0};
        if(value==null)return null;
        Double x=readComponent(value,"x","getX"),y=readComponent(value,"y","getY"),z=readComponent(value,"z","getZ");
        return x!=null&&y!=null&&z!=null?new double[]{x,y,z}:null;
    }
    private static Double readComponent(Object target,String field,String getter){
        try{Field f=findField(target.getClass(),field);if(f!=null&&f.get(target) instanceof Number n)return n.doubleValue();}catch(ReflectiveOperationException|RuntimeException ignored){}
        Object value=invokeNoArg(target,getter);return value instanceof Number n?n.doubleValue():null;
    }
    private static void copyMatrix(Matrix3dc matrix,VehicleState state){
        state.i00=finite(matrix.m00())?matrix.m00():1;state.i01=finite(matrix.m01())?matrix.m01():0;state.i02=finite(matrix.m02())?matrix.m02():0;
        state.i10=finite(matrix.m10())?matrix.m10():0;state.i11=finite(matrix.m11())?matrix.m11():1;state.i12=finite(matrix.m12())?matrix.m12():0;
        state.i20=finite(matrix.m20())?matrix.m20():0;state.i21=finite(matrix.m21())?matrix.m21():0;state.i22=finite(matrix.m22())?matrix.m22():1;
    }
    private static Method findMethod(Class<?> type,String name){Class<?> cursor=type;while(cursor!=null){try{Method method=cursor.getDeclaredMethod(name);method.setAccessible(true);return method;}catch(ReflectiveOperationException|RuntimeException ignored){cursor=cursor.getSuperclass();}}try{Method method=type.getMethod(name);method.setAccessible(true);return method;}catch(ReflectiveOperationException|RuntimeException ignored){return null;}}
    private static Object invokeNoArg(Object target,String... names){if(target==null)return null;for(String name:names){Method method=findMethod(target.getClass(),name);if(method==null)continue;try{return method.invoke(target);}catch(ReflectiveOperationException|RuntimeException ignored){}}return null;}
    private static Field findField(Class<?> type,String name){Class<?> cursor=type;while(cursor!=null){try{Field field=cursor.getDeclaredField(name);field.setAccessible(true);return field;}catch(ReflectiveOperationException|RuntimeException ignored){cursor=cursor.getSuperclass();}}return null;}
    private static boolean finite(double value){return Double.isFinite(value);}
}
