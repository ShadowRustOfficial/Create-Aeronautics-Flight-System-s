package com.flightcomputer.link;

import net.minecraft.core.BlockPos;
import java.util.*;

/** First-party link data model; the actual Create redstone/vector adapters can be added without changing the UI. */
public final class ControllerLinkRegistry {
    private static final Map<BlockPos, Map<String, VectorLink>> LINKS = new HashMap<>();
    private ControllerLinkRegistry() {}

    public static void bind(BlockPos controller, String vector, BlockPos target, String mode) {
        LINKS.computeIfAbsent(controller, p -> new HashMap<>()).put(vector, new VectorLink(vector, target, mode));
    }
    public static void unbind(BlockPos controller, String vector) {
        Map<String, VectorLink> links = LINKS.get(controller);
        if (links != null) {
            links.remove(vector);
            if (links.isEmpty()) LINKS.remove(controller);
        }
    }
    public static Collection<VectorLink> links(BlockPos controller) {
        return Collections.unmodifiableCollection(LINKS.getOrDefault(controller, Map.of()).values());
    }
}
