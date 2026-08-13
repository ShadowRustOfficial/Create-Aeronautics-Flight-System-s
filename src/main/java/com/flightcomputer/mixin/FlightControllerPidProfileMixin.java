package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.autotune.PIDAutoTuneStore;
import com.flightcomputer.control.autotune.TuningResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerPidProfileMixin {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void flightcomputer$savePidProfile(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        TuningResult profile = PIDAutoTuneStore.get(controller.getControllerId());
        if (profile != null) tag.put("PIDAutoTune", PIDAutoTuneStore.toTag(profile));
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void flightcomputer$loadPidProfile(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        TuningResult profile = PIDAutoTuneStore.fromTag(tag.getCompound("PIDAutoTune"));
        if (profile != null) PIDAutoTuneStore.put(controller.getControllerId(), profile);
    }
}
