package com.flightcomputer.control;

import com.flightcomputer.avionics.FlightControllerState;
import java.util.EnumMap;
import java.util.Map;

/** One independent controller instance. Stabilisation and autopilot are independent objectives. */
public final class FlightComputer {
    private final VehicleStateProvider stateProvider;
    private final ThrusterRegistry registry = new ThrusterRegistry();
    private final SixAxisStabilizer stabilizeStabilizer = new SixAxisStabilizer(true);
    private final SixAxisStabilizer cruiseStabilizer = new SixAxisStabilizer();
    private final ThrustAllocator allocator = new ThrustAllocator();
    private final MPCNavigator navigator;
    private FlightMode mode = FlightMode.STABILIZE;
    private double pitchStick, rollStick, yawRateStick, verticalStick, longitudinalStick, lateralStick;
    public double maxManualTiltRadians = Math.toRadians(25);
    public double maxManualYawRate = Math.toRadians(60);
    public double maxManualSpeed = 6.0;
    public double cruiseMaxSpeed = 20.0;
    public int replanIntervalTicks = 4;
    private int ticksSinceReplan;
    private StabilizationSetpoint latestCruiseSetpoint = StabilizationSetpoint.hover();
    private boolean altitudeHold, headingHold, positionHold, velocityHold;
    private boolean previousAltitudeHold, previousHeadingHold, previousPositionHold, previousVelocityHold;
    private double holdX, holdY, holdZ, holdYaw;
    private double holdVx, holdVy, holdVz;
    private double altitudeHoldTargetY = Double.NaN;
    private double lastCruiseLongitudinalVelocity;
    private double lastAppliedTargetX, lastAppliedTargetY, lastAppliedTargetZ;
    private boolean lastAppliedTargetValid;

    // External-physics disturbance recovery. Sable/Physics Staff can change a sublevel's pose and
    // velocity independently of this controller; never feed that discontinuity directly into PID.
    private VehicleState previousControlState;
    private int disturbanceRecoveryTicks;
    private static final int DISTURBANCE_RECOVERY_TICKS = 10;
    private static final double DISTURBANCE_POSITION_DELTA = 4.0;
    private static final double DISTURBANCE_SPEED_DELTA = 12.0;
    private static final double DISTURBANCE_ANGULAR_RATE_DELTA = 2.5;
    private static final double DISTURBANCE_ANGLE_DELTA = Math.toRadians(35.0);

    public FlightComputer(VehicleStateProvider stateProvider, ObstacleSensor obstacleSensor) { this.stateProvider = stateProvider; this.navigator = new MPCNavigator(obstacleSensor); }
    public FlightComputer(VehicleStateProvider stateProvider) { this(stateProvider, null); }
    public ThrusterRegistry getRegistry() { return registry; }
    public ThrustAllocator getAllocator() { return allocator; }
    public MPCNavigator getNavigator() { return navigator; }
    public FlightMode getMode() { return mode; }
    public SixAxisStabilizer getStabilizeStabilizer() { return stabilizeStabilizer; }
    public SixAxisStabilizer getCruiseStabilizer() { return cruiseStabilizer; }
    public void setManualInput(double pitch,double roll,double yawRate,double vertical,double longitudinal,double lateral){pitchStick=pitch;rollStick=roll;yawRateStick=yawRate;verticalStick=vertical;longitudinalStick=longitudinal;lateralStick=lateral;}
    public void setAltitudeHoldTarget(double y) { altitudeHoldTargetY = Double.isFinite(y) ? y : Double.NaN; }
    /** Replaces the active navigation target and invalidates any cached cruise setpoint immediately. */
    public void setNavigationTarget(double targetX, double targetY, double targetZ) {
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) return;
        boolean changed = !navigator.hasTarget()
                || Math.abs(navigator.targetX() - targetX) > 1.0e-6
                || Math.abs(navigator.targetY() - targetY) > 1.0e-6
                || Math.abs(navigator.targetZ() - targetZ) > 1.0e-6;
        navigator.setTarget(targetX,targetY,targetZ);
        if (changed) resetCruiseGuidance();
        lastAppliedTargetX=targetX; lastAppliedTargetY=targetY; lastAppliedTargetZ=targetZ; lastAppliedTargetValid=true;
    }
    /** Clears the active navigation target and all cached guidance derived from it. */
    public void clearNavigationTarget() {
        navigator.clearTarget();
        resetCruiseGuidance();
        lastAppliedTargetValid=false;
    }
    private void resetCruiseGuidance() {
        cruiseStabilizer.resetAll();
        ticksSinceReplan=0;
        lastCruiseLongitudinalVelocity=0.0D;
        latestCruiseSetpoint=StabilizationSetpoint.hover();
    }
    private void synchronizeExternalTarget() {
        if (!navigator.hasTarget()) {
            if (lastAppliedTargetValid) {
                // Runtime target is still authoritative. Re-arm the navigator if it was cleared by
                // arrival handling, a transient controller reset, or another control-layer event.
                navigator.setTarget(lastAppliedTargetX,lastAppliedTargetY,lastAppliedTargetZ);
                resetCruiseGuidance();
            }
            return;
        }
        double x=navigator.targetX(), y=navigator.targetY(), z=navigator.targetZ();
        if (!lastAppliedTargetValid
                || Math.abs(lastAppliedTargetX-x)>1.0e-6
                || Math.abs(lastAppliedTargetY-y)>1.0e-6
                || Math.abs(lastAppliedTargetZ-z)>1.0e-6) {
            resetCruiseGuidance();
            lastAppliedTargetX=x; lastAppliedTargetY=y; lastAppliedTargetZ=z; lastAppliedTargetValid=true;
        }
    }
    /** Mirrors the known-good 0.6.8 hold capture path: state changes capture new targets, but do not reset every PID loop. */
    public void syncHolds(FlightControllerState controllerState, VehicleState state) {
        if(controllerState==null||state==null)return;
        altitudeHold=controllerState.altitudeHold(); headingHold=controllerState.headingHold(); positionHold=controllerState.positionHold(); velocityHold=controllerState.velocityHold();
        if(altitudeHold && Double.isFinite(altitudeHoldTargetY)) holdY=altitudeHoldTargetY;
        else if(altitudeHold&&!previousAltitudeHold)holdY=state.y;
        if(headingHold&&!previousHeadingHold)holdYaw=state.yaw;
        if(positionHold&&!previousPositionHold){holdX=state.x;holdY=state.y;holdZ=state.z;}
        if(velocityHold&&!previousVelocityHold){holdVx=state.vx;holdVy=state.vy;holdVz=state.vz;}
        previousAltitudeHold=altitudeHold;previousHeadingHold=headingHold;previousPositionHold=positionHold;previousVelocityHold=velocityHold;
    }
    public void engageCruise(double targetX,double targetY,double targetZ){setNavigationTarget(targetX,targetY,targetZ);mode=FlightMode.CRUISE;}
    public void disengageCruise(){mode=FlightMode.STABILIZE;clearNavigationTarget();}
    public double distanceToTarget(){VehicleState state=stateProvider.getState();return state!=null&&navigator.hasTarget()?navigator.distanceToTarget(state):-1;}
    public boolean isCruisePathBlocked(){return mode==FlightMode.CRUISE&&navigator.isPathBlocked();}
    public void tick(double dt){tick(dt,true,navigator.hasTarget());}
    /** Runs the active control layers once. Stabilisation and autopilot share the actuator allocator without disabling one another. */
    public void tick(double dt,boolean stabiliserEnabled,boolean autopilotEnabled){
        VehicleState state=stateProvider.getState(); if(state==null)return;
        if (detectPhysicsDisturbance(state)) {
            recoverFromPhysicsDisturbance();
        }
        synchronizeExternalTarget();
        Map<ControlAxis,Double> stabiliseCommands=Map.of();
        if(stabiliserEnabled){
            StabilizationSetpoint sp=StabilizationSetpoint.manualNudge(pitchStick,rollStick,yawRateStick,verticalStick,longitudinalStick,lateralStick,maxManualTiltRadians,maxManualYawRate,maxManualSpeed);
            applyHoldSetpoints(sp,state,!autopilotEnabled);
            stabiliseCommands=stabilizeStabilizer.computeCommands(state,sp,dt);
            if(autopilotEnabled) stabiliseCommands=filterStabilizerAxes(stabiliseCommands);
        }else stabilizeStabilizer.resetAll();
        Map<ControlAxis,Double> autopilotCommands=Map.of();
        if(autopilotEnabled&&navigator.hasTarget()){
            ticksSinceReplan++;
            if(ticksSinceReplan>=Math.max(1,replanIntervalTicks)||navigator.distanceToTarget(state)<3.0){
                latestCruiseSetpoint=navigator.plan(state,cruiseMaxSpeed,estimateCruiseDeceleration(state));
                ticksSinceReplan=0;
            }
            StabilizationSetpoint sp=latestCruiseSetpoint.copy();
            sp.desiredLongitudinalVelocity=smoothCruiseVelocity(state,sp.desiredYaw,sp.desiredLongitudinalVelocity,dt);
            if(altitudeHold && Double.isFinite(altitudeHoldTargetY))
                sp.desiredVerticalVelocity=clamp((altitudeHoldTargetY-state.y)*.9,-maxManualSpeed,maxManualSpeed);
            applyHoldSetpoints(sp,state,false);
            autopilotCommands=cruiseStabilizer.computeCommands(state,sp,dt);
            if(stabiliserEnabled) autopilotCommands=filterAutopilotAxes(autopilotCommands);
            mode=FlightMode.CRUISE;
            if(navigator.distanceToTarget(state)<1.0)disengageCruise();
        }else{cruiseStabilizer.resetAll();ticksSinceReplan=0;lastCruiseLongitudinalVelocity=0;}

        double recoveryScale = disturbanceRecoveryTicks > 0
                ? 1.0 - (0.65 * disturbanceRecoveryTicks / (double) DISTURBANCE_RECOVERY_TICKS)
                : 1.0;
        if (disturbanceRecoveryTicks > 0) disturbanceRecoveryTicks--;
        if (recoveryScale < 0.35) recoveryScale = 0.35;
        if (recoveryScale < 0.999) {
            stabiliseCommands = scaleCommands(stabiliseCommands, recoveryScale);
            autopilotCommands = scaleCommands(autopilotCommands, recoveryScale);
        }
        allocator.applyCombined(registry,state,stabiliseCommands,autopilotCommands);
        previousControlState = state.copy();
    }
    /** Smooths forward acceleration only after the vessel is pointed toward the MPC-selected heading. */
    private double smoothCruiseVelocity(VehicleState state,double desiredYaw,double desiredVelocity,double dt){
        double headingError=Math.abs(normalizeRadians(desiredYaw-state.yaw));
        double alignment=clamp(Math.cos(headingError),0.0,1.0);
        double alignedTarget=desiredVelocity*alignment;
        // Never allow a route to remain indefinitely at zero forward demand simply because the
        // ship is temporarily turning. A small forward crawl lets heading authority recover while
        // preserving the previous acceleration ramp.
        if (navigator.hasTarget() && navigator.distanceToTarget(state) > 3.0 && alignedTarget < 0.5)
            alignedTarget = Math.min(Math.max(1.25, desiredVelocity * 0.12), Math.max(0.0, cruiseMaxSpeed));
        double maxDelta=Math.max(0.25,4.0*Math.max(dt,0.0));
        double delta=clamp(alignedTarget-lastCruiseLongitudinalVelocity,-maxDelta,maxDelta);
        lastCruiseLongitudinalVelocity+=delta;
        return lastCruiseLongitudinalVelocity;
    }
    private static double normalizeRadians(double radians){double r=radians%(Math.PI*2.0);if(r>Math.PI)r-=Math.PI*2.0;if(r<-Math.PI)r+=Math.PI*2.0;return r;}
    /** Detects abrupt Sable pose/velocity changes such as Physics Staff manipulation or collisions. */
    private boolean detectPhysicsDisturbance(VehicleState state) {
        if (previousControlState == null) return false;
        double dp = Math.sqrt(
                sq(state.x - previousControlState.x)
                        + sq(state.y - previousControlState.y)
                        + sq(state.z - previousControlState.z));
        double dv = Math.sqrt(
                sq(state.vx - previousControlState.vx)
                        + sq(state.vy - previousControlState.vy)
                        + sq(state.vz - previousControlState.vz));
        double da = Math.sqrt(
                sq(state.pitchRate - previousControlState.pitchRate)
                        + sq(state.yawRate - previousControlState.yawRate)
                        + sq(state.rollRate - previousControlState.rollRate));
        double dang = Math.sqrt(
                sq(normalizeRadians(state.pitch - previousControlState.pitch))
                        + sq(normalizeRadians(state.yaw - previousControlState.yaw))
                        + sq(normalizeRadians(state.roll - previousControlState.roll)));
        return dp > DISTURBANCE_POSITION_DELTA
                || dv > DISTURBANCE_SPEED_DELTA
                || da > DISTURBANCE_ANGULAR_RATE_DELTA
                || dang > DISTURBANCE_ANGLE_DELTA;
    }
    private void recoverFromPhysicsDisturbance() {
        stabilizeStabilizer.resetAll();
        cruiseStabilizer.resetAll();
        allocator.hardStop();
        ticksSinceReplan = 0;
        lastCruiseLongitudinalVelocity = 0.0D;
        latestCruiseSetpoint = StabilizationSetpoint.hover();
        disturbanceRecoveryTicks = DISTURBANCE_RECOVERY_TICKS;
    }
    private static double sq(double value) { return value * value; }
    private static Map<ControlAxis,Double> scaleCommands(Map<ControlAxis,Double> source, double scale) {
        if(source==null||source.isEmpty())return Map.of();
        Map<ControlAxis,Double> result=new EnumMap<>(ControlAxis.class);
        for(Map.Entry<ControlAxis,Double> entry:source.entrySet()) result.put(entry.getKey(), entry.getValue() * scale);
        return result;
    }
    /** When both systems are active, the stabiliser keeps the vessel level while autopilot owns navigation yaw. */
    private static Map<ControlAxis,Double> filterStabilizerAxes(Map<ControlAxis,Double> source){
        if(source==null||source.isEmpty())return Map.of();
        Map<ControlAxis,Double> result=new EnumMap<>(ControlAxis.class);
        for(Map.Entry<ControlAxis,Double> entry:source.entrySet()) {
            ControlAxis axis=entry.getKey();
            if(axis==ControlAxis.PITCH||axis==ControlAxis.ROLL) result.put(axis,entry.getValue());
        }
        return result;
    }
    /** When both systems are active, autopilot owns translation and yaw so it can actually navigate to the target. */
    private static Map<ControlAxis,Double> filterAutopilotAxes(Map<ControlAxis,Double> source){
        if(source==null||source.isEmpty())return Map.of();
        Map<ControlAxis,Double> result=new EnumMap<>(ControlAxis.class);
        for(Map.Entry<ControlAxis,Double> entry:source.entrySet()) {
            ControlAxis axis=entry.getKey();
            if(axis==ControlAxis.VERTICAL||axis==ControlAxis.LONGITUDINAL||axis==ControlAxis.LATERAL||axis==ControlAxis.YAW) result.put(axis,entry.getValue());
        }
        return result;
    }
    private void applyHoldSetpoints(StabilizationSetpoint sp,VehicleState state,boolean allowPosition){
        if(headingHold){sp.desiredYaw=holdYaw;sp.yawIsRateNotHeading=false;}
        if(positionHold&&allowPosition){double dx=holdX-state.x,dz=holdZ-state.z;double[] body=worldToBodyVelocity(dx,dz,state.yaw);sp.desiredLongitudinalVelocity=clamp(body[0]*.85,-maxManualSpeed,maxManualSpeed);sp.desiredLateralVelocity=clamp(body[1]*.85,-maxManualSpeed,maxManualSpeed);}
        else if(velocityHold&&allowPosition){double[] targetBody=worldToBodyVelocity(holdVx,holdVz,state.yaw);sp.desiredLongitudinalVelocity=clamp(targetBody[0],-maxManualSpeed,maxManualSpeed);sp.desiredLateralVelocity=clamp(targetBody[1],-maxManualSpeed,maxManualSpeed);}
        if(positionHold||altitudeHold)sp.desiredVerticalVelocity=clamp((holdY-state.y)*.9,-maxManualSpeed,maxManualSpeed); else if(velocityHold)sp.desiredVerticalVelocity=clamp(holdVy,-maxManualSpeed,maxManualSpeed);
    }
    private static double[] worldToBodyVelocity(double vx,double vz,double yaw){double cos=Math.cos(-yaw),sin=Math.sin(-yaw);return new double[]{vz*cos-vx*sin,vz*sin+vx*cos};}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private double estimateCruiseDeceleration(VehicleState state){double authority=registry.getVectorAuthority(FlightMode.CRUISE,VectorDirection.NORTH)+registry.getVectorAuthority(FlightMode.CRUISE,VectorDirection.SOUTH);return Math.max(.5,authority/Math.max(state.mass,1.0e-3));}
}
