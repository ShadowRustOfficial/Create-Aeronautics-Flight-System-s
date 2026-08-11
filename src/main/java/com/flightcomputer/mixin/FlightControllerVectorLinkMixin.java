package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightMode;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.VectorDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;

/** Keeps vector links in controller-local plot coordinates and migrates old absolute links once. */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerVectorLinkMixin {
    private static final String FORMAT_TAG = "FlightComputerVectorLinkFormat";
    private static final int CURRENT_FORMAT = 1;

    @Shadow @Final private EnumMap<FlightMode, EnumMap<VectorDirection, BlockPos>> vectorLinks;

    @Redirect(method = "bindVector",
            at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object flightcomputer$storeLocalLink(EnumMap<?, ?> map, Object key, Object value) {
        if (value instanceof BlockPos target) {
            FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
            value = target.subtract(controller.getBlockPos());
        }
        @SuppressWarnings({"rawtypes", "unchecked"}) EnumMap raw = (EnumMap) map;
        return raw.put(key, value);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void flightcomputer$migrateOldAbsoluteLinks(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.getInt(FORMAT_TAG) >= CURRENT_FORMAT) return;
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        BlockPos origin = controller.getBlockPos();
        for (EnumMap<VectorDirection, BlockPos> bank : vectorLinks.values()) {
            for (VectorDirection direction : VectorDirection.values()) {
                BlockPos stored = bank.get(direction);
                if (stored != null) bank.put(direction, stored.subtract(origin));
            }
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void flightcomputer$writeLinkFormat(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt(FORMAT_TAG, CURRENT_FORMAT);
    }
}
