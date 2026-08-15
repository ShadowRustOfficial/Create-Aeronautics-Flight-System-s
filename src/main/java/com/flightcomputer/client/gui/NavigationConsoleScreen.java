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

/** Responsive Navigation Console. Route data and controls occupy separate rows and never overlap. */
public final class NavigationConsoleScreen extends Screen {
    private enum Tab { MAP, ROUTE, FLIGHT_CONTROL, DIAGNOSTICS }
    private enum TargetMode { PLAYER, HOME }
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
    private TargetMode targetMode = TargetMode.PLAYER;
    private FlightControllerBlockEntity controller;
    private EditBox targetInput, altitudeTargetInput, targetPlayerInput, homeInput, nameInput, flightIdInput;
    private Button engageButton, stabiliserButton, modeButton, autopilotButton, altitudeButton, headingButton, positionButton, velocityButton, navigationButton, setAltitudeButton;
    private Button selectWaystoneButton, selectWaypointButton, targetPlayerButton, targetHomeButton;
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
    private int panelBottom() { return top() + ((tab == Tab.FLIGHT_CONTROL || tab == Tab.DIAGNOSTICS) ? 520 : 390); }

    @Override protected void init() {
        controller = getController(); if (controller != null) showTerrain = controller.isTerrainEnabled(); updateControllerPosition();
        int l=innerLeft(), w=panelWidth()-36, y=top(), gap=8, tabW=(w-gap*3)/4;
        addRenderableWidget(Button.builder(Component.literal("MAP"), b->switchTab(Tab.MAP)).bounds(l,y,tabW,22).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b->switchTab(Tab.ROUTE)).bounds(l+tabW+gap,y,tabW,22).build());
        addRenderableWidget(Button.builder(Component.literal("FLIGHT CONTROL"), b->switchTab(Tab.FLIGHT_CONTROL)).bounds(l+(tabW+gap)*2,y,tabW,22).build());
        addRenderableWidget(Button.builder(Component.literal("DIAGNOSTICS"), b->switchTab(Tab.DIAGNOSTICS)).bounds(l+(tabW+gap)*3,y,tabW,22).build());
        int utilityY = y + 25;
        int utilityWidth = Math.max(120, (w - gap) / 2);
        addRenderableWidget(Button.builder(Component.literal("THERMAL"), b -> minecraft.setScreen(new ThermalConsoleScreen(controllerPos))).bounds(l, utilityY, utilityWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("COOLING"), b -> minecraft.setScreen(new CoolingConsoleScreen(controllerPos))).bounds(l + utilityWidth + gap, utilityY, utilityWidth, 20).build());
        if(tab==Tab.MAP) initMap(l,w); else if(tab==Tab.ROUTE) initRoute(l,w); else if(tab==Tab.FLIGHT_CONTROL) initFlightControl(l,w); else initDiagnostics(l,w);
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
        int inputWidth=Math.max(180,col*2-8);
        int altitudeRow=top()+206;
        altitudeTargetInput=new EditBox(font,l,altitudeRow,inputWidth,20,Component.literal("Altitude Y"));
        altitudeTargetInput.setValue(String.format(Locale.ROOT,"%.1f",controllerY)); altitudeTargetInput.setHint(Component.literal("World Y level")); altitudeTargetInput.setMaxLength(16); addRenderableWidget(altitudeTargetInput);
        setAltitudeButton=Button.builder(Component.literal("SET ALTITUDE TARGET"),b->sendAltitudeTarget()).bounds(l+inputWidth+8,altitudeRow,Math.max(170,col*2-8),20).build(); addRenderableWidget(setAltitudeButton);

        int half=(w-8)/2;
        int pushY=top()+148;
        int pushW=Math.max(70,(half-40)/6);
        String[] pushNames={"F","B","U","D","L","R"};
        FlightControllerAction[] pushActions={FlightControllerAction.PUSH_FORWARD,FlightControllerAction.PUSH_BACKWARD,FlightControllerAction.PUSH_UP,FlightControllerAction.PUSH_DOWN,FlightControllerAction.PUSH_LEFT,FlightControllerAction.PUSH_RIGHT};
        for(int i=0;i<6;i++) addRenderableWidget(Button.builder(Component.literal(pushNames[i]),b->send(pushActions[i])).bounds(l+i*(pushW+6),pushY,pushW,24).build());

        int targetLeft=l+half+8, targetWidth=w-half-8;
        targetPlayerButton=Button.builder(Component.literal("PLAYER"),b->{targetMode=TargetMode.PLAYER;refreshTargetLabels();}).bounds(targetLeft,targetWidth>300?pushY:pushY,targetWidth/2-4,24).build();
        targetHomeButton=Button.builder(Component.literal("HOME"),b->{targetMode=TargetMode.HOME;refreshTargetLabels();}).bounds(targetLeft+targetWidth/2+4,pushY,targetWidth/2-4,24).build();
        addRenderableWidget(targetPlayerButton); addRenderableWidget(targetHomeButton);
        targetPlayerInput=new EditBox(font,targetLeft,pushY+30,targetWidth-118,20,Component.literal("Player name")); targetPlayerInput.setValue(minecraft!=null&&minecraft.player!=null?minecraft.player.getGameProfile().name():""); targetPlayerInput.setHint(Component.literal("Player name")); targetPlayerInput.setMaxLength(32); addRenderableWidget(targetPlayerInput);
        addRenderableWidget(Button.builder(Component.literal("SET TARGET"),b->sendSelectedTarget()).bounds(targetLeft+targetWidth-110,pushY+30,110,20).build());
        homeInput=new EditBox(font,targetLeft,pushY+56,targetWidth-110,20,Component.literal("Home X Y Z")); homeInput.setHint(Component.literal("Home X Y Z")); homeInput.setMaxLength(64); loadHomeInput(); addRenderableWidget(homeInput);
        addRenderableWidget(Button.builder(Component.literal("SET HOME"),b->sendHome()).bounds(targetLeft+targetWidth-110,pushY+56,110,20).build());

        int y=top()+250;
        engageButton=Button.builder(Component.literal("SYSTEM"),b->send(FlightControllerAction.TOGGLE_ENGAGED)).bounds(l,y,col,20).build(); stabiliserButton=Button.builder(Component.literal("STABILISER"),b->send(FlightControllerAction.TOGGLE_STABILISER)).bounds(l+col+8,y,col,20).build(); modeButton=Button.builder(Component.literal("MODE"),b->send(FlightControllerAction.CYCLE_MODE)).bounds(l+(col+8)*2,y,col,20).build(); autopilotButton=Button.builder(Component.literal("AUTOPILOT"),b->send(FlightControllerAction.TOGGLE_AUTOPILOT)).bounds(l+(col+8)*3,y,col,20).build(); addRenderableWidget(engageButton);addRenderableWidget(stabiliserButton);addRenderableWidget(modeButton);addRenderableWidget(autopilotButton);
        y+=28; altitudeButton=holdButton(l,y,col,"ALTITUDE HOLD",FlightControllerAction.TOGGLE_ALTITUDE_HOLD); headingButton=holdButton(l+col+8,y,col,"HEADING HOLD",FlightControllerAction.TOGGLE_HEADING_HOLD); positionButton=holdButton(l+(col+8)*2,y,col,"POSITION HOLD",FlightControllerAction.TOGGLE_POSITION_HOLD); velocityButton=holdButton(l+(col+8)*3,y,col,"VELOCITY HOLD",FlightControllerAction.TOGGLE_VELOCITY_HOLD); addRenderableWidget(altitudeButton);addRenderableWidget(headingButton);addRenderableWidget(positionButton);addRenderableWidget(velocityButton);
        y+=28; navigationButton=Button.builder(Component.literal("NAVIGATION"),b->send(FlightControllerAction.TOGGLE_NAVIGATION)).bounds(l,y,col,20).build(); addRenderableWidget(navigationButton); addRenderableWidget(Button.builder(Component.literal("EMERGENCY SHUTDOWN"),b->send(FlightControllerAction.EMERGENCY_SHUTDOWN)).bounds(l+col+8,y,col*2+8,20).build()); addRenderableWidget(Button.builder(Component.literal("DISPLAY TEST"),b->send(FlightControllerAction.PULSE_DISPLAY)).bounds(l+(col+8)*3,y,col,20).build()); refreshControlLabels(); refreshTargetLabels();
    }

    private void initDiagnostics(int l,int w){
        int half=(w-8)/2, y=top()+220;
        nameInput=new EditBox(font,l,y,half-118,20,Component.literal("Sub Level Name")); nameInput.setMaxLength(64); flightIdInput=new EditBox(font,l+half+8,y,half-118,20,Component.literal("Flight ID")); flightIdInput.setMaxLength(32);
        if(controller instanceof FlightIdentityAccess identity){nameInput.setValue(identity.flightcomputer$getSubLevelName());flightIdInput.setValue(identity.flightcomputer$getFlightId());}
        addRenderableWidget(nameInput); addRenderableWidget(flightIdInput);
        addRenderableWidget(Button.builder(Component.literal("SET NAME"),b->setIdentityName()).bounds(l+half-110,y,102,20).build());
        addRenderableWidget(Button.builder(Component.literal("SET ID"),b->setIdentityId()).bounds(l+w-half+half-110,y,102,20).build());
    }

    private void setIdentityName(){if(nameInput!=null)FlightComputerNetwork.sendTarget(controllerPos,0,0,0,"__SET_NAME__:"+nameInput.getValue().trim());}
    private void setIdentityId(){if(flightIdInput!=null)FlightComputerNetwork.sendTarget(controllerPos,0,0,0,"__SET_ID__:"+flightIdInput.getValue().trim());}
    private void loadHomeInput(){if(homeInput==null)return;Vec3 home=null;if(controller instanceof FlightIdentityAccess identity&&minecraft!=null&&minecraft.player!=null)home=identity.flightcomputer$getHome(minecraft.player.getUUID());if(home==null&&minecraft!=null&&minecraft.player!=null)home=minecraft.player.position();if(home!=null)homeInput.setValue(String.format(Locale.ROOT,"%.1f %.1f %.1f",home.x,home.y,home.z));}
    private void sendHome(){if(homeInput==null)return;String[] p=homeInput.getValue().trim().split("\\s+");if(p.length!=3)return;try{double x=Double.parseDouble(p[0]),y=Double.parseDouble(p[1]),z=Double.parseDouble(p[2]);if(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z))FlightComputerNetwork.sendTarget(controllerPos,x,y,z,"__SET_HOME__");}catch(NumberFormatException ignored){}}
    private void sendSelectedTarget(){if(targetMode==TargetMode.HOME){FlightComputerNetwork.sendTarget(controllerPos,0,0,0,"__HOME__");return;}if(targetPlayerInput==null)return;String name=targetPlayerInput.getValue().trim();if(!name.isEmpty())FlightComputerNetwork.sendTarget(controllerPos,0,0,0,"__PLAYER__:"+name);}
    private void refreshTargetLabels(){if(targetPlayerButton!=null)targetPlayerButton.setMessage(Component.literal(targetMode==TargetMode.PLAYER?"PLAYER [SELECTED]":"PLAYER"));if(targetHomeButton!=null)targetHomeButton.setMessage(Component.literal(targetMode==TargetMode.HOME?"HOME [SELECTED]":"HOME"));}

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

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){int l=left(),r=l+panelWidth(),t=top();g.fill(l-8,t-8,r+8,Math.min(height-8,panelBottom()),PANEL);g.drawString(font,"◈ NAVIGATION CONSOLE",l,t-1,TEXT);g.drawString(font,"LINK: "+linkStatus(),r-120,t-1,powered()?GREEN:RED);if(tab==Tab.MAP)renderMap(g,l,t+35);else if(tab==Tab.ROUTE)renderRoute(g,l,t+35);else if(tab==Tab.FLIGHT_CONTROL)renderFlight(g,l,t+35);else renderDiagnostics(g,l,t+35);super.render(g,mouseX,mouseY,partialTick);int tabW=(panelWidth()-52)/4;g.fill(innerLeft()+tab.ordinal()*(tabW+8),t+20,innerLeft()+tab.ordinal()*(tabW+8)+tabW,t+22,BRIGHT);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){ }

    private void renderMap(GuiGraphics g,int l,int top){int ml=l,mt=top+8,mr=l+panelWidth()-36,mb=top+330;g.fill(ml,mt,mr,mb,MAP_BG);g.enableScissor(ml,mt,mr,mb);if(showTerrain&&minecraft!=null&&minecraft.level!=null)renderTerrain(g,minecraft.level,ml,mt,mr,mb);if(showFlightMap)renderPositions(g,ml,mt,mr-ml,mb-mt);if(showWaypoints){renderMarkers(g,waypoints.markers(),ml,mt,mr-ml,mb-mt,CYAN);renderMarkers(g,waystones.markers(),ml,mt,mr-ml,mb-mt,WAYSTONE);}g.disableScissor();FlightMapDiagnostics d=mapPipeline.diagnostics();boolean online=showTerrain&&d.provider()==FlightMapProviderKind.NATIVE_JOURNEYMAP_INSPIRED;g.drawString(font,"NATIVE TERRAIN: "+(online?"ONLINE":"OFFLINE"),ml+8,mt+8,online?GREEN:RED);g.drawString(font,"WAYPOINTS: "+waypoints.markers().size(),ml+8,mt+22,CYAN);g.drawString(font,"WAYSTONES: "+waystones.markers().size(),ml+140,mt+22,WAYSTONE);g.drawString(font,String.format(Locale.ROOT,"CENTRE X %.1f  Z %.1f",centerX,centerZ),ml+8,mb-28,MUTED);g.drawString(font,"DRAG TO PAN | 1 BLOCK/PIXEL",ml+8,mb-14,MUTED);}
    private void renderTerrain(net.minecraft.client.gui.GuiGraphics g,net.minecraft.client.multiplayer.ClientLevel level,int l,int t,int r,int b){int tile=16,step=2;int minX=(int)Math.floor((centerX-(r-l)/2D)/16D)-1,maxX=(int)Math.floor((centerX+(r-l)/2D)/16D)+1;int minZ=(int)Math.floor((centerZ-(b-t)/2D)/16D)-1,maxZ=(int)Math.floor((centerZ+(b-t)/2D)/16D)+1;for(int cz=minZ;cz<=maxZ;cz++)for(int cx=minX;cx<=maxX;cx++){int[] data=mapPipeline.getCachedTile(level,cx,cz);int px=(int)(l+(cx*16-centerX)+(r-l)/2D),py=(int)(t+(cz*16-centerZ)+(b-t)/2D);if(data==null){g.fill(px,py,px+tile,py+tile,0xFF171B1E);continue;}for(int yy=0;yy<tile;yy+=step){int start=0,color=data[yy*tile];for(int xx=step;xx<=tile;xx+=step){int c=xx<tile?data[yy*tile+xx]:Integer.MIN_VALUE;if(c!=color){g.fill(px+start,py+yy,px+xx,py+yy+step,color);start=xx;color=c;}}}}}
    private void renderPositions(GuiGraphics g,int l,int t,int w,int h){if(minecraft!=null&&minecraft.player!=null){Vec3 p=positionResolver.resolve(minecraft.level,minecraft.player.position());if(p!=null)diamond(g,screenX(p.x,l,w),screenZ(p.z,t,h),CYAN);}}
    private int screenX(double x,int l,int w){return (int)(l+w/2D+(x-centerX));}
    private int screenZ(double z,int t,int h){return (int)(t+h/2D+(z-centerZ));}
    private void diamond(GuiGraphics g,int x,int y,int color){g.fill(x-3,y,x+4,y+1,color);g.fill(x-2,y-1,x+3,y+2,color);g.fill(x-1,y-2,x+2,y+3,color);}
    private void renderMarkers(GuiGraphics g,List<FlightMapMarker> markers,int l,int t,int w,int h,int color){for(FlightMapMarker marker:markers){int x=screenX(marker.worldX(),l,w),y=screenZ(marker.worldZ(),t,h);if(x>=l&&x<l+w&&y>=t&&y<t+h){diamond(g,x,y,color);g.drawString(font,marker.label(),x+6,y-4,color);}}}
    private void renderRoute(GuiGraphics g,int l,int top){g.drawString(font,"ROUTE / FLIGHT PLAN",l,top+18,TEXT);FlightComputerNetwork.TelemetryPayload telemetry=FlightComputerTelemetryClient.get(controller==null?null:controller.getControllerId());FlightControllerState state=controller==null?null:controller.getControllerState();if(telemetry!=null&&telemetry.targetPresent()){g.drawString(font,"DESTINATION: "+(telemetry.targetName().isBlank()?"NAVIGATION TARGET":telemetry.targetName()),l,top+58,CYAN);g.drawString(font,String.format(Locale.ROOT,"CURRENT  X %.1f  Y %.1f  Z %.1f",telemetry.x(),telemetry.y(),telemetry.z()),l,top+82,TEXT);g.drawString(font,String.format(Locale.ROOT,"TARGET   X %.1f  Y %.1f  Z %.1f",telemetry.targetX(),telemetry.targetY(),telemetry.targetZ()),l,top+106,TEXT);double bearing=Math.toDegrees(Math.atan2(telemetry.targetX()-telemetry.x(),telemetry.targetZ()-telemetry.z()));if(bearing<0)bearing+=360.0;g.drawString(font,String.format(Locale.ROOT,"ALT %.1f m   DIST %.1f m   BRG %.1f°   HDG %.1f°   SPEED %.2f m/s",telemetry.y(),telemetry.distance(),bearing,telemetry.heading(),telemetry.speed()),l,top+130,TEXT);String mode=state==null?"UNKNOWN":state.flightMode().name();String route=state!=null&&state.routeActive()?"ACTIVE":"IDLE";g.drawString(font,"MODE: "+mode+"   ROUTE: "+route+"   NAVIGATION: "+(state!=null&&state.navigationEnabled()?"ON":"OFF"),l,top+154,telemetry.targetPresent()&&state!=null&&state.flightMode()==FlightMode.AUTOPILOT?GREEN:MUTED);g.drawString(font,"TARGET SOURCE: "+telemetry.targetName(),l,top+178,MUTED);}else g.drawString(font,"NO ACTIVE NAVIGATION TARGET",l,top+58,MUTED);g.drawString(font,"WAYSTONES: "+routeWaystones.markers().size(),l,top+205,WAYSTONE);g.drawString(font,"WAYPOINTS: "+routeWaypoints.markers().size(),l+160,top+205,CYAN);}
    private void renderFlight(GuiGraphics g,int l,int top){g.drawString(font,"FLIGHT CONTROL",l,top+18,TEXT);g.drawString(font,"Manual, Stabilised and Autopilot control share the server-authoritative runtime.",l,top+38,MUTED);g.drawString(font,"Independent push controls and hold controls are available below.",l,top+56,MUTED);if(controller!=null){var s=controller.getControllerState();g.drawString(font,"MODE: "+s.flightMode().name(),l,top+84,CYAN);g.drawString(font,"NAVIGATION: "+on(s.navigationEnabled()),l+180,top+84,CYAN);g.drawString(font,"ALTITUDE HOLD: "+on(s.altitudeHold()),l+360,top+84,CYAN);g.drawString(font,"HEADING HOLD: "+on(s.headingHold()),l,top+104,CYAN);g.drawString(font,"POSITION HOLD: "+on(s.positionHold()),l+180,top+104,CYAN);g.drawString(font,"VELOCITY HOLD: "+on(s.velocityHold()),l+360,top+104,CYAN);g.drawString(font,"PUSH: F/B = FORWARD/BACK | U/D = VERTICAL | L/R = LATERAL",l,top+132,MUTED);g.drawString(font,"TARGET MODE: "+targetMode.name(),l+halfWidth(l),top+132,CYAN);}}
    private int halfWidth(int l){return l+panelWidth()/2;}
    private void renderDiagnostics(GuiGraphics g,int l,int top){FlightMapDiagnostics d=mapPipeline.diagnostics();g.drawString(font,"DIAGNOSTICS",l,top+18,TEXT);if(controller instanceof FlightIdentityAccess identity){g.drawString(font,"SUB LEVEL: "+identity.flightcomputer$getSubLevelName(),l,top+44,CYAN);g.drawString(font,"FLIGHT ID: "+identity.flightcomputer$getFlightId(),l+360,top+44,CYAN);g.drawString(font,"NAMEPLATE IDENTITY: READY",l,top+62,GREEN);}g.drawString(font,"MAP PROVIDER: "+d.provider(),l,top+88,CYAN);g.drawString(font,"MAP STATE: "+d.state(),l+360,top+88,CYAN);g.drawString(font,"CACHE HITS: "+d.cacheHits(),l,top+108,TEXT);g.drawString(font,"CACHE MISSES: "+d.cacheMisses(),l+180,top+108,TEXT);g.drawString(font,"REQUESTED: "+d.requestedTiles(),l+360,top+108,TEXT);g.drawString(font,"DECODED: "+d.decodedTiles(),l,top+128,TEXT);g.drawString(font,"FAILED: "+d.failedTiles(),l+180,top+128,TEXT);g.drawString(font,"PENDING: "+d.pendingTiles(),l+360,top+128,TEXT);var setup=FlightSetupTelemetryClient.get(controller==null?null:controller.getControllerId());if(setup!=null){g.drawString(font,"SETUP: READY",l,top+156,GREEN);g.drawString(font,"POWER "+setup.powerLevel()+"%",l,top+176,TEXT);g.drawString(font,"CONTROL "+setup.controlLevel()+"%",l+180,top+176,TEXT);g.drawString(font,"PROPULSION "+setup.propulsionLevel()+"%",l+360,top+176,TEXT);g.drawString(font,"NAVIGATION "+setup.navigationLevel()+"%",l,top+196,TEXT);}}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}