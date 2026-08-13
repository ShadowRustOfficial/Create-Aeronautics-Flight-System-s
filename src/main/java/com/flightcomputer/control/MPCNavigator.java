package com.flightcomputer.control;

/** Cheap sampling-based receding-horizon outer-loop guidance for CRUISE. */
public final class MPCNavigator {
    private static final int HORIZON_STEPS = 10;
    private static final double HORIZON_DT = 0.3;
    private static final double[] SPEED_FRACTIONS = {0.0, 0.25, 0.5, 0.75, 1.0};
    private static final double[] HEADING_OFFSETS_DEG = {-90, -60, -40, -25, -12, 0, 12, 25, 40, 60, 90};
    private static final double[] VERTICAL_OFFSET_FRACTIONS = {-1.0, -0.5, 0.0, 0.5, 1.0};
    private static final double ARRIVAL_RADIUS = 1.5;
    private static final double ARRIVAL_HOLD_P = 0.55;
    private static final double ARRIVAL_MAX_SPEED = 1.25;
    private static final double CLEARANCE_MARGIN = 1.0;
    private static final double HYSTERESIS_BONUS = 6.0;
    private static final double HEADING_PENALTY_WEIGHT = 1.0;
    private static final double ALTITUDE_PENALTY_WEIGHT = 5.0;
    private static final double EFFORT_WEIGHT = 0.03;

    private final ObstacleSensor obstacleSensor;
    private double targetX, targetY, targetZ;
    private boolean hasTarget;
    private boolean arrivalLocked;
    private double lastChosenHeadingOffsetDeg;
    private boolean pathBlocked;

    public MPCNavigator(ObstacleSensor obstacleSensor) { this.obstacleSensor = obstacleSensor; }
    public MPCNavigator() { this(null); }
    public void setTarget(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        targetX=x; targetY=y; targetZ=z; hasTarget=true; arrivalLocked=false; lastChosenHeadingOffsetDeg=0; pathBlocked=false;
    }
    public void clearTarget() { hasTarget=false; arrivalLocked=false; pathBlocked=false; }
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

    public double targetBearing(VehicleState state) {
        if (!hasTarget || state == null) return state == null ? 0.0D : state.yaw;
        return Math.atan2(targetX - state.x, targetZ - state.z);
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
            sp.desiredYaw=state.yaw;
            sp.yawIsRateNotHeading=false;
            double sinYaw=Math.sin(state.yaw), cosYaw=Math.cos(state.yaw);
            double worldCorrectionX=Math.max(-ARRIVAL_MAX_SPEED, Math.min(ARRIVAL_MAX_SPEED, dx * ARRIVAL_HOLD_P));
            double worldCorrectionZ=Math.max(-ARRIVAL_MAX_SPEED, Math.min(ARRIVAL_MAX_SPEED, dz * ARRIVAL_HOLD_P));
            double worldCorrectionY=Math.max(-ARRIVAL_MAX_SPEED, Math.min(ARRIVAL_MAX_SPEED, dy * ARRIVAL_HOLD_P));
            sp.desiredLongitudinalVelocity=worldCorrectionX*sinYaw+worldCorrectionZ*cosYaw;
            sp.desiredLateralVelocity=worldCorrectionX*cosYaw-worldCorrectionZ*sinYaw;
            sp.desiredVerticalVelocity=worldCorrectionY;
            return sp;
        }
        if (fullDistance > ARRIVAL_RADIUS * 4.0D) arrivalLocked = false;

        double bearing=Math.atan2(dx,dz);
        double radius=Math.max(0.5,state.boundingRadius), halfHeight=Math.max(0.5,state.boundingHalfHeight);
        double safeDeceleration=Math.max(0.5,maxDeceleration);
        double bestScore=Double.POSITIVE_INFINITY, bestHeading=bearing, bestSpeed=0, bestVerticalSpeed=0;
        boolean accepted=false;

        for (double speedFrac:SPEED_FRACTIONS) {
            double candidateSpeed=speedFrac*Math.max(0.0, maxSpeed);
            double stoppingDistance=(candidateSpeed*candidateSpeed)/(2*safeDeceleration);
            double requiredClearance=stoppingDistance+radius+CLEARANCE_MARGIN;
            for (double headingOffsetDeg:HEADING_OFFSETS_DEG) {
                double heading=bearing+Math.toRadians(headingOffsetDeg);
                double dirX=Math.sin(heading), dirZ=Math.cos(heading);
                for (double vFrac:VERTICAL_OFFSET_FRACTIONS) {
                    double verticalSpeed=vFrac*Math.max(0.0, maxSpeed)*0.5;
                    double horizontalSpeed=Math.sqrt(Math.max(0.0, candidateSpeed*candidateSpeed-verticalSpeed*verticalSpeed));
                    double velocityX=dirX*horizontalSpeed;
                    double velocityZ=dirZ*horizontalSpeed;
                    boolean clear=candidateSpeed<=1.0e-6 || isClear(state,velocityX,verticalSpeed,velocityZ,radius,halfHeight,requiredClearance);
                    if (!clear) continue;
                    double closingSpeed=horizontalSpeed*Math.cos(Math.toRadians(headingOffsetDeg));
                    double predictedRemaining=simulateRemaining(flatDist,closingSpeed);
                    double effortPenalty=EFFORT_WEIGHT*candidateSpeed*candidateSpeed;
                    double headingPenalty=HEADING_PENALTY_WEIGHT*Math.abs(headingOffsetDeg);
                    double altitudePenalty=ALTITUDE_PENALTY_WEIGHT*Math.abs(vFrac);
                    double hysteresis=Math.abs(headingOffsetDeg-lastChosenHeadingOffsetDeg)<1.0e-6?-HYSTERESIS_BONUS:0;
                    double score=predictedRemaining*predictedRemaining+effortPenalty+headingPenalty+altitudePenalty+hysteresis;
                    if(score<bestScore){bestScore=score;bestHeading=heading;bestSpeed=horizontalSpeed;bestVerticalSpeed=verticalSpeed;accepted=true;}
                }
            }
        }
        pathBlocked=!accepted;
        if(!accepted){sp.desiredYaw=state.yaw;return sp;}

        double guidanceHeading = obstacleSensor == null ? bearing : bestHeading;
        double guidanceSpeed = bestSpeed;
        lastChosenHeadingOffsetDeg=normalizeDegrees(Math.toDegrees(guidanceHeading-bearing));
        sp.yawIsRateNotHeading=false;
        sp.desiredYaw=guidanceHeading;
        double worldVx=Math.sin(guidanceHeading)*guidanceSpeed;
        double worldVz=Math.cos(guidanceHeading)*guidanceSpeed;
        double cosYaw=Math.cos(-state.yaw), sinYaw=Math.sin(-state.yaw);
        sp.desiredLongitudinalVelocity=worldVz*cosYaw-worldVx*sinYaw;
        sp.desiredLateralVelocity=worldVz*sinYaw+worldVx*cosYaw;
        sp.desiredVerticalVelocity=bestVerticalSpeed;
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
}
