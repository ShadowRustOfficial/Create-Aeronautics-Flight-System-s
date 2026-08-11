package com.flightcomputer.control;

import java.util.Map;

/** One independent controller instance. Stabilisation and autopilot are independent objectives. */
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
    public ThrustAllocator getAllocator() { return allocator; }
    public MPCNavigator getNavigator() { return navigator; }
    public FlightMode getMode() { return mode; }
    public SixAxisStabilizer getStabilizeStabilizer() { return stabilizeStabilizer; }
    public SixAxisStabilizer getCruiseStabilizer() { return cruiseStabilizer; }

    public void setManualInput(double pitch, double roll, double yawRate, double vertical, double longitudinal, double lateral) {
        pitchStick = pitch;
        rollStick = roll;
        yawRateStick = yawRate;
        verticalStick = vertical;
        longitudinalStick = longitudinal;
        lateralStick = lateral;
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

    public void tick(double dt) { tick(dt, true, navigator.hasTarget()); }

    /**
     * Runs the two control layers independently. Autopilot does not require the manual stabiliser
     * flag, and stabilisation does not require a navigation target.
     */
    public void tick(double dt, boolean stabiliserEnabled, boolean autopilotEnabled) {
        VehicleState state = stateProvider.getState();
        if (state == null) return;

        Map<ControlAxis, Double> stabiliseCommands = Map.of();
        if (stabiliserEnabled) {
            StabilizationSetpoint stabiliseSetpoint = StabilizationSetpoint.manualNudge(
                    pitchStick, rollStick, yawRateStick, verticalStick, longitudinalStick, lateralStick,
                    maxManualTiltRadians, maxManualYawRate, maxManualSpeed);
            stabiliseCommands = stabilizeStabilizer.computeCommands(state, stabiliseSetpoint, dt);
        } else {
            stabilizeStabilizer.resetAll();
        }

        Map<ControlAxis, Double> autopilotCommands = Map.of();
        if (autopilotEnabled && navigator.hasTarget()) {
            ticksSinceReplan++;
            if (ticksSinceReplan >= Math.max(1, replanIntervalTicks) || navigator.distanceToTarget(state) < 3.0) {
                latestCruiseSetpoint = navigator.plan(state, cruiseMaxSpeed, estimateCruiseDeceleration(state));
                ticksSinceReplan = 0;
            }
            autopilotCommands = cruiseStabilizer.computeCommands(state, latestCruiseSetpoint, dt);
            mode = FlightMode.CRUISE;
            if (navigator.distanceToTarget(state) < 1.0) disengageCruise();
        } else {
            cruiseStabilizer.resetAll();
            ticksSinceReplan = 0;
        }

        allocator.applyCombined(registry, state, stabiliseCommands, autopilotCommands);
    }

    private double estimateCruiseDeceleration(VehicleState state) {
        double authority = registry.getVectorAuthority(FlightMode.CRUISE, VectorDirection.NORTH)
                + registry.getVectorAuthority(FlightMode.CRUISE, VectorDirection.SOUTH);
        return Math.max(0.5, authority / Math.max(state.mass, 1.0e-3));
    }
}
