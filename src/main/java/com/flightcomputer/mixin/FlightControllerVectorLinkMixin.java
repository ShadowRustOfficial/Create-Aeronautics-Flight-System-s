package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps vector links in controller-local coordinates. Sable sub-levels move and rotate, so storing
 * the original world BlockPos makes a thruster disappear as soon as the craft is assembled/moved.
 */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerVectorLinkMixin {
    @Redirect(
            method = "bindVector",
            at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object flightcomputer$storeLocalLink(EnumMap<?, ?> map, Object key, Object value) {
        if (value instanceof BlockPos target) {
            FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
            BlockPos origin = controller.getBlockPos();
            value = target.subtract(origin);
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        EnumMap raw = (EnumMap) map;
        return raw.put(key, value);
    }

    @Redirect(
            method = "getVectorLinks",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;"))
    private Map<?, ?> flightcomputer$normalizeStoredLinks(Map<?, ?> stored) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        BlockPos origin = controller.getBlockPos();
        Map<Object, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : stored.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof BlockPos pos) {
                int dx = pos.getX() - origin.getX();
                int dy = pos.getY() - origin.getY();
                int dz = pos.getZ() - origin.getZ();
                // Old revisions stored absolute world positions. Current links are local offsets.
                // A working vehicle bank is intentionally local and within the registry scan range.
                if (Math.abs(dx) > 128 || Math.abs(dy) > 128 || Math.abs(dz) > 128)
                    value = pos.subtract(origin);
            }
            normalized.put(entry.getKey(), value);
        }
        return Map.copyOf(normalized);
    }
}
