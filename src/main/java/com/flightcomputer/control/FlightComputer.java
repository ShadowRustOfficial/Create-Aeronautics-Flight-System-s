package com.flightcomputer.control;

import java.util.List;
import java.util.Map;

/** One independent controller instance. Stabilisation and autopilot are concurrent objectives. */
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
        this.stateProvider = stateProvider;
        this.navigator = new MPCNavigator(obstacleSensor);
    }
    public FlightComputer(VehicleStateProvider stateProvider) { this(stateProvider, null); }
    public ThrusterRegistry getRegistry() { return registry; }
    public MPCNavigator getNavigator() { return navigator; }
    public FlightMode getMode() { return mode; }
    public SixAxisStabilizer getStabilizeStabilizer() { return stabilizeStabilizer; }
    public SixAxisStabilizer getCruiseStabilizer() { return cruiseStabilizer; }

    public void setManualInput(double pitch, double roll, double yawRate, double vertical,
                               double longitudinal, double lateral) {
        pitchStick = pitch; rollStick = roll; yawRateStick = yawRate;
        verticalStick = vertical; longitudinalStick = longitudinal; lateralStick = lateral;
    }

    public void engageCruise(double targetX, double targetY, double targetZ) {
        navigator.setTarget(targetX, targetY, targetZ);
        cruiseStabilizer.resetAll();
        ticksSinceReplan = 0;
        mode = FlightMode.CRUISE;
    }

    public void disengageCruise() {
        mode = FlightMode.STABILIZE;
        navigator.clearTarget();
        latestCruiseSetpoint = StabilizationSetpoint.hover();
    }

    public double distanceToTarget() {
        VehicleState state = stateProvider.getState();
        return state != null && navigator.hasTarget() ? navigator.distanceToTarget(state) : -1;
    }

    public boolean isCruisePathBlocked() { return mode == FlightMode.CRUISE && navigator.isPathBlocked(); }

    public void tick(double dt) {
        VehicleState state = stateProvider.getState();
        if (state == null) return;

        // Stabilisation is always solved. Autopilot adds a second objective rather than replacing it.
        StabilizationSetpoint stabiliseSetpoint = StabilizationSetpoint.manualNudge(
                pitchStick, rollStick, yawRateStick, verticalStick, longitudinalStick, lateralStick,
                maxManualTiltRadians, maxManualYawRate, maxManualSpeed);
        Map<ControlAxis, Double> stabiliseCommands = stabilizeStabilizer.computeCommands(state, stabiliseSetpoint, dt);

        Map<ControlAxis, Double> autopilotCommands = Map.of();
        if (navigator.hasTarget()) {
            ticksSinceReplan++;
            if (ticksSinceReplan >= Math.max(1, replanIntervalTicks) || navigator.distanceToTarget(state) < 3.0) {
                latestCruiseSetpoint = navigator.plan(state, cruiseMaxSpeed, estimateCruiseDeceleration(state));
                ticksSinceReplan = 0;
            }
            autopilotCommands = cruiseStabilizer.computeCommands(state, latestCruiseSetpoint, dt);
            mode = FlightMode.CRUISE;
            if (navigator.distanceToTarget(state) < 1.0) disengageCruise();
        }

        // One allocator pass produces one final command per physical thruster.
        allocator.applyCombined(registry, stabiliseCommands, autopilotCommands);
    }

    private double estimateCruiseDeceleration(VehicleState state) {
        double authority = registry.getVectorAuthority(FlightMode.CRUISE, VectorDirection.NORTH)
                + registry.getVectorAuthority(FlightMode.CRUISE, VectorDirection.SOUTH);
        return Math.max(0.5, authority / Math.max(state.mass, 1.0e-3));
    }
}
