package com.flightcomputer.control;

/** Cheap sampling-based receding-horizon outer-loop guidance for CRUISE. */
public final class MPCNavigator {
    private static final int HORIZON_STEPS = 10;
    private static final double HORIZON_DT = 0.3;
    private static final double[] SPEED_FRACTIONS = {0.0, 0.25, 0.5, 0.75, 1.0};
    private static final double[] HEADING_OFFSETS_DEG = {-90, -60, -40, -25, -12, 0, 12, 25, 40, 60, 90};
    private static final double ARRIVAL_RADIUS = 1.5;
    private static final double CLEARANCE_MARGIN = 1.0;
    private static final double HYSTERESIS_BONUS = 6.0;
    private static final double HEADING_PENALTY_WEIGHT = 1.0;
    private static final double EFFORT_WEIGHT = 0.03;
    private static final double MIN_HORIZONTAL_GUIDANCE_DISTANCE = 3.0;
    private static final double MIN_HORIZONTAL_GUIDANCE_SPEED_FRACTION = 0.25;
    private static final double MIN_HORIZONTAL_GUIDANCE_SPEED = 1.0;

    private final ObstacleSensor obstacleSensor;
    private double targetX, targetY, targetZ;
    private boolean hasTarget;
    private double lastChosenHeadingOffsetDeg;
    private boolean pathBlocked;

    public MPCNavigator(ObstacleSensor obstacleSensor) { this.obstacleSensor = obstacleSensor; }
    public MPCNavigator() { this(null); }
    public void setTarget(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        targetX=x; targetY=y; targetZ=z; hasTarget=true; lastChosenHeadingOffsetDeg=0; pathBlocked=false;
    }
    public void clearTarget() { hasTarget=false; pathBlocked=false; }
    public boolean hasTarget() { return hasTarget; }
    public boolean isPathBlocked() { return pathBlocked; }
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public double targetZ() { return targetZ; }
    public double distanceToTarget(VehicleState state) {
        if (state == null || !hasTarget) return -1.0D;
        double dx=targetX-state.x, dy=targetY-state.y, dz=targetZ-state.z;
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    /** Returns the direct world-space bearing from the vessel to the active target. */
    public double targetBearing(VehicleState state) {
        if (!hasTarget || state == null) return state == null ? 0.0D : state.yaw;
        return Math.atan2(targetX - state.x, targetZ - state.z);
    }

    public StabilizationSetpoint plan(VehicleState state, double maxSpeed, double maxDeceleration) {
        StabilizationSetpoint sp = StabilizationSetpoint.hover();
        if (!hasTarget || state == null) return sp;
        double dx=targetX-state.x, dy=targetY-state.y, dz=targetZ-state.z;
        double flatDist=Math.sqrt(dx*dx+dz*dz);
        if (flatDist < ARRIVAL_RADIUS && Math.abs(dy) < ARRIVAL_RADIUS) {
            pathBlocked=false;
            sp.desiredYaw=state.yaw;
            sp.yawIsRateNotHeading=false;
            return sp;
        }

        double bearing=Math.atan2(dx,dz);
        double safeDeceleration=Math.max(0.5,maxDeceleration);
        double targetVerticalSpeed = clamp(dy * 0.75, -Math.max(2.0, maxSpeed * 0.5), Math.max(2.0, maxSpeed * 0.5));
        double radius=Math.max(0.5,state.boundingRadius), halfHeight=Math.max(0.5,state.boundingHalfHeight);
        double bestScore=Double.POSITIVE_INFINITY, bestHeading=bearing, bestSpeed=0;
        boolean accepted=false;

        for (double speedFrac:SPEED_FRACTIONS) {
            double candidateSpeed=speedFrac*Math.max(0.0, maxSpeed);
            double stoppingDistance=(candidateSpeed*candidateSpeed)/(2*safeDeceleration);
            double requiredClearance=stoppingDistance+radius+CLEARANCE_MARGIN;
            for (double headingOffsetDeg:HEADING_OFFSETS_DEG) {
                double heading=bearing+Math.toRadians(headingOffsetDeg);
                double dirX=Math.sin(heading), dirZ=Math.cos(heading);
                double horizontalSpeed=candidateSpeed;
                double velocityX=dirX*horizontalSpeed;
                double velocityZ=dirZ*horizontalSpeed;
                boolean clear=candidateSpeed<=1.0e-6 || isClear(state,velocityX,targetVerticalSpeed,velocityZ,radius,halfHeight,requiredClearance);
                if (!clear) continue;
                double closingSpeed=horizontalSpeed*Math.cos(Math.toRadians(headingOffsetDeg));
                double predictedRemaining=simulateRemaining(flatDist,closingSpeed);
                double effortPenalty=EFFORT_WEIGHT*candidateSpeed*candidateSpeed;
                double headingPenalty=HEADING_PENALTY_WEIGHT*Math.abs(headingOffsetDeg);
                double hysteresis=Math.abs(headingOffsetDeg-lastChosenHeadingOffsetDeg)<1.0e-6?-HYSTERESIS_BONUS:0;
                double score=predictedRemaining*predictedRemaining+effortPenalty+headingPenalty+hysteresis;
                if(score<bestScore){bestScore=score;bestHeading=heading;bestSpeed=horizontalSpeed;accepted=true;}
            }
        }

        // A valid route must not silently collapse into a hover command while meaningful
        // horizontal separation remains. This is especially important when the sampled
        // horizon is temporarily conservative or a planner state has just been refreshed.
        if (accepted && flatDist >= MIN_HORIZONTAL_GUIDANCE_DISTANCE && bestSpeed <= 1.0e-6) {
            double fallbackSpeed=Math.min(Math.max(0.0, maxSpeed),
                    Math.max(MIN_HORIZONTAL_GUIDANCE_SPEED, maxSpeed * MIN_HORIZONTAL_GUIDANCE_SPEED_FRACTION));
            double fallbackX=Math.sin(bearing)*fallbackSpeed;
            double fallbackZ=Math.cos(bearing)*fallbackSpeed;
            double stoppingDistance=(fallbackSpeed*fallbackSpeed)/(2*safeDeceleration);
            double requiredClearance=stoppingDistance+radius+CLEARANCE_MARGIN;
            boolean clear=isClear(state,fallbackX,targetVerticalSpeed,fallbackZ,radius,halfHeight,requiredClearance);
            if (clear) {
                bestHeading=bearing;
                bestSpeed=fallbackSpeed;
            } else {
                accepted=false;
            }
        }

        pathBlocked=!accepted;
        if(!accepted){sp.desiredYaw=state.yaw;return sp;}

        /*
         * With no obstacle sensor the route must be followed directly. The sampled heading
         * offsets are useful for obstacle avoidance, but there is no reason to let the outer
         * loop choose a lateral heading when the route has no obstacle constraint. In particular,
         * a destination west/east of the current course must produce an unambiguous yaw command.
         */
        double guidanceHeading = obstacleSensor == null ? bearing : bestHeading;
        double guidanceSpeed = bestSpeed;
        lastChosenHeadingOffsetDeg=normalizeDegrees(Math.toDegrees(guidanceHeading-bearing));
        sp.yawIsRateNotHeading=false;
        sp.desiredYaw=guidanceHeading;

        // The inner stabilizer consumes longitudinal/lateral velocities in the vessel's BODY
        // frame. Convert the selected world velocity into the current body frame and provide
        // both horizontal components so route guidance does not collapse into forward-only flight.
        double worldVx=Math.sin(guidanceHeading)*guidanceSpeed;
        double worldVz=Math.cos(guidanceHeading)*guidanceSpeed;
        double cosYaw=Math.cos(-state.yaw), sinYaw=Math.sin(-state.yaw);
        sp.desiredLongitudinalVelocity=worldVz*cosYaw-worldVx*sinYaw;
        sp.desiredLateralVelocity=worldVz*sinYaw+worldVx*cosYaw;
        sp.desiredVerticalVelocity=targetVerticalSpeed;
        return sp;
    }

    private boolean isClear(VehicleState state,double dirX,double dirY,double dirZ,double radius,double halfHeight,double requiredClearance){
        if(obstacleSensor==null)return true;
        double len=Math.sqrt(dirX*dirX+dirY*dirY+dirZ*dirZ); if(len<1.0e-6)return true;
        double nx=dirX/len,ny=dirY/len,nz=dirZ/len;
        double perpLen=Math.sqrt(nx*nx+nz*nz);
        double px=perpLen>1.0e-6?-nz:1,pz=perpLen>1.0e-6?nx:0;
        double[][] origins={{0,0,0},{px*radius,0,pz*radius},{-px*radius,0,-pz*radius},{0,halfHeight,0},{0,-halfHeight,0}};
        for(double[] off:origins){double dist=obstacleSensor.raycast(state.x+off[0],state.y+off[1],state.z+off[2],nx,ny,nz,requiredClearance);if(dist>=0&&dist<requiredClearance)return false;}
        return true;
    }
    private double simulateRemaining(double start,double closing){double r=start;for(int i=0;i<HORIZON_STEPS;i++)r=Math.max(0,r-closing*HORIZON_DT);return r;}
    private static double normalizeDegrees(double deg){double d=deg%360;if(d>180)d-=360;if(d<-180)d+=360;return d;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}