package com.flightcomputer.client.xaerobridge.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BridgeLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("FlightComputer/XaeroBridge");

    private BridgeLog() {}

    public static void info(String message) { LOGGER.info(message); }
    public static void warn(String message) { LOGGER.warn(message); }
}
