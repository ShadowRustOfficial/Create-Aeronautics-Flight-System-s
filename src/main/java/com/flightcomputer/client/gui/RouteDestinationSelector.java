package com.flightcomputer.client.gui;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.client.map.FlightMapMarker;
import com.flightcomputer.client.map.WaypointMapProvider;
import com.flightcomputer.client.map.WaystoneMapProvider;
import com.flightcomputer.network.FlightComputerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/** Adds destination selectors only when the Navigation Console is actually on ROUTE. */
@EventBusSubscriber(modid = FlightComputer.MOD_ID, value = Dist.CLIENT)
public final class RouteDestinationSelector {
    private static final WaystoneMapProvider WAYSTONES = new WaystoneMapProvider();
    private static final WaypointMapProvider WAYPOINTS = new WaypointMapProvider();
    private static int waystoneIndex;
    private static int waypointIndex;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof NavigationConsoleScreen screen)) return;
        // ROUTE is the only existing console page with the destination EditBox.
        if (screen.children().stream().noneMatch(child -> child instanceof EditBox)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || screen.controllerPos() == null) return;

        WAYSTONES.tick(mc.level);
        WAYPOINTS.tick(mc.level);
        int left = Math.max(10, (screen.width - 640) / 2);
        int top = 20;
        int y = top + 230;

        event.addListener(Button.builder(Component.literal("SELECT WAYSTONE"), button -> selectWaystone(screen, button))
                .bounds(left + 20, y, 180, 20).build());
        event.addListener(Button.builder(Component.literal("SELECT WAYPOINT"), button -> selectWaypoint(screen, button))
                .bounds(left + 210, y, 180, 20).build());
        event.addListener(Button.builder(Component.literal("REFRESH LOCATIONS"), button -> refresh(screen))
                .bounds(left + 400, y, 180, 20).build());
    }

    private static void selectWaystone(NavigationConsoleScreen screen, Button button) {
        Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return;
        WAYSTONES.tick(mc.level); List<FlightMapMarker> markers = WAYSTONES.markers();
        if (markers.isEmpty()) { button.setMessage(Component.literal("NO WAYSTONES")); return; }
        waystoneIndex = Math.floorMod(waystoneIndex, markers.size());
        FlightMapMarker marker = markers.get(waystoneIndex++); setDestination(screen, marker);
        button.setMessage(Component.literal("WAYSTONE: " + marker.label()));
    }

    private static void selectWaypoint(NavigationConsoleScreen screen, Button button) {
        Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return;
        WAYPOINTS.tick(mc.level); List<FlightMapMarker> markers = WAYPOINTS.markers();
        if (markers.isEmpty()) { button.setMessage(Component.literal("NO WAYPOINTS")); return; }
        waypointIndex = Math.floorMod(waypointIndex, markers.size());
        FlightMapMarker marker = markers.get(waypointIndex++); setDestination(screen, marker);
        button.setMessage(Component.literal("WAYPOINT: " + marker.label()));
    }

    private static void setDestination(NavigationConsoleScreen screen, FlightMapMarker marker) {
        FlightComputerNetwork.sendTarget(screen.controllerPos(), marker.worldX(), marker.worldY(), marker.worldZ(), marker.label());
    }

    private static void refresh(NavigationConsoleScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) { WAYSTONES.tick(mc.level); WAYPOINTS.tick(mc.level); }
        waystoneIndex = 0; waypointIndex = 0;
    }

    private RouteDestinationSelector() {}
}
