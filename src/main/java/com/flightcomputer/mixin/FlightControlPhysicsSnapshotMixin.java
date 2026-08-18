package com.flightcomputer.mixin;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.SableDynamicsReader;
import com.flightcomputer.control.VehicleState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the adaptive controller fed from Sable's live physics panel without compile-time Sable imports. */
@Mixin(targets = "com.flightcomputer.control.FlightControlRuntimeManager$Runtime")
public abstract class FlightControlPhysicsSnapshotMixin {
    @Shadow private VehicleState snapshot;

    @Inject(method = "update", at = @At("TAIL"))
    private void flightcomputer$readPhysicsPanel(FlightControllerBlockEntity controller, CallbackInfo ci) {
        if(snapshot==null||controller==null||controller.getLevel()==null)return;
        try{
            Class<?> sable=Class.forName("dev.ryanhcode.sable.companion.SableCompanion",false,getClass().getClassLoader());
            Object instance=sable.getField("INSTANCE").get(null);if(instance==null)return;
            Object subLevel=null;
            try{subLevel=instance.getClass().getMethod("getContaining",net.minecraft.world.level.block.entity.BlockEntity.class).invoke(instance,controller);}catch(ReflectiveOperationException ignored){}
            if(subLevel==null){try{subLevel=instance.getClass().getMethod("getContaining",net.minecraft.world.level.Level.class,Vec3.class).invoke(instance,controller.getLevel(),Vec3.atCenterOf(controller.getBlockPos()));}catch(ReflectiveOperationException ignored){}}
            if(subLevel!=null)SableDynamicsReader.readPhysicsPanel(subLevel,snapshot);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
    }
}
