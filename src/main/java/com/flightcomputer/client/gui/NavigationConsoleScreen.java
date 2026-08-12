package com.flightcomputer.client.gui;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.avionics.PowerState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.FlightComputerTelemetryClient;
import com.flightcomputer.client.map.FlightControllerWorldPositionResolver;
import com.flightcomputer.client.map.FlightMapDiagnostics;
import com.flightcomputer.client.map.FlightMapMarker;
import com.flightcomputer.client.map.FlightMapPipeline;
import com.flightcomputer.client.map.FlightMapProviderKind;
import com.flightcomputer.client.map.LiveWorldMapProvider;
import com.flightcomputer.client.map.WaypointMapProvider;
import com.flightcomputer.client.map.WaystoneMapProvider;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Navigation Console. Each primary page owns its own controls/content; no page-specific
 * widgets are installed by another page or by a render overlay.
 */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }
    private static final int PANEL = 0xE610141A, MAP_BG = 0xFF000000;
    private static final int CYAN = 0xFF55AAFF, BRIGHT = 0xFF66D9FF, GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555, TEXT = 0xFFE6EEF2, MUTED = 0xFF9DAEB5, WAYSTONE = 0xFFFFCC55;

    private final BlockPos controllerPos;
    private final LiveWorldMapProvider mapProvider = new LiveWorldMapProvider();
    private final FlightMapPipeline mapPipeline = new FlightMapPipeline(mapProvider);
    private final FlightControllerWorldPositionResolver positionResolver = new FlightControllerWorldPositionResolver();
    private final WaystoneMapProvider waystones = new WaystoneMapProvider();
    private final WaypointMapProvider waypoints = new WaypointMapProvider();
    private final WaystoneMapProvider routeWaystones = new WaystoneMapProvider();
    private final WaypointMapProvider routeWaypoints = new WaypointMapProvider();

    private Tab tab = Tab.MAP;
    private FlightControllerBlockEntity controller;
    private EditBox targetInput;
    private boolean showTerrain = true, showFlightMap = true, showWaypoints = true;
    private boolean dragging;
    private double centerX, centerZ, controllerX, controllerY, controllerZ;
    private int waypointIndex, waystoneIndex;

    public NavigationConsoleScreen(BlockPos controllerPos) {
        super(Component.literal("Navigation Console"));
        this.controllerPos = controllerPos;
        centerX = controllerX = controllerPos.getX() + .5D;
        centerZ = controllerZ = controllerPos.getZ() + .5D;
        controllerY = controllerPos.getY() + .5D;
    }

    public BlockPos controllerPos() { return controllerPos; }

    @Override protected void init() {
        controller = getController();
        if (controller != null) showTerrain = controller.isTerrainEnabled();
        updateControllerPosition();
        if (controller != null && tab == Tab.MAP) { centerX = controllerX; centerZ = controllerZ; }

        int left = Math.max(10, (width - 640) / 2), top = 20;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b -> switchTab(Tab.MAP)).bounds(left, top, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> switchTab(Tab.ROUTE)).bounds(left + 160, top, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b -> switchTab(Tab.FLIGHT_CONTROL)).bounds(left + 320, top, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b -> switchTab(Tab.DIAGNOSTICS)).bounds(left + 480, top, 150, 22).build());

        // These are navigation entry points only. Their actual UIs are separate screens.
        addRenderableWidget(Button.builder(Component.literal("THERMAL"), b -> minecraft.setScreen(new ThermalConsoleScreen(controllerPos)))
                .bounds(left + 480, top + 24, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("COOLING"), b -> minecraft.setScreen(new CoolingConsoleScreen(controllerPos)))
                .bounds(left + 555, top + 24, 75, 20).build());

        if (tab == Tab.MAP) initMap(left, top);
        else if (tab == Tab.ROUTE) initRoute(left, top);
        else if (tab == Tab.FLIGHT_CONTROL) initFlightControl(left, top);
    }

    private void initMap(int left, int top) {
        int y = top + 310, x = left + 20;
        addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"), b -> centrePlayer()).bounds(x, y, 118, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CENTRE CTRL"), b -> centreController()).bounds(x + 122, y, 104, 20).build());
        y += 24; x = left + 20;
        addRenderableWidget(Button.builder(Component.literal("TERRAIN: " + on(showTerrain)), b -> { showTerrain = !showTerrain; b.setMessage(Component.literal("TERRAIN: " + on(showTerrain))); }).bounds(x, y, 92, 20).build());
        x += 96;
        addRenderableWidget(Button.builder(Component.literal("FLIGHT MAP: " + on(showFlightMap)), b -> { showFlightMap = !showFlightMap; b.setMessage(Component.literal("FLIGHT MAP: " + on(showFlightMap))); }).bounds(x, y, 100, 20).build());
        x += 104;
        addRenderableWidget(Button.builder(Component.literal("WAYPOINTS: " + on(showWaypoints)), b -> { showWaypoints = !showWaypoints; b.setMessage(Component.literal("WAYPOINTS: " + on(showWaypoints))); }).bounds(x, y, 112, 20).build());
        x += 116;
        addRenderableWidget(Button.builder(Component.literal("REFRESH MARKERS"), b -> refreshMarkers()).bounds(x, y, 122, 20).build());
    }

    private void initRoute(int left, int top) {
        targetInput = new EditBox(font, left + 20, top + 150, 360, 20, Component.literal("Target X Y Z"));
        targetInput.setHint(Component.literal("X Y Z  (example: 120 80 -240)"));
        addRenderableWidget(targetInput);
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION"), b -> sendTarget()).bounds(left + 390, top + 150, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLEAR DESTINATION"), b -> FlightComputerNetwork.clearTarget(controllerPos)).bounds(left + 20, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("START ROUTE"), b -> send(FlightControllerAction.START_ROUTE)).bounds(left + 210, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT ROUTE"), b -> send(FlightControllerAction.ABORT_ROUTE)).bounds(left + 400, top + 180, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SELECT WAYSTONE"), this::selectWaystone).bounds(left + 20, top + 230, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SELECT WAYPOINT"), this::selectWaypoint).bounds(left + 210, top + 230, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH LOCATIONS"), b -> refreshLocations()).bounds(left + 400, top + 230, 180, 20).build());
    }

    private void initFlightControl(int left, int top) {
        FlightControllerState s = controller == null ? FlightControllerState.DEFAULT : controller.getControllerState();
        // Do not rebuild the screen immediately after sending a server action. The old client-side
        // BE state could still be present for one tick and would make a working button appear to
        // have reverted. The authoritative BE update will refresh the state naturally.
        addRenderableWidget(Button.builder(Component.literal(s.engaged() ? "DISENGAGE SYSTEM" : "ENGAGE SYSTEM"), b -> send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(left + 20, top + 180, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal(s.stabiliser() ? "STABILISER: ON" : "STABILISER: OFF"), b -> send(FlightControllerAction.TOGGLE_STABILISER)).bounds(left + 165, top + 180, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal(s.flightMode().name().replace('_',' ')), b -> send(FlightControllerAction.CYCLE_MODE)).bounds(left + 300, top + 180, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal(s.flightMode() == com.flightcomputer.avionics.FlightMode.AUTOPILOT ? "AUTOPILOT: ON" : "AUTOPILOT: OFF"), b -> send(FlightControllerAction.TOGGLE_AUTOPILOT)).bounds(left + 425, top + 180, 135, 20).build());
        addHoldButton(left + 20, top + 210, "ALTITUDE HOLD", FlightControllerAction.TOGGLE_ALTITUDE_HOLD, s.altitudeHold());
        addHoldButton(left + 165, top + 210, "HEADING HOLD", FlightControllerAction.TOGGLE_HEADING_HOLD, s.headingHold());
        addHoldButton(left + 310, top + 210, "POSITION HOLD", FlightControllerAction.TOGGLE_POSITION_HOLD, s.positionHold());
        addHoldButton(left + 455, top + 210, "VELOCITY HOLD", FlightControllerAction.TOGGLE_VELOCITY_HOLD, s.velocityHold());
        addRenderableWidget(Button.builder(Component.literal(s.navigationEnabled() ? "NAVIGATION: ON" : "NAVIGATION: OFF"), b -> send(FlightControllerAction.TOGGLE_NAVIGATION)).bounds(left + 20, top + 240, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("EMERGENCY SHUTDOWN"), b -> send(FlightControllerAction.EMERGENCY_SHUTDOWN)).bounds(left + 170, top + 240, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"), b -> send(FlightControllerAction.PULSE_DISPLAY)).bounds(left + 355, top + 240, 130, 20).build());
    }

    private void addHoldButton(int x, int y, String name, FlightControllerAction action, boolean active) {
        addRenderableWidget(Button.builder(Component.literal(name + ": " + on(active)), b -> send(action)).bounds(x, y, 140, 20).build());
    }

    private void refreshWidgets() { clearWidgets(); init(); }
    private static String on(boolean value) { return value ? "ON" : "OFF"; }

    private void sendTarget() {
        if (targetInput == null) return;
        String[] p = targetInput.getValue().trim().split("\\s+");
        if (p.length != 3) return;
        try { FlightComputerNetwork.sendTarget(controllerPos, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), "CUSTOM DESTINATION"); }
        catch (NumberFormatException ignored) { }
    }

    private void selectWaystone(Button b) {
        if (minecraft == null || minecraft.level == null) return;
        routeWaystones.tick(minecraft.level);
        var list = routeWaystones.markers();
        if (list.isEmpty()) { b.setMessage(Component.literal("NO WAYSTONES FOUND")); return; }
        waystoneIndex = Math.floorMod(waystoneIndex, list.size());
        FlightMapMarker m = list.get(waystoneIndex++);
        FlightComputerNetwork.sendTarget(controllerPos, m.worldX(), m.worldY(), m.worldZ(), m.label());
        b.setMessage(Component.literal("WAYSTONE: " + m.label()));
    }

    private void selectWaypoint(Button b) {
        if (minecraft == null || minecraft.level == null) return;
        routeWaypoints.tick(minecraft.level);
        var list = routeWaypoints.markers();
        if (list.isEmpty()) { b.setMessage(Component.literal("NO WAYPOINTS FOUND")); return; }
        waypointIndex = Math.floorMod(waypointIndex, list.size());
        FlightMapMarker m = list.get(waypointIndex++);
        FlightComputerNetwork.sendTarget(controllerPos, m.worldX(), m.worldY(), m.worldZ(), m.label());
        b.setMessage(Component.literal("WAYPOINT: " + m.label()));
    }

    private void refreshLocations() {
        waypointIndex = waystoneIndex = 0;
        if (minecraft != null && minecraft.level != null) { routeWaystones.tick(minecraft.level); routeWaypoints.tick(minecraft.level); }
    }
    private void refreshMarkers() { refreshLocations(); }

    private FlightControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) return null;
        BlockEntity be = minecraft.level.getBlockEntity(controllerPos);
        return be instanceof FlightControllerBlockEntity fc ? fc : null;
    }
    private boolean powered() { return controller != null && controller.getEnergyStorage().getEnergyStored() > 0 && controller.getPowerState() != PowerState.NO_POWER; }
    private String linkStatus() { return !powered() ? "OFFLINE" : controller.getLinkedControllerId() != null ? "CONNECTED" : "NOT LINKED"; }
    private void send(FlightControllerAction action) { FlightComputerNetwork.sendControllerAction(controllerPos, action); }
    private void switchTab(Tab next) { tab = next; clearWidgets(); init(); }

    private void updateControllerPosition() {
        if (minecraft == null || minecraft.level == null) return;
        Vec3 v = positionResolver.resolve(minecraft.level, controllerPos);
        if (v != null) { controllerX = v.x; controllerY = v.y; controllerZ = v.z; }
    }
    private void centrePlayer() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;
        Vec3 v = positionResolver.resolve(minecraft.level, minecraft.player.position());
        if (v != null) { centerX = v.x; centerZ = v.z; }
    }
    private void centreController() { updateControllerPosition(); centerX = controllerX; centerZ = controllerZ; }

    @Override public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) return;
        controller = getController();
        updateControllerPosition();
        if (tab == Tab.MAP) { mapPipeline.tick(minecraft.level, 4); waystones.tick(minecraft.level); waypoints.tick(minecraft.level); }
        if (tab == Tab.ROUTE) { routeWaystones.tick(minecraft.level); routeWaypoints.tick(minecraft.level); }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(10, (width - 640) / 2), top = 20;
        g.fill(left - 8, top - 8, left + 648, Math.min(height - 8, top + 355), PANEL);
        g.drawString(font, "◈ NAVIGATION CONSOLE", left, top - 2, TEXT);
        g.drawString(font, "LINK: " + linkStatus(), left + 500, top - 2, powered() ? GREEN : RED);
        if (tab == Tab.MAP) renderMap(g, left, top + 42);
        else if (tab == Tab.ROUTE) renderRoute(g, left, top + 42);
        else if (tab == Tab.FLIGHT_CONTROL) renderFlight(g, left, top + 42);
        else renderDiagnostics(g, left, top + 42);
        super.render(g, mouseX, mouseY, partialTick);
        int activeX = left + tab.ordinal() * 160;
        g.fill(activeX, top + 20, activeX + 150, top + 22, BRIGHT);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) { }

    private void renderMap(GuiGraphics g, int left, int top) {
        int l = left + 20, t = top + 8, r = left + 620, b = top + 268;
        g.fill(l, t, r, b, MAP_BG); g.enableScissor(l,t,r,b);
        if (showTerrain && minecraft != null && minecraft.level != null) renderTerrain(g, minecraft.level, l,t,r,b);
        if (showFlightMap) renderPositions(g,l,t,r-l,b-t);
        if (showWaypoints) { renderMarkers(g, waypoints.markers(), l,t,r-l,b-t, CYAN); renderMarkers(g, waystones.markers(), l,t,r-l,b-t, WAYSTONE); }
        g.disableScissor();
        FlightMapDiagnostics d = mapPipeline.diagnostics();
        boolean online = showTerrain && d.provider() == FlightMapProviderKind.NATIVE_JOURNEYMAP_INSPIRED;
        g.drawString(font, "NATIVE TERRAIN: " + (online ? "ONLINE" : "OFFLINE"), l+8,t+8, online?GREEN:RED);
        g.drawString(font, "WAYPOINTS: " + waypoints.markers().size(), l+8,t+20,CYAN);
        g.drawString(font, "WAYSTONES: " + waystones.markers().size(), l+125,t+20,WAYSTONE);
        g.drawString(font, String.format("CENTRE X %.1f  Z %.1f",centerX,centerZ), l+8,b-30,MUTED);
        g.drawString(font, "DRAG TO PAN | 1 BLOCK/PIXEL", l+8,b-14,MUTED);
    }

    private void renderTerrain(GuiGraphics g, net.minecraft.client.multiplayer.ClientLevel level, int l,int t,int r,int b) {
        int tile = 16, step = 2;
        int minX=(int)Math.floor((centerX-(r-l)/2D)/16D)-1, maxX=(int)Math.floor((centerX+(r-l)/2D)/16D)+1;
        int minZ=(int)Math.floor((centerZ-(r-l)/2D)/16D)-1, maxZ=(int)Math.floor((centerZ+(b-t)/2D)/16D)+1;
        for(int cz=minZ;cz<=maxZ;cz++) for(int cx=minX;cx<=maxX;cx++) {
            int[] data=mapPipeline.getCachedTile(level,cx,cz); int px=(int)(l+(cx*16-centerX)+(r-l)/2D), py=(int)(t+(cz*16-centerZ)+(b-t)/2D);
            if(data==null){g.fill(px,py,px+tile,py+tile,0xFF171B1E);continue;}
            for(int y=0;y<tile;y+=step){int start=0,color=data[y*tile];for(int x=step;x<=tile;x+=step){int c=x<tile?data[y*tile+x]:Integer.MIN_VALUE;if(c!=color){g.fill(px+start,py+y,px+x,py+y+step,color);start=x;color=c;}}}
        }
    }

    private void renderPositions(GuiGraphics g,int l,int t,int w,int h){
        if(minecraft!=null&&minecraft.player!=null){Vec3 p=positionResolver.resolve(minecraft.level,minecraft.player.position());if(p!=null) diamond(g,screenX(p.x,l,w),screenZ(p.z,t,h),4,RED);}
        diamond(g,screenX(controllerX,l,w),screenZ(controllerZ,t,h),4,BRIGHT);
    }
    private void renderMarkers(GuiGraphics g, java.util.List<FlightMapMarker> list,int l,int t,int w,int h,int color){
        for(FlightMapMarker m:list){int x=screenX(m.worldX(),l,w),z=screenZ(m.worldZ(),t,h);diamond(g,x,z,3,color);if(x>=l&&x<l+w&&z>=t&&z<t+h)g.drawString(font,m.label(),x+5,z-4,color);}
    }
    private int screenX(double x,int l,int w){return (int)Math.round(l+w/2D+x-centerX);}
    private int screenZ(double z,int t,int h){return (int)Math.round(t+h/2D+z-centerZ);}
    private static void diamond(GuiGraphics g,int x,int y,int r,int c){g.fill(x,y-r,x+1,y+r+1,c);for(int i=1;i<=r;i++){g.fill(x-i,y-i,x+i+1,y-i+1,c);g.fill(x-i,y+i-1,x+i+1,y+i,c);}}

    private void renderRoute(GuiGraphics g,int left,int top){
        g.drawString(font,"ROUTE / FLIGHT PLAN",left+20,top+10,TEXT);
        var s=controller==null?null:FlightComputerTelemetryClient.get(controller.getControllerId());
        if(s==null||!s.targetPresent()){g.drawString(font,"DESTINATION: NONE",left+20,top+48,MUTED);g.drawString(font,"Enter coordinates, or select a Waypoint / Waystone below.",left+20,top+76,MUTED);return;}

        double currentX = s.x(), currentY = s.y(), currentZ = s.z();
        double bearing = Math.toDegrees(Math.atan2(s.targetX() - currentX, s.targetZ() - currentZ));
        bearing = normalizeDegrees(bearing);
        g.drawString(font,"DESTINATION: "+s.targetName(),left+20,top+48,BRIGHT);
        g.drawString(font,String.format("CURRENT X %.1f  Y %.1f  Z %.1f",currentX,currentY,currentZ),left+20,top+72,TEXT);
        g.drawString(font,String.format("TARGET  X %.1f  Y %.1f  Z %.1f",s.targetX(),s.targetY(),s.targetZ()),left+20,top+94,TEXT);
        g.drawString(font,String.format("ALT %.1f m  DIST %.1f m  BRG %.1f°  HDG %.1f°  SPEED %.2f m/s",currentY,s.distance(),bearing,normalizeDegrees(s.heading()),s.speed()),left+20,top+116,TEXT);
        double eta=s.speed()>.1?s.distance()/s.speed():-1; g.drawString(font,eta<0?"ETA: CALCULATING":String.format("ETA %.1f s  ROUTE: MPC / SMOOTH ACCELERATION",eta),left+20,top+140,eta<0?MUTED:GREEN);
    }

    private void renderFlight(GuiGraphics g,int left,int top){
        FlightControllerState s=controller==null?FlightControllerState.DEFAULT:controller.getControllerState();
        var t=controller==null?null:FlightComputerTelemetryClient.get(controller.getControllerId());
        g.drawString(font,"FLIGHT CONTROL",left+20,top+10,TEXT);
        g.drawString(font,"SYSTEM: "+(s.engaged()?"ENGAGED":"DISENGAGED"),left+20,top+42,s.engaged()?GREEN:MUTED);
        g.drawString(font,"STABILISER: "+on(s.stabiliser()),left+20,top+65,s.stabiliser()?GREEN:MUTED);
        g.drawString(font,"AUTOPILOT: "+(s.flightMode()==com.flightcomputer.avionics.FlightMode.AUTOPILOT?"ON":"OFF"),left+200,top+65,s.flightMode()==com.flightcomputer.avionics.FlightMode.AUTOPILOT?GREEN:MUTED);
        g.drawString(font,"MODE: "+s.flightMode(),left+20,top+88,BRIGHT);
        g.drawString(font,"NAVIGATION: "+on(s.navigationEnabled())+"   ROUTE: "+on(s.routeActive()),left+200,top+88,TEXT);
        g.drawString(font,"HOLDS  ALT "+on(s.altitudeHold())+"  HDG "+on(s.headingHold())+"  POS "+on(s.positionHold())+"  VEL "+on(s.velocityHold()),left+20,top+112,MUTED);
        if(t!=null) g.drawString(font,String.format("ALT %.1f  SPEED %.1f  HEADING %.1f°  TARGET %s",t.y(),t.speed(),normalizeDegrees(t.heading()),t.targetPresent()?t.targetName():"NONE"),left+20,top+136,TEXT);
    }

    private void renderDiagnostics(GuiGraphics g,int left,int top){
        long e=controller==null?0:controller.getEnergyStorage().getEnergyStored(), cap=controller==null?0:controller.getEnergyStorage().getMaxEnergyStored();
        g.drawString(font,"DIAGNOSTICS",left+20,top+10,TEXT); g.drawString(font,"CONTROLLER: "+(powered()?"OPERATIONAL":"OFFLINE"),left+20,top+42,powered()?GREEN:RED);
        g.drawString(font,"LINK: "+linkStatus(),left+20,top+65,powered()?GREEN:RED); g.drawString(font,"ENERGY: "+format(e)+" / "+format(cap)+" FE",left+20,top+88,e>0?GREEN:RED);
        FlightMapDiagnostics d=mapPipeline.diagnostics(); g.drawString(font,"MAP ENGINE: NATIVE CPU TERRAIN",left+20,top+120,BRIGHT);
        g.drawString(font,"REQUESTED "+d.requestedCount()+"  PENDING "+d.pendingCount()+"  DECODED "+d.decodedCount()+"  FAILED "+d.failedCount(),left+20,top+144,MUTED);
        g.drawString(font,String.format("WORLD X %.2f  Y %.2f  Z %.2f",controllerX,controllerY,controllerZ),left+20,top+168,TEXT);
    }
    private static String format(long v){return String.format("%,d",Math.max(0,v));}
    private static double normalizeDegrees(double degrees){double value=degrees%360.0D;return value<0?value+360.0D:value;}

    @Override public boolean mouseClicked(double x,double y,int button){if(tab==Tab.MAP&&button==0&&x>=mapLeft()&&x<mapRight()&&y>=mapTop()&&y<mapBottom()){dragging=true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(tab==Tab.MAP&&dragging&&button==0){centerX-=dx;centerZ-=dy;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    private int mapLeft(){return Math.max(10,(width-640)/2)+20;} private int mapRight(){return mapLeft()+600;} private int mapTop(){return 70;} private int mapBottom(){return 330;}
    @Override public boolean isPauseScreen(){return false;}
}
