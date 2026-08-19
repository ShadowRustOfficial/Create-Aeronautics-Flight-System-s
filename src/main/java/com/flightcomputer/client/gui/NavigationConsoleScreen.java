package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.FlightMode;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.client.FlightSetupTelemetryClient;
import com.flightcomputer.client.map.FlightControllerWorldPositionResolver;
import com.flightcomputer.client.map.FlightMapDiagnostics;
import com.flightcomputer.client.map.FlightMapMarker;
import com.flightcomputer.client.map.FlightMapPipeline;
import com.flightcomputer.client.map.FlightMapProviderKind;
import com.flightcomputer.client.map.LiveWorldMapProvider;
import com.flightcomputer.client.map.WaypointMapProvider;
import com.flightcomputer.client.map.WaystoneMapProvider;
import com.flightcomputer.identity.FlightIdentityAccess;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Coherent, responsive Navigation Console. UI-only layout; all actions remain server-authoritative. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }
    private enum TargetMode { PLAYER, HOME }

    private static final int PANEL = 0xF20C1117, MAP_BG = 0xFF05080B;
    private static final int CYAN = 0xFF55AAFF, BRIGHT = 0xFF66D9FF, GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555, TEXT = 0xFFE6EEF2, MUTED = 0xFF9DAEB5, WAYSTONE = 0xFFFFCC55;
    private static final int LINE = 0xFF29343C;

    private final BlockPos controllerPos;
    private final LiveWorldMapProvider mapProvider = new LiveWorldMapProvider();
    private final FlightMapPipeline mapPipeline = new FlightMapPipeline(mapProvider);
    private final FlightControllerWorldPositionResolver positionResolver = new FlightControllerWorldPositionResolver();
    private final WaystoneMapProvider waystones = new WaystoneMapProvider();
    private final WaypointMapProvider waypoints = new WaypointMapProvider();
    private final WaystoneMapProvider routeWaystones = new WaystoneMapProvider();
    private final WaypointMapProvider routeWaypoints = new WaypointMapProvider();

    private Tab tab = Tab.MAP;
    private TargetMode targetMode = TargetMode.PLAYER;
    private FlightControllerBlockEntity controller;
    private EditBox targetInput, altitudeTargetInput, targetPlayerInput, homeInput, nameInput, flightIdInput;
    private Button engageButton, stabiliserButton, modeButton, autopilotButton, altitudeButton, headingButton, positionButton, velocityButton, navigationButton, setAltitudeButton;
    private Button selectWaystoneButton, selectWaypointButton, targetPlayerButton, targetHomeButton;
    private boolean showTerrain = true, showFlightMap = true, showWaypoints = true;
    private boolean dragging, pendingWaystone, pendingWaypoint;
    private double centerX, centerZ, controllerX, controllerY, controllerZ;
    private double mapScale = 1.0D;
    private int waypointIndex, waystoneIndex;
    private long controllerActionCooldown;
    private double lastDragX, lastDragY;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
        centerX = controllerX = controllerPos.getX() + .5D;
        centerZ = controllerZ = controllerPos.getZ() + .5D;
        controllerY = controllerPos.getY() + .5D;
    }

    public BlockPos controllerPos() { return controllerPos; }
    private int panelWidth() { return Math.min(Math.max(980, width - 40), 1480); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return 18; }
    private int innerLeft() { return left() + 18; }
    private int contentTop() { return top() + 70; }
    private int panelBottom() { return height - 12; }

    @Override protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();
        updateControllerPosition();
        int l = innerLeft(), w = panelWidth() - 36, y = top(), gap = 10;
        int tabW = (w - gap * 3) / 4;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b -> switchTab(Tab.MAP)).bounds(l, y, tabW, 26).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> switchTab(Tab.ROUTE)).bounds(l + tabW + gap, y, tabW, 26).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b -> switchTab(Tab.FLIGHT_CONTROL)).bounds(l + (tabW + gap) * 2, y, tabW, 26).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b -> switchTab(Tab.DIAGNOSTICS)).bounds(l + (tabW + gap) * 3, y, tabW, 26).build());
        int utilityY = y + 31, utilityWidth = (w - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal("THERMAL"), b -> minecraft.setScreen(new ThermalConsoleScreen(controllerPos))).bounds(l, utilityY, utilityWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("COOLING"), b -> minecraft.setScreen(new CoolingConsoleScreen(controllerPos))).bounds(l + utilityWidth + gap, utilityY, utilityWidth, 22).build());
        if (tab == Tab.MAP) initMap(l, w); else if (tab == Tab.ROUTE) initRoute(l, w); else if (tab == Tab.FLIGHT_CONTROL) initFlightControl(l, w); else initDiagnostics(l, w);
    }

    private void initMap(int l, int w) {
        int y = height - 48, gap = 8, cols = 6, bw = (w - gap * (cols - 1)) / cols;
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer()).bounds(l, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CENTRE CTRL"), b -> centreController()).bounds(l + bw + gap, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("TERRAIN: " + on(showTerrain)), b -> { showTerrain = !showTerrain; b.setMessage(Component.literal("TERRAIN: " + on(showTerrain))); }).bounds(l + (bw + gap) * 2, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT MAP: " + on(showFlightMap)), b -> { showFlightMap = !showFlightMap; b.setMessage(Component.literal("FLIGHT MAP: " + on(showFlightMap))); }).bounds(l + (bw + gap) * 3, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("WAYPOINTS: " + on(showWaypoints)), b -> { showWaypoints = !showWaypoints; b.setMessage(Component.literal("WAYPOINTS: " + on(showWaypoints))); }).bounds(l + (bw + gap) * 4, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH MARKERS"), b -> refreshMarkers()).bounds(l + (bw + gap) * 5, y, bw, 22).build());
    }

    private void initRoute(int l, int w) {
        int y = contentTop() + 48, gap = 8, inputW = Math.min(520, w - 210);
        targetInput = new EditBox(font, l, y, inputW, 22, Component.literal("Target X Y Z")); targetInput.setHint(Component.literal("X Y Z  (example: 120 80 -240)")); addRenderableWidget(targetInput);
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION"), b -> sendTarget()).bounds(l + inputW + gap, y, 190, 22).build());
        y += 34;
        int col = (w - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.literal("CLEAR DESTINATION"), b -> FlightComputerNetwork.clearTarget(controllerPos)).bounds(l, y, col, 22).build());
        addRenderableWidget(Button.builder(Component.literal("START ROUTE"), b -> send(FlightControllerAction.START_ROUTE)).bounds(l + col + gap, y, col, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT ROUTE"), b -> send(FlightControllerAction.ABORT_ROUTE)).bounds(l + (col + gap) * 2, y, col, 22).build());
        y += 34;
        selectWaystoneButton = Button.builder(Component.literal("SELECT WAYSTONE"), this::selectWaystone).bounds(l, y, col, 22).build();
        selectWaypointButton = Button.builder(Component.literal("SELECT WAYPOINT"), this::selectWaypoint).bounds(l + col + gap, y, col, 22).build();
        addRenderableWidget(selectWaystoneButton); addRenderableWidget(selectWaypointButton);
        addRenderableWidget(Button.builder(Component.literal("REFRESH LOCATIONS"), b -> refreshLocations()).bounds(l + (col + gap) * 2, y, col, 22).build());
    }

    private void initFlightControl(int l, int w) {
        int gap = 10, col = (w - gap * 3) / 4, y = contentTop() + 48, half = (w - gap) / 2;
        engageButton = Button.builder(Component.literal("SYSTEM"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(l, y, col, 24).build();
        stabiliserButton = Button.builder(Component.literal("STABILISER"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(l + col + gap, y, col, 24).build();
        modeButton = Button.builder(Component.literal("MODE"), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(l + (col + gap) * 2, y, col, 24).build();
        autopilotButton = Button.builder(Component.literal("AUTOPILOT"), b -> send(FlightControllerAction.TOGGLE_AUTOPILOT)).bounds(l + (col + gap) * 3, y, col, 24).build();
        addRenderableWidget(engageButton); addRenderableWidget(stabiliserButton); addRenderableWidget(modeButton); addRenderableWidget(autopilotButton);

        int pushY = y + 58, pushW = Math.max(70, (half - gap * 5) / 6);
        String[] pushNames = {"F", "B", "U", "D", "L", "R"};
        FlightControllerAction[] pushActions = {FlightControllerAction.PUSH_FORWARD, FlightControllerAction.PUSH_BACKWARD, FlightControllerAction.PUSH_UP, FlightControllerAction.PUSH_DOWN, FlightControllerAction.PUSH_LEFT, FlightControllerAction.PUSH_RIGHT};
        for (int i = 0; i < 6; i++) { final int index = i; addRenderableWidget(Button.builder(Component.literal(pushNames[index]), b -> send(pushActions[index])).bounds(l + index * (pushW + 6), pushY, pushW, 26).build()); }

        int targetLeft = l + half + gap, targetWidth = w - half - gap;
        targetPlayerButton = Button.builder(Component.literal("PLAYER"), b -> { targetMode = TargetMode.PLAYER; refreshTargetLabels(); }).bounds(targetLeft, pushY, targetWidth / 2 - 4, 26).build();
        targetHomeButton = Button.builder(Component.literal("HOME"), b -> { targetMode = TargetMode.HOME; refreshTargetLabels(); }).bounds(targetLeft + targetWidth / 2 + 4, pushY, targetWidth / 2 - 4, 26).build();
        addRenderableWidget(targetPlayerButton); addRenderableWidget(targetHomeButton);
        targetPlayerInput = new EditBox(font, targetLeft, pushY + 32, targetWidth - 118, 22, Component.literal("Player name")); targetPlayerInput.setValue(minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : ""); targetPlayerInput.setMaxLength(32); addRenderableWidget(targetPlayerInput);
        addRenderableWidget(Button.builder(Component.literal("SET TARGET"), b -> sendSelectedTarget()).bounds(targetLeft + targetWidth - 110, pushY + 32, 110, 22).build());
        homeInput = new EditBox(font, targetLeft, pushY + 60, targetWidth - 110, 22, Component.literal("Home X Y Z")); homeInput.setMaxLength(64); loadHomeInput(); addRenderableWidget(homeInput);
        addRenderableWidget(Button.builder(Component.literal("SET HOME"), b -> sendHome()).bounds(targetLeft + targetWidth - 110, pushY + 60, 110, 22).build());

        int altitudeY = pushY + 96, altitudeW = Math.max(220, half - gap);
        altitudeTargetInput = new EditBox(font, l, altitudeY, altitudeW, 22, Component.literal("Altitude Y")); altitudeTargetInput.setValue(String.format(Locale.ROOT, "%.1f", controllerY)); altitudeTargetInput.setHint(Component.literal("World Y level")); altitudeTargetInput.setMaxLength(16); addRenderableWidget(altitudeTargetInput);
        setAltitudeButton = Button.builder(Component.literal("SET ALTITUDE TARGET"), b -> sendAltitudeTarget()).bounds(l + altitudeW + gap, altitudeY, half - gap, 22).build(); addRenderableWidget(setAltitudeButton);

        int controlY = altitudeY + 38;
        altitudeButton = holdButton(l, controlY, col, "ALTITUDE HOLD", FlightControllerAction.TOGGLE_ALTITUDE_HOLD);
        headingButton = holdButton(l + col + gap, controlY, col, "HEADING HOLD", FlightControllerAction.TOGGLE_HEADING_HOLD);
        positionButton = holdButton(l + (col + gap) * 2, controlY, col, "POSITION HOLD", FlightControllerAction.TOGGLE_POSITION_HOLD);
        velocityButton = holdButton(l + (col + gap) * 3, controlY, col, "VELOCITY HOLD", FlightControllerAction.TOGGLE_VELOCITY_HOLD);
        addRenderableWidget(altitudeButton); addRenderableWidget(headingButton); addRenderableWidget(positionButton); addRenderableWidget(velocityButton);
        int bottomY = controlY + 34;
        navigationButton = Button.builder(Component.literal("NAVIGATION"), b -> send(FlightControllerAction.TOGGLE_NAVIGATION)).bounds(l, bottomY, col, 22).build(); addRenderableWidget(navigationButton);
        addRenderableWidget(Button.builder(Component.literal("EMERGENCY SHUTDOWN"), b -> send(FlightControllerAction.EMERGENCY_SHUTDOWN)).bounds(l + col + gap, bottomY, col * 2 + gap, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(l + (col + gap) * 3, bottomY, col, 22).build());
        refreshControlLabels(); refreshTargetLabels();
    }

    private void initDiagnostics(int l, int w) {
        int gap = 10, half = (w - gap) / 2, y = contentTop() + 62;
        nameInput = new EditBox(font, l, y, half - 120, 22, Component.literal("Sub Level Name")); flightIdInput = new EditBox(font, l + half + gap, y, half - 120, 22, Component.literal("Flight ID"));
        nameInput.setMaxLength(64); flightIdInput.setMaxLength(32);
        if (controller instanceof FlightIdentityAccess identity) { nameInput.setValue(identity.flightcomputer$getSubLevelName()); flightIdInput.setValue(identity.flightcomputer$getFlightId()); }
        addRenderableWidget(nameInput); addRenderableWidget(flightIdInput);
        addRenderableWidget(Button.builder(Component.literal("SET NAME"), b -> setIdentityName()).bounds(l + half - 110, y, 102, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SET ID"), b -> setIdentityId()).bounds(l + half + gap + half - 110, y, 102, 22).build());
    }

    private void setIdentityName() { if (nameInput != null) FlightComputerNetwork.sendTarget(controllerPos, 0, 0, 0, "__SET_NAME__:" + nameInput.getValue().trim()); }
    private void setIdentityId() { if (flightIdInput != null) FlightComputerNetwork.sendTarget(controllerPos, 0, 0, 0, "__SET_ID__:" + flightIdInput.getValue().trim()); }
    private void loadHomeInput() { if (homeInput == null) return; Vec3 home = null; if (controller instanceof FlightIdentityAccess identity && minecraft != null && minecraft.player != null) home = identity.flightcomputer$getHome(minecraft.player.getUUID()); if (home == null && minecraft != null && minecraft.player != null) home = minecraft.player.position(); if (home != null) homeInput.setValue(String.format(Locale.ROOT, "%.1f %.1f %.1f", home.x, home.y, home.z)); }
    private void sendHome() { if (homeInput == null) return; String[] p = homeInput.getValue().trim().split("\\s+"); if (p.length != 3) return; try { double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]), z = Double.parseDouble(p[2]); if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) FlightComputerNetwork.sendTarget(controllerPos, x, y, z, "__SET_HOME__"); } catch (NumberFormatException ignored) { } }
    private void sendSelectedTarget() { if (targetMode == TargetMode.HOME) { FlightComputerNetwork.sendTarget(controllerPos, 0, 0, 0, "__HOME__"); return; } if (targetPlayerInput == null) return; String name = targetPlayerInput.getValue().trim(); if (!name.isEmpty()) FlightComputerNetwork.sendTarget(controllerPos, 0, 0, 0, "__PLAYER__:" + name); }
    private void refreshTargetLabels() { if (targetPlayerButton != null) targetPlayerButton.setMessage(Component.literal(targetMode == TargetMode.PLAYER ? "PLAYER [SELECTED]" : "PLAYER")); if (targetHomeButton != null) targetHomeButton.setMessage(Component.literal(targetMode == TargetMode.HOME ? "HOME [SELECTED]" : "HOME")); }
    private Button holdButton(int x, int y, int width, String name, FlightControllerAction action) { return Button.builder(Component.literal(name), b -> send(action)).bounds(x, y, width, 22).build(); }
    private static String on(boolean v) { return v ? "ON" : "OFF"; }
    private void refreshControlLabels() { if (controller == null) return; FlightControllerState s = controller.getControllerState(); if (engageButton != null) engageButton.setMessage(Component.literal("SYSTEM: " + (s.engaged() ? "ENGAGED" : "DISENGAGED"))); if (stabiliserButton != null) stabiliserButton.setMessage(Component.literal("STABILISER: " + on(s.stabiliser()))); if (modeButton != null) modeButton.setMessage(Component.literal("MODE: " + s.flightMode().name().replace('_', ' '))); if (autopilotButton != null) autopilotButton.setMessage(Component.literal("AUTOPILOT: " + (s.flightMode() == FlightMode.AUTOPILOT ? "ON" : "OFF"))); if (altitudeButton != null) altitudeButton.setMessage(Component.literal("ALTITUDE HOLD: " + on(s.altitudeHold()))); if (headingButton != null) headingButton.setMessage(Component.literal("HEADING HOLD: " + on(s.headingHold()))); if (positionButton != null) positionButton.setMessage(Component.literal("POSITION HOLD: " + on(s.positionHold()))); if (velocityButton != null) velocityButton.setMessage(Component.literal("VELOCITY HOLD: " + on(s.velocityHold()))); if (navigationButton != null) navigationButton.setMessage(Component.literal("NAVIGATION: " + on(s.navigationEnabled()))); }

    private void sendTarget() { if (targetInput == null) return; String[] p = targetInput.getValue().trim().split("\\s+"); if (p.length != 3) return; try { FlightComputerNetwork.sendTarget(controllerPos, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), "CUSTOM DESTINATION"); } catch (NumberFormatException ignored) { } }
    private void sendAltitudeTarget() { if (altitudeTargetInput == null) return; try { double y = Double.parseDouble(altitudeTargetInput.getValue().trim()); if (Double.isFinite(y)) { FlightComputerNetwork.sendAltitudeHoldTarget(controllerPos, y); altitudeTargetInput.setValue(String.format(Locale.ROOT, "%.1f", y)); } } catch (NumberFormatException ignored) { } }
    private void selectWaystone(Button b) { if (minecraft == null || minecraft.level == null) return; routeWaystones.requestRefresh(minecraft.level); List<FlightMapMarker> list = routeWaystones.markers(); if (list.isEmpty()) { pendingWaystone = true; b.setMessage(Component.literal("LOADING WAYSTONES...")); return; } pendingWaystone = false; chooseWaystone(list, b); }
    private void chooseWaystone(List<FlightMapMarker> list, Button b) { waystoneIndex = Math.floorMod(waystoneIndex, list.size()); FlightMapMarker m = list.get(waystoneIndex++); FlightComputerNetwork.sendTarget(controllerPos, m.worldX(), m.worldY(), m.worldZ(), m.label()); b.setMessage(Component.literal("WAYSTONE: " + m.label())); }
    private void selectWaypoint(Button b) { if (minecraft == null || minecraft.level == null) return; routeWaypoints.refreshNow(minecraft.level); List<FlightMapMarker> list = routeWaypoints.markers(); if (list.isEmpty()) { pendingWaypoint = true; b.setMessage(Component.literal("SCANNING WAYPOINTS...")); return; } pendingWaypoint = false; chooseWaypoint(list, b); }
    private void chooseWaypoint(List<FlightMapMarker> list, Button b) { waypointIndex = Math.floorMod(waypointIndex, list.size()); FlightMapMarker m = list.get(waypointIndex++); FlightComputerNetwork.sendTarget(controllerPos, m.worldX(), m.worldY(), m.worldZ(), m.label()); b.setMessage(Component.literal("WAYPOINT: " + m.label())); }
    private void refreshLocations() { waypointIndex = waystoneIndex = 0; pendingWaystone = pendingWaypoint = false; if (minecraft != null && minecraft.level != null) { routeWaystones.requestRefresh(minecraft.level); routeWaypoints.refreshNow(minecraft.level); } if (selectWaystoneButton != null) selectWaystoneButton.setMessage(Component.literal("SELECT WAYSTONE")); if (selectWaypointButton != null) selectWaypointButton.setMessage(Component.literal("SELECT WAYPOINT")); }
    private void refreshMarkers() { refreshLocations(); }
    private FlightControllerBlockEntity getController() { if (minecraft == null || minecraft.level == null) return null; BlockEntity be = minecraft.level.getBlockEntity(controllerPos); return be instanceof FlightControllerBlockEntity fc ? fc : null; }
    private boolean powered() { return controller != null && controller.getEnergyStorage().getEnergyStored() > 0 && controller.getPowerState() != PowerState.NO_POWER; }
    private String linkStatus() { return !powered() ? "OFFLINE" : controller.getLinkedControllerId() != null ? "CONNECTED" : "NOT LINKED"; }
    private void send(FlightControllerAction action) { if (minecraft == null || minecraft.level == null) return; long now = minecraft.level.getGameTime(); if (now < controllerActionCooldown) return; controllerActionCooldown = now + 1L; FlightComputerNetwork.sendControllerAction(controllerPos, action); }
    private void switchTab(Tab next) { if (tab == next) return; tab = next; clearWidgets(); init(); }
    private void updateControllerPosition() { if (minecraft == null || minecraft.level == null) return; Vec3 v = positionResolver.resolve(minecraft.level, controllerPos); if (v != null) { controllerX = v.x; controllerY = v.y; controllerZ = v.z; } }
    private void centrePlayer() { if (minecraft == null || minecraft.level == null || minecraft.player == null) return; Vec3 v = positionResolver.resolve(minecraft.level, minecraft.player.position()); if (v != null) { centerX = v.x; centerZ = v.z; } }
    private void centreController() { updateControllerPosition(); centerX = controllerX; centerZ = controllerZ; }

    @Override public void tick() { super.tick(); if (minecraft == null || minecraft.level == null) return; controller = getController(); updateControllerPosition(); if (tab == Tab.MAP) { mapPipeline.tick(minecraft.level, 4); waystones.tick(minecraft.level); waypoints.tick(minecraft.level); } else if (tab == Tab.ROUTE) { routeWaystones.tick(minecraft.level); routeWaypoints.tick(minecraft.level); if (pendingWaystone && !routeWaystones.markers().isEmpty()) { pendingWaystone = false; chooseWaystone(routeWaystones.markers(), selectWaystoneButton); } if (pendingWaypoint && !routeWaypoints.markers().isEmpty()) { pendingWaypoint = false; chooseWaypoint(routeWaypoints.markers(), selectWaypointButton); } } if (tab == Tab.FLIGHT_CONTROL) refreshControlLabels(); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) { int l = left(), r = l + panelWidth(), t = top(); g.fill(l - 8, t - 8, r + 8, panelBottom(), PANEL); g.drawString(font, "◈ NAVIGATION CONSOLE", l, t - 1, TEXT); String status = "LINK: " + linkStatus(); g.drawString(font, status, r - font.width(status), t - 1, powered() ? GREEN : RED); drawSectionLine(g, l, t + 64, r); if (tab == Tab.MAP) renderMap(g, l, contentTop()); else if (tab == Tab.ROUTE) renderRoute(g, l, contentTop()); else if (tab == Tab.FLIGHT_CONTROL) renderFlight(g, l, contentTop()); else renderDiagnostics(g, l, contentTop()); super.render(g, mouseX, mouseY, partialTick); int w = panelWidth() - 36, gap = 10, tabW = (w - gap * 3) / 4; g.fill(innerLeft() + tab.ordinal() * (tabW + gap), t + 24, innerLeft() + tab.ordinal() * (tabW + gap) + tabW, t + 26, BRIGHT); }
    private void drawSectionLine(GuiGraphics g, int l, int y, int r) { g.fill(l, y, r, y + 1, LINE); }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) { }

    private void renderMap(GuiGraphics g, int l, int top) { int ml = l, mt = top + 8, mr = l + panelWidth() - 36, mb = height - 70; g.drawString(font, "MAP / LOCAL TERRAIN", ml, top, TEXT); FlightMapDiagnostics d = mapPipeline.diagnostics(); boolean online = showTerrain && d.provider() == FlightMapProviderKind.NATIVE_JOURNEYMAP_INSPIRED; String terrain = "NATIVE TERRAIN: " + (online ? "ONLINE" : "OFFLINE"); g.drawString(font, terrain, ml + 210, top, online ? GREEN : RED); g.drawString(font, "WAYPOINTS: " + waypoints.markers().size(), ml + 390, top, CYAN); g.drawString(font, "WAYSTONES: " + waystones.markers().size(), ml + 520, top, WAYSTONE); g.fill(ml, mt, mr, mb, MAP_BG); g.enableScissor(ml, mt, mr, mb); if (showTerrain && minecraft != null && minecraft.level != null) renderTerrain(g, minecraft.level, ml, mt, mr, mb); if (showFlightMap) renderPositions(g, ml, mt, mr - ml, mb - mt); if (showWaypoints) { renderMarkers(g, waypoints.markers(), ml, mt, mr - ml, mb - mt, CYAN); renderMarkers(g, waystones.markers(), ml, mt, mr - ml, mb - mt, WAYSTONE); } g.disableScissor(); String centre = String.format(Locale.ROOT, "CENTRE X %.1f  Z %.1f", centerX, centerZ); g.drawString(font, centre, ml + 8, mb - 26, MUTED); g.drawString(font, "DRAG TO PAN  |  SCROLL TO ZOOM  |  LOADED CHUNKS PRE-WARMED", ml + 250, mb - 26, MUTED); }
    private void renderTerrain(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.multiplayer.ClientLevel level, int l, int t, int r, int b) { int tile = 16, step = 2; double pixelsPerBlock = mapScale; int minX = (int)Math.floor((centerX - (r - l) / 2D / pixelsPerBlock) / 16D) - 1, maxX = (int)Math.floor((centerX + (r - l) / 2D / pixelsPerBlock) / 16D) + 1; int minZ = (int)Math.floor((centerZ - (b - t) / 2D / pixelsPerBlock) / 16D) - 1, maxZ = (int)Math.floor((centerZ + (b - t) / 2D / pixelsPerBlock) / 16D) + 1; for (int cz = minZ; cz <= maxZ; cz++) for (int cx = minX; cx <= maxX; cx++) { int[] data = mapPipeline.getCachedTile(level, cx, cz); int px = (int)(l + (cx * 16 - centerX) * pixelsPerBlock + (r - l) / 2D), py = (int)(t + (cz * 16 - centerZ) * pixelsPerBlock + (b - t) / 2D); int drawTile = Math.max(1, (int)Math.round(tile * pixelsPerBlock)); if (data == null) { g.fill(px, py, px + drawTile, py + drawTile, 0xFF171B1E); continue; } for (int yy = 0; yy < tile; yy += step) { int start = 0, color = data[yy * tile]; for (int xx = step; xx <= tile; xx += step) { int c = xx < tile ? data[yy * tile + xx] : Integer.MIN_VALUE; if (c != color) { int x0 = px + (int)Math.round(start * pixelsPerBlock), x1 = px + (int)Math.round(xx * pixelsPerBlock); int y0 = py + (int)Math.round(yy * pixelsPerBlock), y1 = py + (int)Math.round((yy + step) * pixelsPerBlock); g.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), color); start = xx; color = c; } } } } }
    private void renderPositions(GuiGraphics g, int l, int t, int w, int h) { if (minecraft != null && minecraft.player != null) { Vec3 p = positionResolver.resolve(minecraft.level, minecraft.player.position()); if (p != null) diamond(g, screenX(p.x, l, w), screenZ(p.z, t, h), CYAN); } }
    private int screenX(double x, int l, int w) { return (int)(l + w / 2D + (x - centerX) * mapScale); }
    private int screenZ(double z, int t, int h) { return (int)(t + h / 2D + (z - centerZ) * mapScale); }
    private void diamond(GuiGraphics g, int x, int y, int color) { g.fill(x - 3, y, x + 4, y + 1, color); g.fill(x - 2, y - 1, x + 3, y + 2, color); g.fill(x - 1, y - 2, x + 2, y + 3, color); }
    private void renderMarkers(GuiGraphics g, List<FlightMapMarker> markers, int l, int t, int w, int h, int color) { for (FlightMapMarker marker : markers) { int x = screenX(marker.worldX(), l, w), y = screenZ(marker.worldZ(), t, h); if (x >= l && x < l + w && y >= t && y < t + h) { diamond(g, x, y, color); g.drawString(font, marker.label(), x + 6, y - 4, color); } } }

    private void renderRoute(GuiGraphics g, int l, int top) { g.drawString(font, "ROUTE / FLIGHT PLAN", l, top, TEXT); FlightComputerNetwork.TelemetryPayload telemetry = FlightComputerTelemetryClient.get(controller == null ? null : controller.getControllerId()); FlightControllerState state = controller == null ? null : controller.getControllerState(); int y = top + 180; if (telemetry != null && telemetry.targetPresent()) { g.drawString(font, "DESTINATION: " + (telemetry.targetName().isBlank() ? "NAVIGATION TARGET" : telemetry.targetName()), l, y, CYAN); g.drawString(font, String.format(Locale.ROOT, "CURRENT   X %.1f  Y %.1f  Z %.1f", telemetry.x(), telemetry.y(), telemetry.z()), l, y + 24, TEXT); g.drawString(font, String.format(Locale.ROOT, "TARGET    X %.1f  Y %.1f  Z %.1f", telemetry.targetX(), telemetry.targetY(), telemetry.targetZ()), l, y + 46, TEXT); double bearing = Math.toDegrees(Math.atan2(telemetry.targetX() - telemetry.x(), telemetry.targetZ() - telemetry.z())); if (bearing < 0) bearing += 360.0; g.drawString(font, String.format(Locale.ROOT, "ALT %.1f m   DIST %.1f m   BRG %.1f°   HDG %.1f°   SPEED %.2f m/s", telemetry.y(), telemetry.distance(), bearing, telemetry.heading(), telemetry.speed()), l, y + 70, TEXT); String mode = state == null ? "UNKNOWN" : state.flightMode().name(); String route = state != null && state.routeActive() ? "ACTIVE" : "IDLE"; g.drawString(font, "MODE: " + mode + "   ROUTE: " + route + "   NAVIGATION: " + (state != null && state.navigationEnabled() ? "ON" : "OFF"), l, y + 94, telemetry.targetPresent() && state != null && state.flightMode() == FlightMode.AUTOPILOT ? GREEN : MUTED); } else g.drawString(font, "NO ACTIVE NAVIGATION TARGET", l, y, MUTED); g.drawString(font, "WAYSTONES: " + routeWaystones.markers().size(), l, y + 128, WAYSTONE); g.drawString(font, "WAYPOINTS: " + routeWaypoints.markers().size(), l + 180, y + 128, CYAN); }
    private void renderFlight(GuiGraphics g, int l, int top) { g.drawString(font, "FLIGHT CONTROL", l, top, TEXT); g.drawString(font, "MANUAL / STABILISED / AUTOPILOT — SERVER AUTHORITATIVE", l, top + 22, MUTED); if (controller != null) { FlightControllerState s = controller.getControllerState(); g.drawString(font, "MODE: " + s.flightMode().name().replace('_', ' '), l, top + 42, CYAN); g.drawString(font, "NAVIGATION: " + on(s.navigationEnabled()), l + 220, top + 42, CYAN); g.drawString(font, "ALTITUDE HOLD: " + on(s.altitudeHold()), l + 410, top + 42, CYAN); g.drawString(font, "HEADING HOLD: " + on(s.headingHold()), l, top + 64, CYAN); g.drawString(font, "POSITION HOLD: " + on(s.positionHold()), l + 220, top + 64, CYAN); g.drawString(font, "VELOCITY HOLD: " + on(s.velocityHold()), l + 410, top + 64, CYAN); g.drawString(font, "PUSH: F/B FORWARD/BACK · U/D VERTICAL · L/R LATERAL", l, top + 88, MUTED); g.drawString(font, "TARGET MODE: " + targetMode.name(), l + 560, top + 88, CYAN); } }
    private void renderDiagnostics(GuiGraphics g, int l, int top) { FlightMapDiagnostics d = mapPipeline.diagnostics(); g.drawString(font, "DIAGNOSTICS / SYSTEM HEALTH", l, top, TEXT); if (controller instanceof FlightIdentityAccess identity) { g.drawString(font, "SUB LEVEL: " + identity.flightcomputer$getSubLevelName(), l, top + 118, CYAN); g.drawString(font, "FLIGHT ID: " + identity.flightcomputer$getFlightId(), l + 380, top + 118, CYAN); g.drawString(font, "NAMEPLATE IDENTITY: READY", l, top + 140, GREEN); } g.drawString(font, "MAP PROVIDER: " + d.provider(), l, top + 170, CYAN); g.drawString(font, "MAP STATE: " + d.state(), l + 380, top + 170, CYAN); g.drawString(font, "CACHE HITS: " + d.cacheHits(), l, top + 194, TEXT); g.drawString(font, "CACHE MISSES: " + d.cacheMisses(), l + 190, top + 194, TEXT); g.drawString(font, "REQUESTED: " + d.requestedTiles(), l + 380, top + 194, TEXT); g.drawString(font, "DECODED: " + d.decodedTiles(), l, top + 218, TEXT); g.drawString(font, "FAILED: " + d.failedTiles(), l + 190, top + 218, TEXT); g.drawString(font, "PENDING: " + d.pendingTiles(), l + 380, top + 218, TEXT); var setup = FlightSetupTelemetryClient.get(controller == null ? null : controller.getControllerId()); if (setup != null) { g.drawString(font, "SETUP: READY", l, top + 252, GREEN); g.drawString(font, "POWER " + setup.powerLevel() + "%", l, top + 276, TEXT); g.drawString(font, "CONTROL " + setup.controlLevel() + "%", l + 190, top + 276, TEXT); g.drawString(font, "PROPULSION " + setup.propulsionLevel() + "%", l + 380, top + 276, TEXT); g.drawString(font, "NAVIGATION " + setup.navigationLevel() + "%", l, top + 300, TEXT); } }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { if (tab == Tab.MAP && button == 0) { int ml = left() + 18, mt = contentTop() + 8, mr = left() + panelWidth() - 18, mb = height - 70; if (mouseX >= ml && mouseX < mr && mouseY >= mt && mouseY < mb) { dragging = true; lastDragX = mouseX; lastDragY = mouseY; } } return super.mouseClicked(mouseX, mouseY, button); }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { if (button == 0) dragging = false; return super.mouseReleased(mouseX, mouseY, button); }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { if (tab == Tab.MAP && dragging && button == 0) { centerX -= (mouseX - lastDragX) / mapScale; centerZ -= (mouseY - lastDragY) / mapScale; lastDragX = mouseX; lastDragY = mouseY; return true; } return super.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { if (tab == Tab.MAP) { int mt = contentTop() + 8, mb = height - 70; if (mouseY >= mt && mouseY < mb) { mapScale = Math.max(0.35D, Math.min(3.0D, mapScale * (scrollY > 0 ? 1.15D : 1.0D / 1.15D))); return true; } } return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY); }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
}
