package com.flightcomputer.control;

import java.util.List;
import java.util.Map;

/** One controller instance per craft. STABILIZE and CRUISE each own an independent 6-vector set. */
public final class FlightComputer {
    private final VehicleStateProvider stateProvider;
    private final ThrusterRegistry registry = new ThrusterRegistry();
    private final SixAxisStabilizer stabilizeStabilizer = new SixAxisStabilizer();
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

    public FlightComputer(VehicleStateProvider stateProvider, ObstacleSensor obstacleSensor) {
        this.stateProvider=stateProvider; this.navigator=new MPCNavigator(obstacleSensor);
    }
    public FlightComputer(VehicleStateProvider stateProvider){this(stateProvider,null);}
    public ThrusterRegistry getRegistry(){return registry;}
    public MPCNavigator getNavigator(){return navigator;}
    public FlightMode getMode(){return mode;}
    public SixAxisStabilizer getStabilizeStabilizer(){return stabilizeStabilizer;}
    public SixAxisStabilizer getCruiseStabilizer(){return cruiseStabilizer;}

    public void setManualInput(double pitch,double roll,double yawRate,double vertical,double longitudinal,double lateral){
        pitchStick=pitch;rollStick=roll;yawRateStick=yawRate;verticalStick=vertical;longitudinalStick=longitudinal;lateralStick=lateral;
    }

    public List<ControlAxis> engageCruise(double targetX,double targetY,double targetZ){
        List<ControlAxis> missing=registry.getUnlinkedAxes(FlightMode.CRUISE);
        if(!missing.isEmpty())return missing;
        navigator.setTarget(targetX,targetY,targetZ);cruiseStabilizer.resetAll();ticksSinceReplan=0;mode=FlightMode.CRUISE;return null;
    }
    public void disengageCruise(){mode=FlightMode.STABILIZE;navigator.clearTarget();latestCruiseSetpoint=StabilizationSetpoint.hover();}
    public double distanceToTarget(){return navigator.hasTarget()?navigator.distanceToTarget(stateProvider.getState()):-1;}
    public boolean isCruisePathBlocked(){return mode==FlightMode.CRUISE&&navigator.isPathBlocked();}

    public void tick(double dt){
        VehicleState state=stateProvider.getState();if(state==null)return;
        StabilizationSetpoint stabilizeSetpoint=mode==FlightMode.STABILIZE
                ?StabilizationSetpoint.manualNudge(pitchStick,rollStick,yawRateStick,verticalStick,longitudinalStick,lateralStick,maxManualTiltRadians,maxManualYawRate,maxManualSpeed)
                :StabilizationSetpoint.hover();
        Map<ControlAxis,Double> stabilizeCommands=stabilizeStabilizer.computeCommands(state,stabilizeSetpoint,dt);
        allocator.apply(registry,FlightMode.STABILIZE,stabilizeCommands);

        if(mode==FlightMode.CRUISE){
            ticksSinceReplan++;
            if(ticksSinceReplan>=Math.max(1,replanIntervalTicks)||navigator.distanceToTarget(state)<3.0){
                latestCruiseSetpoint=navigator.plan(state,cruiseMaxSpeed,estimateCruiseDeceleration(state));ticksSinceReplan=0;
            }
            allocator.apply(registry,FlightMode.CRUISE,cruiseStabilizer.computeCommands(state,latestCruiseSetpoint,dt));
            if(navigator.distanceToTarget(state)<1.0)disengageCruise();
        }
    }

    private double estimateCruiseDeceleration(VehicleState state){
        double authority=registry.getAxisAuthority(FlightMode.CRUISE,ControlAxis.LONGITUDINAL);
        return Math.max(0.5,authority/Math.max(state.mass,1.0e-3));
    }
}
