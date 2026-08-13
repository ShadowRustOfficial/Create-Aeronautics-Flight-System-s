package com.flightcomputer.control;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Receding-horizon route planner used by the autopilot and the map route preview. */
public final class MPCNavigator {
    private static final int HORIZON_STEPS = 10;
    private static final double HORIZON_DT = 0.3;
    private static final double ARRIVAL_RADIUS = 1.5;
    private static final double ARRIVAL_HOLD_P = 0.55;
    private static final double ARRIVAL_MAX_SPEED = 1.25;
    private static final double CLEARANCE_MARGIN = 1.0;
    private static final double HYSTERESIS_BONUS = 6.0;
    private static final double LOOKAHEAD_DISTANCE = 32.0;

    private final ObstacleSensor obstacleSensor;
    private double targetX, targetY, targetZ;
    private boolean hasTarget;
    private boolean arrivalLocked;
    private boolean pathBlocked;
    private double lastChosenHeadingOffsetDeg;
    private List<Vec3> routePoints = List.of();

    public MPCNavigator(ObstacleSensor obstacleSensor) { this.obstacleSensor = obstacleSensor; }
    public MPCNavigator() { this(null); }
    public void setTarget(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        targetX=x; targetY=y; targetZ=z; hasTarget=true; arrivalLocked=false; pathBlocked=false; lastChosenHeadingOffsetDeg=0; routePoints=List.of();
    }
    public void clearTarget() { hasTarget=false; arrivalLocked=false; pathBlocked=false; routePoints=List.of(); }
    public boolean hasTarget() { return hasTarget; }
    public boolean isPathBlocked() { return pathBlocked; }
    public boolean isArrivalLocked() { return arrivalLocked; }
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public double targetZ() { return targetZ; }
    public List<Vec3> routePoints() { return routePoints; }
    public double distanceToTarget(VehicleState state) {
        if (state == null || !hasTarget) return -1.0D;
        double dx=targetX-state.x, dy=targetY-state.y, dz=targetZ-state.z;
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    public double targetBearing(VehicleState state) {
        if (!hasTarget || state == null) return state == null ? 0.0D : state.yaw;
        return Math.atan2(targetX - state.x, targetZ - state.z);
    }

    /**
     * Returns the vessel yaw that makes the Flight Computer's BACK point along the selected
     * world-space travel bearing. Translation is deliberately kept independent of this heading.
     */
    private static double desiredVesselYawForBearing(VehicleState state, double bearing) {
        return normalizeRadians(bearing - state.bodyBackYawOffset);
    }

    public StabilizationSetpoint plan(VehicleState state, double maxSpeed, double maxDeceleration) {
        StabilizationSetpoint sp = StabilizationSetpoint.hover();
        if (!hasTarget || state == null) return sp;

        double dx=targetX-state.x, dy=targetY-state.y, dz=targetZ-state.z;
        double flatDist=Math.sqrt(dx*dx+dz*dz);
        double fullDistance=Math.sqrt(dx*dx+dy*dy+dz*dz);

        if (arrivalLocked || (flatDist < ARRIVAL_RADIUS && Math.abs(dy) < ARRIVAL_RADIUS)) {
            arrivalLocked = true;
            pathBlocked = false;
            routePoints = List.of(new Vec3(state.x,state.y,state.z), new Vec3(targetX,targetY,targetZ));
            // At the exact destination there is no meaningful travel bearing. Hold the current
            // vessel orientation rather than injecting a new yaw impulse while position-locking.
            sp.desiredYaw=state.yaw;
            sp.yawIsRateNotHeading=false;
            double sinYaw=Math.sin(state.yaw), cosYaw=Math.cos(state.yaw);
            double worldCorrectionX=clamp(dx*ARRIVAL_HOLD_P,-ARRIVAL_MAX_SPEED,ARRIVAL_MAX_SPEED);
            double worldCorrectionZ=clamp(dz*ARRIVAL_HOLD_P,-ARRIVAL_MAX_SPEED,ARRIVAL_MAX_SPEED);
            double worldCorrectionY=clamp(dy*ARRIVAL_HOLD_P,-ARRIVAL_MAX_SPEED,ARRIVAL_MAX_SPEED);
            sp.desiredLongitudinalVelocity=worldCorrectionX*sinYaw+worldCorrectionZ*cosYaw;
            sp.desiredLateralVelocity=worldCorrectionX*cosYaw-worldCorrectionZ*sinYaw;
            sp.desiredVerticalVelocity=worldCorrectionY;
            return sp;
        }
        if (fullDistance > ARRIVAL_RADIUS * 4.0D) arrivalLocked = false;

        Vec3 navigationPoint = new Vec3(targetX,targetY,targetZ);
        List<Vec3> planned = buildClearRoute(state, navigationPoint);
        routePoints = planned;
        if (planned.size() > 1) navigationPoint = planned.get(1);

        double segDx=navigationPoint.x-state.x, segDy=navigationPoint.y-state.y, segDz=navigationPoint.z-state.z;
        double segFlat=Math.sqrt(segDx*segDx+segDz*segDz);
        double bearing=Math.atan2(segDx,segDz);
        double radius=Math.max(0.5,state.boundingRadius);
        double safeDeceleration=Math.max(0.5,maxDeceleration);

        double distanceForSpeed=Math.max(segFlat,Math.abs(segDy));
        double stoppingSpeed=Math.sqrt(Math.max(0.0,2.0*safeDeceleration*Math.max(0.0,distanceForSpeed-radius)));
        double desiredSpeed=Math.min(Math.max(0.0,maxSpeed),stoppingSpeed);
        if(distanceForSpeed>6.0) desiredSpeed=Math.max(desiredSpeed,Math.min(maxSpeed,6.0));

        double desiredVesselYaw=desiredVesselYawForBearing(state,bearing);
        double headingOffset=Math.toDegrees(normalizeRadians(desiredVesselYaw-state.yaw));
        lastChosenHeadingOffsetDeg=headingOffset;

        double horizontalSpeed=segFlat<1.0e-6?0.0:Math.min(desiredSpeed,Math.max(0.0,segFlat*1.5));
        double verticalSpeed=clamp(segDy*0.9,-Math.max(1.0,maxSpeed),Math.max(1.0,maxSpeed));
        if (planned.size() <= 1 && obstacleSensor != null && !segmentClear(state,new Vec3(targetX,targetY,targetZ),radius,Math.max(.5,state.boundingHalfHeight))) {
            pathBlocked=true;
            horizontalSpeed=0.0;
        } else {
            pathBlocked=false;
        }

        sp.yawIsRateNotHeading=false;
        sp.desiredYaw=desiredVesselYaw;

        // Keep the desired translation in WORLD space. This is intentionally independent of
        // heading so a vessel can begin translating toward the target while it turns to present
        // the Flight Computer's back to the route. The subsequent body-frame conversion preserves
        // the sign of the requested north/south/east/west velocity.
        double worldVx=Math.sin(bearing)*horizontalSpeed;
        double worldVz=Math.cos(bearing)*horizontalSpeed;
        double cosYaw=Math.cos(-state.yaw), sinYaw=Math.sin(-state.yaw);
        sp.desiredLongitudinalVelocity=worldVz*cosYaw-worldVx*sinYaw;
        sp.desiredLateralVelocity=worldVz*sinYaw+worldVx*cosYaw;
        sp.desiredVerticalVelocity=verticalSpeed;
        return sp;
    }

    private List<Vec3> buildClearRoute(VehicleState state, Vec3 target) {
        List<Vec3> direct=List.of(new Vec3(state.x,state.y,state.z),target);
        if(obstacleSensor==null || segmentClear(state,target,Math.max(.5,state.boundingRadius),Math.max(.5,state.boundingHalfHeight))) return direct;

        double targetBearing=Math.atan2(target.x-state.x,target.z-state.z);
        double distance=Math.sqrt((target.x-state.x)*(target.x-state.x)+(target.z-state.z)*(target.z-state.z));
        double lookahead=Math.min(Math.max(8.0,distance*0.35),LOOKAHEAD_DISTANCE);
        double[] offsets={-90,-60,-35,-20,20,35,60,90};
        double[] vertical={-8,-4,0,4,8};
        Vec3 best=null; double bestScore=Double.POSITIVE_INFINITY;
        for(double offset:offsets){
            double h=targetBearing+Math.toRadians(offset);
            for(double vy:vertical){
                Vec3 candidate=new Vec3(state.x+Math.sin(h)*lookahead,state.y+vy,state.z+Math.cos(h)*lookahead);
                if(!segmentClear(state,candidate,Math.max(.5,state.boundingRadius),Math.max(.5,state.boundingHalfHeight))) continue;
                VehicleState candidateState = new VehicleState();
                candidateState.x = candidate.x; candidateState.y = candidate.y; candidateState.z = candidate.z;
                if(!segmentClear(candidateState,target,Math.max(.5,state.boundingRadius),Math.max(.5,state.boundingHalfHeight))) continue;
                double score=candidate.distanceTo(target)+candidate.distanceTo(new Vec3(state.x,state.y,state.z))*0.15+Math.abs(offset)*0.12+Math.abs(vy)*0.6;
                if(score<bestScore){bestScore=score;best=candidate;}
            }
        }
        if(best==null) return direct;
        return List.of(new Vec3(state.x,state.y,state.z),best,target);
    }

    private boolean segmentClear(VehicleState origin,Vec3 end,double radius,double halfHeight){
        if(obstacleSensor==null)return true;
        double dx=end.x-origin.x,dy=end.y-origin.y,dz=end.z-origin.z;
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1.0e-6)return true;
        double nx=dx/length,ny=dy/length,nz=dz/length;
        double perp=Math.sqrt(nx*nx+nz*nz);
        double px=perp>1.0e-6?-nz:1,pz=perp>1.0e-6?nx:0;
        double[][] offsets={{0,0,0},{px*radius,0,pz*radius},{-px*radius,0,-pz*radius},{0,halfHeight,0},{0,-halfHeight,0}};
        for(double[] off:offsets){double hit=obstacleSensor.raycast(origin.x+off[0],origin.y+off[1],origin.z+off[2],nx,ny,nz,length+1.0);if(hit>=0&&hit<length+1.0)return false;}
        return true;
    }

    private double simulateRemaining(double start,double closing){double r=start;for(int i=0;i<HORIZON_STEPS;i++)r=Math.max(0,r-closing*HORIZON_DT);return r;}
    private static double normalizeRadians(double radians){double r=radians%(Math.PI*2.0);if(r>Math.PI)r-=Math.PI*2;if(r<-Math.PI)r+=Math.PI*2;return r;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}