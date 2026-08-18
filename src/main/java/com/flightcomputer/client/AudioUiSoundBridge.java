package com.flightcomputer.client;

import com.flightcomputer.avionics.FlightHold;
import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.gui.CoolingConsoleScreen;
import com.flightcomputer.client.gui.FlightOperationsScreen;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.client.gui.ThermalConsoleScreen;
import com.flightcomputer.network.FlightComputerUiSoundNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import java.util.Locale;

public final class AudioUiSoundBridge {
    private AudioUiSoundBridge() {}
    public enum Kind { TOGGLE_ON, TOGGLE_OFF, TAB, INTERACT, DISCOVER }
    public static boolean isFlightComputerScreen(Screen screen){return screen instanceof NavigationConsoleScreen||screen instanceof ThermalConsoleScreen||screen instanceof CoolingConsoleScreen||screen instanceof FlightOperationsScreen;}
    public static void play(Kind kind){Minecraft mc=Minecraft.getInstance();if(mc==null)return;BlockPos pos=controllerPos(mc.screen);if(pos!=null)FlightComputerUiSoundNetwork.request(pos,soundId(kind));}
    public static void playForButton(Button button){Minecraft mc=Minecraft.getInstance();if(button==null||mc==null||!isFlightComputerScreen(mc.screen))return;BlockPos pos=controllerPos(mc.screen);if(pos==null)return;String text=button.getMessage().getString().trim().toUpperCase(Locale.ROOT);if(text.equals("EMERGENCY SHUTDOWN")||text.equals("INSERT HELD")||text.equals("REMOVE"))return;if(mc.screen instanceof NavigationConsoleScreen&&isControllerActionButton(text))return;if(isTabButton(text)){FlightComputerUiSoundNetwork.request(pos,soundId(Kind.TAB));return;}FlightControllerBlockEntity controller=controller(mc.screen);Kind toggle=toggleKind(mc.screen,controller,text);FlightComputerUiSoundNetwork.request(pos,soundId(toggle==null?Kind.INTERACT:toggle));}
    private static boolean isControllerActionButton(String text){return text.equals("SYSTEM")||text.startsWith("SYSTEM:")||text.equals("STABILISER")||text.startsWith("STABILISER:")||text.equals("MODE")||text.startsWith("MODE:")||text.equals("AUTOPILOT")||text.startsWith("AUTOPILOT:")||text.equals("ALTITUDE HOLD")||text.startsWith("ALTITUDE HOLD:")||text.equals("HEADING HOLD")||text.startsWith("HEADING HOLD:")||text.equals("POSITION HOLD")||text.startsWith("POSITION HOLD:")||text.equals("VELOCITY HOLD")||text.startsWith("VELOCITY HOLD:")||text.equals("NAVIGATION")||text.startsWith("NAVIGATION:")||text.equals("START ROUTE")||text.equals("ABORT ROUTE")||text.equals("DISPLAY TEST")||text.equals("F")||text.equals("B")||text.equals("U")||text.equals("D")||text.equals("L")||text.equals("R");}
    private static boolean isTabButton(String text){return text.equals("MAP")||text.equals("ROUTE")||text.equals("FLIGHT CONTROL")||text.equals("DIAGNOSTICS")||text.equals("THERMAL")||text.equals("COOLING")||text.equals("NAVIGATION")||text.equals("IDENTITY")||text.equals("COMBAT")||text.equals("LANDING")||text.equals("DOCKING")||text.equals("SYSTEM");}
    private static Kind toggleKind(Screen screen,FlightControllerBlockEntity controller,String text){if(text.startsWith("TERRAIN:")||text.startsWith("FLIGHT MAP:")||text.startsWith("WAYPOINTS:")||text.startsWith("STABILISER AMBIENT:")||text.startsWith("MAP CONTACT:"))return text.contains(": ON")||text.contains(": ENGAGED")?Kind.TOGGLE_OFF:Kind.TOGGLE_ON;if(screen instanceof FlightOperationsScreen&&text.endsWith(" HOLD")&&controller instanceof FlightOperationsHolder holder){String holdName=text.substring(0,text.length()-5).trim();try{return holder.getFlightOperations().hasHold(FlightHold.valueOf(holdName))?Kind.TOGGLE_OFF:Kind.TOGGLE_ON;}catch(IllegalArgumentException ignored){return null;}}return null;}
    private static int soundId(Kind kind){return switch(kind){case TOGGLE_ON->0;case TOGGLE_OFF->1;case TAB->2;case INTERACT->3;case DISCOVER->4;};}
    private static BlockPos controllerPos(Screen screen){if(screen instanceof NavigationConsoleScreen navigation)return navigation.controllerPos();if(screen instanceof ThermalConsoleScreen thermal)return thermal.controllerPos();if(screen instanceof CoolingConsoleScreen cooling)return cooling.controllerPos();if(screen instanceof FlightOperationsScreen operations)return operations.controllerPos();return null;}
    private static FlightControllerBlockEntity controller(Screen screen){Minecraft mc=Minecraft.getInstance();if(mc==null||mc.level==null)return null;BlockPos pos=controllerPos(screen);if(pos==null)return null;return mc.level.getBlockEntity(pos) instanceof FlightControllerBlockEntity fc?fc:null;}
}
