package com.flightcomputer.client.gui;

/**
 * Intentionally empty compatibility shell.
 *
 * Thermal and Cooling entry buttons are owned exclusively by NavigationConsoleScreen now.
 * Keeping this class without a ScreenEvent subscriber prevents stale overlay widgets from
 * being injected a second time after a page switch.
 */
public final class ThermalConsoleTab {
    private ThermalConsoleTab() {}
}
