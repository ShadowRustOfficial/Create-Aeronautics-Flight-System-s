package com.flightcomputer.control;

import com.flightcomputer.control.autotune.TuningResult;

/**
 * Builds deterministic PID inputs from the live Sable vehicle instead of using one global tune.
 * Re-tuning is triggered by a 10-block structure bucket, mass/inertia/COM changes, force-model
 * changes, or a newly discovered thruster.
 */
public final class AdaptiveFlightTuner {
    private long lastFingerprint = Long.MIN_VALUE;

    public void update(VehicleState state, ThrusterRegistry registry, SixAxisStabilizer stabilise, SixAxisStabilizer cruise) {
        if(state==null||registry==null||stabilise==null||cruise==null)return;
        long fingerprint=fingerprint(state,registry);
        if(fingerprint==lastFingerprint)return;
        lastFingerprint=fingerprint;
        stabilise.applyProfile(profile(state,registry,FlightMode.STABILIZE,true));
        cruise.applyProfile(profile(state,registry,FlightMode.CRUISE,false));
    }

    public long fingerprint(VehicleState state,ThrusterRegistry registry){
        long h=1469598103934665603L;
        h=mix(h,state.structureBlockCount/10);h=mix(h,Math.round(state.mass/10.0));
        h=mix(h,q(state.comX));h=mix(h,q(state.comY));h=mix(h,q(state.comZ));
        h=mix(h,q(state.inertiaPitch));h=mix(h,q(state.inertiaRoll));h=mix(h,q(state.inertiaYaw));h=mix(h,q(state.gravityAcceleration));
        h=mix(h,q(state.dragForceX));h=mix(h,q(state.dragForceY));h=mix(h,q(state.dragForceZ));
        h=mix(h,q(state.levitationForceX));h=mix(h,q(state.levitationForceY));h=mix(h,q(state.levitationForceZ));
        h=mix(h,q(state.balloonLiftForceX));h=mix(h,q(state.balloonLiftForceY));h=mix(h,q(state.balloonLiftForceZ));
        h=mix(h,q(state.propulsionForceX));h=mix(h,q(state.propulsionForceY));h=mix(h,q(state.propulsionForceZ));
        h=mix(h,q(state.liftForceX));h=mix(h,q(state.liftForceY));h=mix(h,q(state.liftForceZ));
        h=mix(h,q(state.magneticForceX));h=mix(h,q(state.magneticForceY));h=mix(h,q(state.magneticForceZ));
        h=mix(h,q(state.recoilForceX));h=mix(h,q(state.recoilForceY));h=mix(h,q(state.recoilForceZ));
        h=mix(h,q(state.impactForceX));h=mix(h,q(state.impactForceY));h=mix(h,q(state.impactForceZ));
        for(FlightMode mode:FlightMode.values())for(VectorDirection d:VectorDirection.values()){h=mix(h,registry.getVectorThrusterCount(mode,d));h=mix(h,q(registry.getVectorAuthority(mode,d)));}
        return h;
    }

    private TuningResult profile(VehicleState s,ThrusterRegistry r,FlightMode mode,boolean stable){
        double mass=Math.max(1e-3,s.mass);
        double verticalAuthority=Math.min(r.getVectorAuthority(mode,VectorDirection.UP),r.getVectorAuthority(mode,VectorDirection.DOWN))/mass;
        double longitudinalAuthority=Math.min(r.getVectorAuthority(mode,VectorDirection.NORTH),r.getVectorAuthority(mode,VectorDirection.SOUTH))/mass;
        double lateralAuthority=Math.min(r.getVectorAuthority(mode,VectorDirection.EAST),r.getVectorAuthority(mode,VectorDirection.WEST))/mass;
        double pitchAuthority=torqueAuthority(r,mode,s,0)/Math.max(1e-3,s.inertiaPitch);
        double rollAuthority=torqueAuthority(r,mode,s,2)/Math.max(1e-3,s.inertiaRoll);
        double yawAuthority=torqueAuthority(r,mode,s,1)/Math.max(1e-3,s.inertiaYaw);

        double pitchScale=responseScale(pitchAuthority,2.0),rollScale=responseScale(rollAuthority,2.0),yawScale=responseScale(yawAuthority,1.5);
        double verticalScale=responseScale(verticalAuthority,3.0),longitudinalScale=responseScale(longitudinalAuthority,3.0),lateralScale=responseScale(lateralAuthority,3.0);
        double pitchMax=Math.max(.5,pitchAuthority*.72),rollMax=Math.max(.5,rollAuthority*.72),yawMax=Math.max(.5,yawAuthority*.72);
        double verticalMax=Math.max(.5,verticalAuthority*.72),longitudinalMax=Math.max(.5,longitudinalAuthority*.72),lateralMax=Math.max(.5,lateralAuthority*.72);

        TuningResult.Gains pitch=g(8.0*pitchScale,.35*pitchScale*.75,4.0*(1.12/pitchScale),pitchMax);
        TuningResult.Gains roll=g(8.0*rollScale,.35*rollScale*.75,4.0*(1.12/rollScale),rollMax);
        TuningResult.Gains yaw=g(4.5*yawScale,.20*yawScale*.75,2.0*(1.10/yawScale),yawMax);
        TuningResult.Gains vertical=g(3.0*verticalScale,.60*verticalScale*.70,1.2*(1.10/verticalScale),verticalMax);
        TuningResult.Gains longitudinal=g(2.0*longitudinalScale,.20*longitudinalScale*.70,.8*(1.10/longitudinalScale),longitudinalMax);
        TuningResult.Gains lateral=g(2.0*lateralScale,.20*lateralScale*.70,.8*(1.10/lateralScale),lateralMax);
        return new TuningResult(pitch,roll,yaw,vertical,longitudinal,lateral,fingerprint(s,r),stable?2:2);
    }

    private static TuningResult.Gains g(double p,double i,double d,double max){return new TuningResult.Gains(clamp(p,.35,12),clamp(i,0,.75),clamp(d,.5,9),Math.max(.5,max));}
    private static double responseScale(double authority,double reference){if(!Double.isFinite(authority)||authority<=0)return .55;return clamp(Math.sqrt(authority/reference),.55,1.25);}

    private static double torqueAuthority(ThrusterRegistry registry,FlightMode mode,VehicleState state,int axis){
        double total=0;
        for(ThrusterLink link:registry.getAllLinks(mode)){
            if(link==null||link.source==null)continue;
            double thrust=Math.max(0,link.source.getAvailableThrust());if(thrust<=0)continue;
            double[] m=link.source.getMountOffset();if(m==null||m.length<3)continue;
            double rx=m[0]-state.comX,ry=m[1]-state.comY,rz=m[2]-state.comZ;
            double fx=link.direction.x()*link.polarity*thrust,fy=link.direction.y()*link.polarity*thrust,fz=link.direction.z()*link.polarity*thrust;
            double tx=ry*fz-rz*fy,tz=rx*fy-ry*fx,ty=rz*fx-rx*fz;
            total+=Math.abs(axis==0?tx:axis==1?ty:tz);
        }
        return total;
    }
    private static long q(double value){return Math.round(Double.isFinite(value)?value*1000.0:0.0);}
    private static long mix(long h,long value){h^=value;return h*1099511628211L;}
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
