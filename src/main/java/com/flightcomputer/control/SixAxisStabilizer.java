package com.flightcomputer.control;

import com.flightcomputer.control.autotune.TuningResult;
import org.joml.Vector3d;
import java.util.EnumMap;
import java.util.Map;

/** One independent 6-vector stabilizer. A separate instance is used for STABILIZE and CRUISE. */
public final class SixAxisStabilizer {
    private final AxisPID pitchPID,rollPID,yawPID,verticalPID,longitudinalPID,lateralPID; private final boolean legacyStableProfile; public double gravity=0.0;
    public SixAxisStabilizer(){this(false);}
    public SixAxisStabilizer(boolean legacyStableProfile){this.legacyStableProfile=legacyStableProfile;if(legacyStableProfile){pitchPID=new AxisPID(8,.35,4,30);rollPID=new AxisPID(8,.35,4,30);yawPID=new AxisPID(4.5,.2,2,18);verticalPID=new AxisPID(3,.6,1.2,40);longitudinalPID=new AxisPID(2,.2,.8,40);lateralPID=new AxisPID(2,.2,.8,40);}else{pitchPID=new AxisPID(3.5,.05,4,16);rollPID=new AxisPID(3.5,.05,4,16);yawPID=new AxisPID(4,.1,2.5,16);verticalPID=new AxisPID(3,.4,1.2,36);longitudinalPID=new AxisPID(2,.15,.8,36);lateralPID=new AxisPID(2,.15,.8,36);}}
    public void applyProfile(TuningResult profile){if(profile==null)return;apply(pitchPID,profile.pitch());apply(rollPID,profile.roll());apply(yawPID,profile.yaw());apply(verticalPID,profile.vertical());apply(longitudinalPID,profile.longitudinal());apply(lateralPID,profile.lateral());}
    public TuningResult snapshotProfile(long fingerprint,int version){return new TuningResult(g(pitchPID),g(rollPID),g(yawPID),g(verticalPID),g(longitudinalPID),g(lateralPID),fingerprint,version);}
    private static void apply(AxisPID axis,TuningResult.Gains gains){if(gains!=null)axis.setGains(gains.p(),gains.i(),gains.d(),gains.maxOutput());}
    private static TuningResult.Gains g(AxisPID axis){return new TuningResult.Gains(axis.kp(),axis.ki(),axis.kd(),Math.max(.5,axis.kp()));}
    public Map<ControlAxis,Double> computeCommands(VehicleState state,StabilizationSetpoint sp,double dt){
        Map<ControlAxis,Double> out=new EnumMap<>(ControlAxis.class);double pitchError=wrapAngle(sp.desiredPitch-state.pitch),rollError=wrapAngle(sp.desiredRoll-state.roll),yawError=sp.yawIsRateNotHeading?sp.desiredYawRate-state.yawRate:wrapAngle(sp.desiredYaw-state.yaw);double ip=Math.max(state.inertiaPitch,1e-3),ir=Math.max(state.inertiaRoll,1e-3),iy=Math.max(state.inertiaYaw,1e-3),mass=Math.max(state.mass,1e-3);
        double pitchCommand=pitchPID.update(pitchError,state.pitch,dt,ip),rollCommand=rollPID.update(rollError,state.roll,dt,ir);if(!legacyStableProfile){pitchCommand+=clamp(-3*state.pitchRate*ip,-8,8);rollCommand+=clamp(-3*state.rollRate*ir,-8,8);}double yawCommand=yawPID.update(yawError,sp.yawIsRateNotHeading?state.yawRate:state.yaw,dt,iy);
        Vector3d externalBody=state.externalForceBody(),passive=state.namedPassiveForce(),externalTorque=state.externalTorqueBody();
        if(passive.lengthSquared()<1e-12)passive.set(externalBody);else stateToBody(state,passive);
        double passiveY=passive.y;if(Math.abs(passiveY)<1e-6&&Math.abs(state.gravityAcceleration)>1e-6)passiveY=-mass*state.gravityAcceleration;
        pitchCommand-=externalTorque.x;rollCommand-=externalTorque.z;
        double[] bodyVel=state.bodyFrameVelocity();double verticalForce=verticalPID.update(sp.desiredVerticalVelocity-state.vy,state.vy,dt,mass)-passiveY;double longitudinalForce=longitudinalPID.update(sp.desiredLongitudinalVelocity-bodyVel[0],bodyVel[0],dt,mass)-passive.z;double lateralForce=lateralPID.update(sp.desiredLateralVelocity-bodyVel[1],bodyVel[1],dt,mass)-passive.x;
        out.put(ControlAxis.PITCH,pitchCommand);out.put(ControlAxis.ROLL,rollCommand);out.put(ControlAxis.YAW,yawCommand);out.put(ControlAxis.VERTICAL,verticalForce);out.put(ControlAxis.LONGITUDINAL,longitudinalForce);out.put(ControlAxis.LATERAL,lateralForce);return out;
    }
    private static void stateToBody(VehicleState state,Vector3d world){new org.joml.Quaterniond().rotationY(state.yaw).rotateX(state.pitch).rotateZ(state.roll).conjugate().transform(world);}
    public Map<ControlAxis,Double> computeCommands(VehicleState state,StabilizationSetpoint sp,double dt,ThrusterRegistry ignoredRegistry,FlightMode ignoredMode){return computeCommands(state,sp,dt);}
    public void resetAll(){pitchPID.reset();rollPID.reset();yawPID.reset();verticalPID.reset();longitudinalPID.reset();lateralPID.reset();}
    private static double wrapAngle(double radians){double a=radians%(2*Math.PI);if(a>Math.PI)a-=2*Math.PI;if(a<-Math.PI)a+=2*Math.PI;return a;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
