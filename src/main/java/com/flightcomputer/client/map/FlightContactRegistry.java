package com.flightcomputer.client.map;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.client.gui.NavigationConsoleScreen;
import com.flightcomputer.map.FlightContact;
import com.flightcomputer.network.FlightControllerContactNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client contact cache for remote powered Flight Controllers.
 *
 * <p>The controller that owns the currently open Navigation Console is deliberately excluded
 * from this remote-contact set. The local controller already has its own map/telemetry marker
 * and must never be converted into a selectable remote contact or become the map centre merely
 * because its discovery packet was received.</p>
 */
public final class FlightContactRegistry {
    private static final ConcurrentHashMap<UUID, FlightContact> CONTACTS = new ConcurrentHashMap<>();
    private FlightContactRegistry() { }

    public static void accept(FlightContact contact) {
        if (contact == null || contact.controllerId() == null) return;
        if (isLocalController(contact.controllerId())) {
            CONTACTS.remove(contact.controllerId());
            return;
        }
        CONTACTS.put(contact.controllerId(), contact);
    }

    public static void acceptPacket(FlightControllerContactNetwork.ContactPayload payload) {
        if (payload == null || payload.controllerId() == null) return;
        if (isLocalController(payload.controllerId())) {
            CONTACTS.remove(payload.controllerId());
            return;
        }
        if (!payload.powered() || !payload.visible()) {
            CONTACTS.remove(payload.controllerId());
            return;
        }
        CONTACTS.put(payload.controllerId(), new FlightContact(
                payload.controllerId(),
                payload.subLevelName(),
                payload.flightId(),
                "",
                payload.x(), payload.y(), payload.z(),
                0.0D, 0.0D, 0.0D, 0.0D,
                "POWERED", System.currentTimeMillis() / 50L));
    }

    public static FlightContact get(UUID id) {
        if (id == null || isLocalController(id)) return null;
        return CONTACTS.get(id);
    }

    /** Returns only remote powered contacts; the local Navigation Console controller is excluded. */
    public static List<FlightContact> active(long ignoredTick) {
        long tick = System.currentTimeMillis() / 50L;
        UUID localId = localControllerId();
        if (localId != null) CONTACTS.remove(localId);

        CONTACTS.values().removeIf(c -> c == null || c.isStale(tick) || (localId != null && localId.equals(c.controllerId())));
        List<FlightContact> result = new ArrayList<>(CONTACTS.values());
        result.sort(Comparator.comparing(FlightContact::displayId, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static void remove(UUID id) { if (id != null) CONTACTS.remove(id); }
    public static void clear() { CONTACTS.clear(); }

    /** Resolve the controller that owns the open Navigation Console without using marker coordinates. */
    private static UUID localControllerId() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null || !(minecraft.screen instanceof NavigationConsoleScreen screen)) {
                return null;
            }
            BlockEntity be = minecraft.level.getBlockEntity(screen.controllerPos());
            return be instanceof FlightControllerBlockEntity controller ? controller.getControllerId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isLocalController(UUID id) {
        UUID localId = localControllerId();
        return localId != null && localId.equals(id);
    }
}
