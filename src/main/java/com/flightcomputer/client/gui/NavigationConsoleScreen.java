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

/** Responsive Navigation Console. Route data and controls occupy separate rows and never overlap. */
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
    private EditBox targetInput, altitudeTargetInput;
    private Button engageButton, stabiliserButton, modeButton, autopilotButton, altitudeButton, headingButton, positionButton, velocityButton, navigationButton, setAltitudeButton;
    private Button selectWaystoneButton, selectWaypointButton;
    private boolean showTerrain = true, showFlightMap = true, showWaypoints = true, dragging, pendingWaystone, pendingWaypoint;
    private long controllerActionCooldown;
    private double centerX, centerZ, controllerX, controllerY, controllerZ;
    private int waypointIndex, waystoneIndex;

    public NavigationConsoleScreen(BlockPos controllerPos) { super(Component.literal("Navigation Console")); this.controllerPos = controllerPos; centerX = controllerX = controllerPos.getX()+.5D; centerZ = controllerZ = controllerPos.getZ()+.5D; controllerY = controllerPos.getY()+.5D; }
    public BlockPos controllerPos() { return controllerPos; }
    private int panelWidth() { return Math.min(Math.max(760, width - 32), 1240); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return 18; }
    private int innerLeft() { return left()+18; }

    @Override protected void init() {
        controller = getController(); if (controller != null) showTerrain = controller.isTerrainEnabled(); updateControllerPosition();
        int l=innerLeft(), w=panelWidth()-36, y=top(), gap=8, tabW=(w-gap*3)/4;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b->switchTab(Tab.MAP)).bounds(l,y,tabW,22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b->switchTab(Tab.ROUTE)).bounds(l+tabW+gap,y,tabW,22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b->switchTab(Tab.FLIGHT_CONTROL)).bounds(l+(tabW+gap)*2,y,tabW,22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b->switchTab(Tab.DIAGNOSTICS)).bounds(l+(tabW+gap)*3,y,tabW,22).build());
        if(tab==Tab.MAP) initMap(l,w); else if(tab==Tab.ROUTE) initRoute(l,w); else if(tab==Tab.FLIGHT_CONTROL) initFlightControl(l,w);
    }

    private void initMap(int l,int w){int y=top()+350; addRenderableWidget(Button.builder(Component.literal("CENTRE PLAYER"),b->centrePlayer()).bounds(l,y,125,20).build()); addRenderableWidget(Button.builder(Component.literal("CENTRE CTRL"),b->centreController()).bounds(l+133,y,110,20).build()); addRenderableWidget(Button.builder(Component.literal("TERRAIN: "+on(showTerrain)),b->{showTerrain=!showTerrain;b.setMessage(Component.literal("TERRAIN: "+on(showTerrain)));}).bounds(l+251,y,105,20).build()); addRenderableWidget(Button.builder(Component.literal("FLIGHT MAP: "+on(showFlightMap)),b->{showFlightMap=!showFlightMap;b.setMessage(Component.literal("FLIGHT MAP: "+on(showFlightMap)));}).bounds(l+364,y,115,20).build()); addRenderableWidget(Button.builder(Component.literal("WAYPOINTS: "+on(showWaypoints)),b->{showWaypoints=!showWaypoints;b.setMessage(Component.literal("WAYPOINTS: "+on(showWaypoints)));}).bounds(l+487,y,125,20).build()); addRenderableWidget(Button.builder(Component.literal("REFRESH MARKERS"),b->refreshMarkers()).bounds(l+620,y,135,20).build());}

    private void initRoute(int l,int w){
        targetInput=new EditBox(font,l,top()+266,Math.min(360,w-260),20,Component.literal("Target X Y Z")); targetInput.setHint(Component.literal("X Y Z  (example: 120 80 -240)")); addRenderableWidget(targetInput);
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION"),b->sendTarget()).bounds(l+targetInput.getWidth()+8,top()+266,190,20).build());
        int row=top()+296,col=(w-16)/3;
        addRenderableWidget(Button.builder(Component.literal("CLEAR DESTINATION"),b->FlightComputerNetwork.clearTarget(controllerPos)).bounds(l,row,col,20).build());
        addRenderableWidget(Button.builder(Component.literal("START ROUTE"),b->send(FlightControllerAction.START_ROUTE)).bounds(l+col+8,row,col,20).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT ROUTE"),b->send(FlightControllerAction.ABORT_ROUTE)).bounds(l+(col+8)*2,row,col,20).build());
        row+=28; selectWaystoneButton=Button.builder(Component.literal("SELECT WAYSTONE"),this::selectWaystone).bounds(l,row,col,20).build(); selectWaypointButton=Button.builder(Component.literal("SELECT WAYPOINT"),this::selectWaypoint).bounds(l+col+8,row,col,20).build(); addRenderableWidget(selectWaystoneButton); addRenderableWidget(selectWaypointButton); addRenderableWidget(Button.builder(Component.literal("REFRESH LOCATIONS"),b->refreshLocations()).bounds(l+(col+8)*2,row,col,20).build());
    }

    private void initFlightControl(int l,int w){
        int col=(w-24)/4;
        int altitudeRow=top()+206;
        int inputWidth=Math.max(180,col*2-8);
        altitudeTargetInput=new EditBox(font,l,altitudeRow,inputWidth,20,Component.literal("Altitude Y"));
        altitudeTargetInput.setValue(String.format(Locale.ROOT,"%.1f",controllerY));
        altitudeTargetInput.setHint(Component.literal("World Y level"));
        altitudeTargetInput.setMaxLength(16);
        addRenderableWidget(altitudeTargetInput);
        setAltitudeButton=Button.builder(Component.literal("SET ALTITUDE TARGET"),b->sendAltitudeTarget()).bounds(l+inputWidth+8,altitudeRow,Math.max(170,col*2-8),20).build();
        addRenderableWidget(setAltitudeButton);

        int y=top()+250;
        engageButton=Button.builder(Component.literal("SYSTEM"),b->send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(l,y,col,20).build(); stabiliserButton=Button.builder(Component.literal("STABILISER"),b->send(FlightControllerAction.TOGGLE_STABILISER)).bounds(l+col+8,y,col,20).build(); modeButton=Button.builder(Component.literal("MODE"),b->send(FlightControllerAction.CYCLE_MODE)).bounds(l+(col+8)*2,y,col,20).build(); autopilotButton=Button.builder(Component.literal("AUTOPILOT"),b->send(FlightControllerAction.TOGGLE_AUTOPILOT)).bounds(l+(col+8)*3,y,col,20).build(); addRenderableWidget(engageButton);addRenderableWidget(stabiliserButton);addRenderableWidget(modeButton);addRenderableWidget(autopilotButton);
        y+=28; altitudeButton=holdButton(l,y,col,"ALTITUDE HOLD",FlightControllerAction.TOGGLE_ALTITUDE_HOLD); headingButton=holdButton(l+col+8,y,col,"HEADING HOLD",FlightControllerAction.TOGGLE_HEADING_HOLD); positionButton=holdButton(l+(col+8)*2,y,col,"POSITION HOLD",FlightControllerAction.TOGGLE_POSITION_HOLD); velocityButton=holdButton(l+(col+8)*3,y,col,"VELOCITY HOLD",FlightControllerAction.TOGGLE_VELOCITY_HOLD); addRenderableWidget(altitudeButton);addRenderableWidget(headingButton);addRenderableWidget(positionButton);addRenderableWidget(velocityButton);
        y+=28; navigationButton=Button.builder(Component.literal("NAVIGATION"),b->send(FlightControllerAction.TOGGLE_NAVIGATION)).bounds(l,y,col,20).build(); addRenderableWidget(navigationButton); addRenderableWidget(Button.builder(Component.literal("EMERGENCY SHUTDOWN"),b->send(FlightControllerAction.EMERGENCY_SHUTDOWN)).bounds(l+col+8,y,col*2+8,20).build()); addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"),b->send(FlightControllerAction.PULSE_DISPLAY)).bounds(l+(col+8)*3,y,col,20).build()); refreshControlLabels();
    }
    private Button holdButton(int x,int y,int width,String name,FlightControllerAction action){return Button.builder(Component.literal(name),b->send(action)).bounds(x,y,width,20).build();}
    private static String on(boolean v){return v?"ON":"OFF";}
    private void refreshControlLabels(){if(controller==null)return; FlightControllerState s=controller.getControllerState(); if(engageButton!=null)engageButton.setMessage(Component.literal("SYSTEM: "+(s.engaged()?"ENGAGED":"DISENGAGED")));if(stabiliserButton!=null)stabiliserButton.setMessage(Component.literal("STABILISER: "+on(s.stabiliser())));if(modeButton!=null)modeButton.setMessage(Component.literal("MODE: "+s.flightMode().name().replace('_',' ')));if(autopilotButton!=null)autopilotButton.setMessage(Component.literal("AUTOPILOT: "+(s.flightMode()==FlightMode.AUTOPILOT?"ON":"OFF")));if(altitudeButton!=null)altitudeButton.setMessage(Component.literal("ALTITUDE HOLD: "+on(s.altitudeHold())));if(headingButton!=null)headingButton.setMessage(Component.literal("HEADING HOLD: "+on(s.headingHold())));if(positionButton!=null)positionButton.setMessage(Component.literal("POSITION HOLD: "+on(s.positionHold())));if(velocityButton!=null)velocityButton.setMessage(Component.literal("VELOCITY HOLD: "+on(s.velocityHold())));if(navigationButton!=null)navigationButton.setMessage(Component.literal("NAVIGATION: "+on(s.navigationEnabled())));}

    private void sendTarget(){if(targetInput==null)return;String[] p=targetInput.getValue().trim().split("\\s+");if(p.length!=3)return;try{FlightComputerNetwork.sendTarget(controllerPos,Double.parseDouble(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]),"CUSTOM DESTINATION");}catch(NumberFormatException ignored){}}
    private void sendAltitudeTarget(){if(altitudeTargetInput==null)return;try{double y=Double.parseDouble(altitudeTargetInput.getValue().trim());if(Double.isFinite(y)){FlightComputerNetwork.sendAltitudeHoldTarget(controllerPos,y);altitudeTargetInput.setValue(String.format(Locale.ROOT,"%.1f",y));}}catch(NumberFormatException ignored){}}
    private void selectWaystone(Button b){if(minecraft==null||minecraft.level==null)return;routeWaystones.requestRefresh(minecraft.level);List<FlightMapMarker> list=routeWaystones.markers();if(list.isEmpty()){pendingWaystone=true;b.setMessage(Component.literal("LOADING WAYSTONES..."));return;}pendingWaystone=false;chooseWaystone(list,b);}
    private void chooseWaystone(List<FlightMapMarker> list,Button b){waystoneIndex=Math.floorMod(waystoneIndex,list.size());FlightMapMarker m=list.get(waystoneIndex++);FlightComputerNetwork.sendTarget(controllerPos,m.worldX(),m.worldY(),m.worldZ(),m.label());b.setMessage(Component.literal("WAYSTONE: "+m.label()));}
    private void selectWaypoint(Button b){if(minecraft==null||minecraft.level==null)return;routeWaypoints.refreshNow(minecraft.level);List<FlightMapMarker> list=routeWaypoints.markers();if(list.isEmpty()){pendingWaypoint=true;b.setMessage(Component.literal("SCANNING WAYPOINTS..."));return;}pendingWaypoint=false;chooseWaypoint(list,b);}
    private void chooseWaypoint(List<FlightMapMarker> list,Button b){waypointIndex=Math.floorMod(waypointIndex,list.size());FlightMapMarker m=list.get(waypointIndex++);FlightComputerNetwork.sendTarget(controllerPos,m.worldX(),m.worldY(),m.worldZ(),m.label());b.setMessage(Component.literal("WAYPOINT: "+m.label()));}
    private void refreshLocations(){waypointIndex=waystoneIndex=0;pendingWaystone=pendingWaypoint=false;if(minecraft!=null&&minecraft.level!=null){routeWaystones.requestRefresh(minecraft.level);routeWaypoints.refreshNow(minecraft.level);}if(selectWaystoneButton!=null)selectWaystoneButton.setMessage(Component.literal("SELECT WAYSTONE"));if(selectWaypointButton!=null)selectWaypointButton.setMessage(Component.literal("SELECT WAYPOINT"));}
    private void refreshMarkers(){refreshLocations();}
    private FlightControllerBlockEntity getController(){if(minecraft==null||minecraft.level==null)return null;BlockEntity be=minecraft.level.getBlockEntity(controllerPos);return be instanceof FlightControllerBlockEntity fc?fc:null;}
    private boolean powered(){return controller!=null&&controller.getEnergyStorage().getEnergyStored()>0&&controller.getPowerState()!=PowerState.NO_POWER;}
    private String linkStatus(){return !powered()?"OFFLINE":controller.getLinkedControllerId()!=null?"CONNECTED":"NOT LINKED";}
    private void send(FlightControllerAction action){if(minecraft==null||minecraft.level==null)return;long now=minecraft.level.getGameTime();if(now<controllerActionCooldown)return;controllerActionCooldown=now+1L;FlightComputerNetwork.sendControllerAction(controllerPos,action);}
    private void switchTab(Tab next){if(tab==next)return;tab=next;clearWidgets();init();}
    private void updateControllerPosition(){if(minecraft==null||minecraft.level==null)return;Vec3 v=positionResolver.resolve(minecraft.level,controllerPos);if(v!=null){controllerX=v.x;controllerY=v.y;controllerZ=v.z;}}
    private void centrePlayer(){if(minecraft==null||minecraft.level==null||minecraft.player==null)return;Vec3 v=positionResolver.resolve(minecraft.level,minecraft.player.position());if(v!=null){centerX=v.x;centerZ=v.z;}}
    private void centreController(){updateControllerPosition();centerX=controllerX;centerZ=controllerZ;}

    @Override public void tick(){super.tick();if(minecraft==null||minecraft.level==null)return;controller=getController();updateControllerPosition();if(tab==Tab.MAP){mapPipeline.tick(minecraft.level,4);waystones.tick(minecraft.level);waypoints.tick(minecraft.level);}else if(tab==Tab.ROUTE){routeWaystones.tick(minecraft.level);routeWaypoints.tick(minecraft.level);if(pendingWaystone&&!routeWaystones.markers().isEmpty()){pendingWaystone=false;chooseWaystone(routeWaystones.markers(),selectWaystoneButton);}if(pendingWaypoint&&!routeWaypoints.markers().isEmpty()){pendingWaypoint=false;chooseWaypoint(routeWaypoints.markers(),selectWaypointButton);}}if(tab==Tab.FLIGHT_CONTROL)refreshControlLabels();}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){int l=left(),r=l+panelWidth(),t=top();g.fill(l-8,t-8,r+8,Math.min(height-8,t+390),PANEL);g.drawString(font,"◈ NAVIGATION CONSOLE",l,t-1,TEXT);g.drawString(font,"LINK: "+linkStatus(),r-120,t-1,powered()?GREEN:RED);if(tab==Tab.MAP)renderMap(g,l,t+35);else if(tab==Tab.ROUTE)renderRoute(g,l,t+35);else if(tab==Tab.FLIGHT_CONTROL)renderFlight(g,l,t+35);else renderDiagnostics(g,l,t+35);super.render(g,mouseX,mouseY,partialTick);int tabW=(panelWidth()-52)/4;g.fill(innerLeft()+tab.ordinal()*(tabW+8),t+20,innerLeft()+tab.ordinal()*(tabW+8)+tabW,t+22,BRIGHT);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}

    private void renderMap(GuiGraphics g,int l,int top){int ml=l,mt=top+8,mr=l+panelWidth()-36,mb=top+330;g.fill(ml,mt,mr,mb,MAP_BG);g.enableScissor(ml,mt,mr,mb);if(showTerrain&&minecraft!=null&&minecraft.level!=null)renderTerrain(g,minecraft.level,ml,mt,mr,mb);if(showFlightMap)renderPositions(g,ml,mt,mr-ml,mb-mt);if(showWaypoints){renderMarkers(g,waypoints.markers(),ml,mt,mr-ml,mb-mt,CYAN);renderMarkers(g,waystones.markers(),ml,mt,mr-ml,mb-mt,WAYSTONE);}g.disableScissor();FlightMapDiagnostics d=mapPipeline.diagnostics();boolean online=showTerrain&&d.provider()==FlightMapProviderKind.NATIVE_JOURNEYMAP_INSPIRED;g.drawString(font,"NATIVE TERRAIN: "+(online?"ONLINE":"OFFLINE"),ml+8,mt+8,online?GREEN:RED);g.drawString(font,"WAYPOINTS: "+waypoints.markers().size(),ml+8,mt+22,CYAN);g.drawString(font,"WAYSTONES: "+waystones.markers().size(),ml+140,mt+22,WAYSTONE);g.drawString(font,String.format(Locale.ROOT,"CENTRE X %.1f  Z %.1f",centerX,centerZ),ml+8,mb-28,MUTED);g.drawString(font,"DRAG TO PAN | 1 BLOCK/PIXEL",ml+8,mb-14,MUTED);}
    private void renderTerrain(GuiGraphics g,net.minecraft.client.multiplayer.ClientLevel level,int l,int t,int r,int b){int tile=16,step=2;int minX=(int)Math.floor((centerX-(r-l)/2D)/16D)-1,maxX=(int)Math.floor((centerX+(r-l)/2D)/16D)+1;int minZ=(int)Math.floor((centerZ-(b-t)/2D)/16D)-1,maxZ=(int)Math.floor((centerZ+(b-t)/2D)/16D)+1;for(int cz=minZ;cz<=maxZ;cz++)for(int cx=minX;cx<=maxX;cx++){int[] data=mapPipeline.getCachedTile(level,cx,cz);int px=(int)(l+(cx*16-centerX)+(r-l)/2D),py=(int)(t+(cz*16-centerZ)+(b-t)/2D);if(data==null){g.fill(px,py,px+tile,py+tile,0xFF171B1E);continue;}for(int yy=0;yy<tile;yy+=step){int start=0,color=data[yy*tile];for(int xx=step;xx<=tile;xx+=step){int c=xx<tile?data[yy*tile+xx]:Integer.MIN_VALUE;if(c!=color){g.fill(px+start,py+yy,px+xx,py+yy+step,color);start=xx;color=c;}}}}}
    private void renderPositions(GuiGraphics g,int l,int t,int w,int h){if(minecraft!=null&&minecraft.player!=null){Vec3 p=positionResolver.resolve(minecraft.level,minecraft.player.position());if(p!=null)diamond(g,screenX(p.x,l,w),screenZ(p.z,t,h),4,RED);}diamond(g,screenX(controllerX,l,w),screenZ(controllerZ,t,h),4,BRIGHT);}
    private void renderMarkers(GuiGraphics g,List<FlightMapMarker> list,int l,int t,int w,int h,int color){for(FlightMapMarker m:list){int x=screenX(m.worldX(),l,w),z=screenZ(m.worldZ(),t,h);diamond(g,x,z,3,color);if(x>=l&&x<l+w&&z>=t&&z<t+h)g.drawString(font,m.label(),x+5,z-4,color);}}
    private int screenX(double x,int l,int w){return(int)Math.round(l+w/2D+x-centerX);} private int screenZ(double z,int t,int h){return(int)Math.round(t+h/2D+z-centerZ);} private static void diamond(GuiGraphics g,int x,int y,int r,int c){g.fill(x,y-r,x+1,y+r+1,c);for(int i=1;i<=r;i++){g.fill(x-i,y-i,x+i+1,y-i+1,c);g.fill(x-i,y+i-1,x+i+1,y+i,c);}}

    private void renderRoute(GuiGraphics g,int l,int top){g.drawString(font,"ROUTE / FLIGHT PLAN",l,top+10,TEXT);var telemetry=controller==null?null:FlightComputerTelemetryClient.get(controller.getControllerId());if(telemetry==null||!telemetry.targetPresent()){g.drawString(font,"DESTINATION: NONE",l,top+46,MUTED);g.drawString(font,"Enter coordinates, or select a Waypoint / Waystone below.",l,top+70,MUTED);g.drawString(font,"LOCATION SOURCES: XAERO WAYPOINTS + WAYSTONES",l,top+94,MUTED);return;}double cx=telemetry.x(),cy=telemetry.y(),cz=telemetry.z();double bearing=normalizeDegrees(Math.toDegrees(Math.atan2(telemetry.targetX()-cx,telemetry.targetZ()-cz)));g.drawString(font,"DESTINATION: "+telemetry.targetName(),l,top+46,BRIGHT);g.drawString(font,String.format(Locale.ROOT,"CURRENT X %.1f  Y %.1f  Z %.1f",cx,cy,cz),l,top+72,TEXT);g.drawString(font,String.format(Locale.ROOT,"TARGET  X %.1f  Y %.1f  Z %.1f",telemetry.targetX(),telemetry.targetY(),telemetry.targetZ()),l,top+98,TEXT);g.drawString(font,String.format(Locale.ROOT,"ALT %.1f m   DIST %.1f m   BRG %.1f°   HDG %.1f°   SPEED %.2f m/s",cy,telemetry.distance(),bearing,normalizeDegrees(telemetry.heading()),telemetry.speed()),l,top+124,TEXT);double eta=telemetry.speed()>.1?telemetry.distance()/telemetry.speed():-1;g.drawString(font,eta<0?"ETA: CALCULATING":String.format(Locale.ROOT,"ETA %.1f s   ROUTE: MPC / SMOOTH ACCELERATION",eta),l,top+150,eta<0?MUTED:GREEN);g.drawString(font,"TARGET SOURCE: "+sourceLabel(telemetry.targetName()),l,top+176,MUTED);}
    private String sourceLabel(String name){if(name==null||name.isBlank())return"CUSTOM";String v=name.toLowerCase(Locale.ROOT);if(v.contains("waystone"))return"WAYSTONE";if(v.contains("waypoint"))return"XAERO WAYPOINT";return v.equals("custom destination")?"CUSTOM":"NAVIGATION DESTINATION";}
    private void renderFlight(GuiGraphics g,int l,int top){FlightControllerState s=controller==null?FlightControllerState.DEFAULT:controller.getControllerState();var telemetry=controller==null?null:FlightComputerTelemetryClient.get(controller.getControllerId());g.drawString(font,"FLIGHT CONTROL",l,top+10,TEXT);g.drawString(font,"SYSTEM: "+(s.engaged()?"ENGAGED":"DISENGAGED"),l,top+44,s.engaged()?GREEN:MUTED);g.drawString(font,"STABILISER: "+on(s.stabiliser()),l,top+68,s.stabiliser()?GREEN:MUTED);g.drawString(font,"AUTOPILOT: "+(s.flightMode()==FlightMode.AUTOPILOT?"ON":"OFF"),l+190,top+68,s.flightMode()==FlightMode.AUTOPILOT?GREEN:MUTED);g.drawString(font,"MODE: "+s.flightMode(),l,top+92,BRIGHT);g.drawString(font,"NAVIGATION: "+on(s.navigationEnabled())+"   ROUTE: "+on(s.routeActive()),l+190,top+92,TEXT);g.drawString(font,"HOLDS  ALT "+on(s.altitudeHold())+"  HDG "+on(s.headingHold())+"  POS "+on(s.positionHold())+"  VEL "+on(s.velocityHold()),l,top+116,MUTED);String altitudeText=altitudeTargetInput==null?String.format(Locale.ROOT,"%.1f",controllerY):altitudeTargetInput.getValue();g.drawString(font,"ALTITUDE TARGET Y: "+altitudeText+"  |  WORLD Y",l,top+180,BRIGHT);if(telemetry!=null)g.drawString(font,String.format(Locale.ROOT,"ALT %.1f  SPEED %.2f  HEADING %.1f°  TARGET %s",telemetry.y(),telemetry.speed(),normalizeDegrees(telemetry.heading()),telemetry.targetPresent()?telemetry.targetName():"NONE"),l,top+140,TEXT);}

    private void renderDiagnostics(GuiGraphics g,int l,int top){long energy=controller==null?0:controller.getEnergyStorage().getEnergyStored(),cap=controller==null?0:controller.getEnergyStorage().getMaxEnergyStored();g.drawString(font,"DIAGNOSTICS / TELEMETRY",l,top+10,TEXT);g.drawString(font,"CONTROLLER: "+(powered()?"OPERATIONAL":"OFFLINE"),l,top+40,powered()?GREEN:RED);g.drawString(font,"LINK: "+linkStatus(),l,top+62,powered()?GREEN:RED);g.drawString(font,"ENERGY: "+format(energy)+" / "+format(cap)+" FE",l,top+84,energy>0?GREEN:RED);FlightMapDiagnostics d=mapPipeline.diagnostics();g.drawString(font,"MAP ENGINE: NATIVE CPU TERRAIN",l,top+114,BRIGHT);g.drawString(font,"REQUESTED "+d.requestedCount()+"  PENDING "+d.pendingCount()+"  DECODED "+d.decodedCount()+"  FAILED "+d.failedCount(),l,top+136,MUTED);g.drawString(font,String.format(Locale.ROOT,"WORLD X %.2f  Y %.2f  Z %.2f",controllerX,controllerY,controllerZ),l,top+158,TEXT);var setup=controller==null?null:FlightSetupTelemetryClient.get(controller.getControllerId());if(setup==null){g.drawString(font,"THRUSTER SETUP: WAITING FOR SERVER TELEMETRY...",l,top+190,MUTED);return;}boolean enough=setup.upwardThrusterCount()>0&&setup.hoverFraction()<=1.0D;int c=enough?GREEN:RED;g.drawString(font,String.format(Locale.ROOT,"VESSEL MASS %.2f kg   WEIGHT %.1f N",setup.mass(),setup.weightForce()),l,top+188,TEXT);g.drawString(font,String.format(Locale.ROOT,"ENVELOPE Ø %.2f m   HEIGHT %.2f m",setup.envelopeDiameter(),setup.envelopeHeight()),l,top+210,TEXT);g.drawString(font,String.format(Locale.ROOT,"UPWARD THRUST %.1f N   LIFT THRUSTERS %d",setup.verticalMaxThrust(),setup.upwardThrusterCount()),l,top+232,c);if(Double.isFinite(setup.hoverFraction())){g.drawString(font,String.format(Locale.ROOT,"STATIC HOVER BASELINE: %.1f%% OF MAX LIFT",setup.hoverFraction()*100D),l,top+254,c);g.drawString(font,String.format(Locale.ROOT,"PER LIFT THRUSTER: ~%.1f N REQUIRED   (REDSTONE EQUIV. %d/15)",setup.recommendedOutputPerThruster(),setup.recommendedRedstonePower()),l,top+276,c);g.drawString(font,"USE THE THRUSTER'S REAL OUTPUT RANGE (NOT 1-15): SCALE EACH TO THIS FORCE / ITS MAX THRUST",l,top+298,TEXT);g.drawString(font,String.format(Locale.ROOT,"LIFT RESERVE: %+.1f%%   CURRENT LIFT: %.1f%%",setup.liftMargin()*100D,setup.currentVerticalFraction()*100D),l,top+320,MUTED);}else{g.drawString(font,"HOVER BASELINE: NO UPWARD THRUSTERS DETECTED",l,top+254,RED);g.drawString(font,"LINK UPWARD/LIFT THRUSTERS BEFORE ENABLING STABILISATION",l,top+276,RED);}}
    private static String format(long v){return String.format(Locale.ROOT,"%,d",Math.max(0,v));} private static double normalizeDegrees(double d){double v=d%360D;return v<0?v+360D:v;}

    @Override public boolean mouseClicked(double x,double y,int button){if(tab==Tab.MAP&&button==0&&x>=mapLeft()&&x<mapRight()&&y>=mapTop()&&y<mapBottom()){dragging=true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(tab==Tab.MAP&&dragging&&button==0){centerX-=dx;centerZ-=dy;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    private int mapLeft(){return left();} private int mapRight(){return left()+panelWidth()-36;} private int mapTop(){return top()+43;} private int mapBottom(){return top()+365;}
    @Override public boolean isPauseScreen(){return false;}
}
