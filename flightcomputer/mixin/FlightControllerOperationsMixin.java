package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightOperationsHolder;
import com.flightcomputer.avionics.FlightOperationsState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persists the expansion state without bloating the controller's core implementation. */
@Mixin(com.flightcomputer.block.FlightControllerBlockEntity.class)
public abstract class FlightControllerOperationsMixin implements FlightOperationsHolder {
    @Unique private FlightOperationsState flightComputer$operations = new FlightOperationsState();

    @Override public FlightOperationsState getFlightOperations() { return flightComputer$operations; }

    @Override public void setFlightOperations(FlightOperationsState state) {
        flightComputer$operations = state == null ? new FlightOperationsState() : state;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void flightComputer$saveOperations(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        CompoundTag operations = new CompoundTag();
        flightComputer$operations.save(operations);
        tag.put("FlightOperations", operations);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void flightComputer$loadOperations(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.contains("FlightOperations", CompoundTag.TAG_COMPOUND))
            flightComputer$operations = FlightOperationsState.load(tag.getCompound("FlightOperations"));
    }
}
