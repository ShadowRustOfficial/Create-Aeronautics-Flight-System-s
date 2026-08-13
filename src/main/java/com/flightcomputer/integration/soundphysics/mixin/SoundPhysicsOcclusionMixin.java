package com.flightcomputer.integration.soundphysics.mixin;

import com.flightcomputer.integration.soundphysics.SableAcousticCache;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds prepared Sable hull occlusion to normal Sound Physics direct occlusion. */
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsOcclusionMixin {
    @Inject(method = "calculateOcclusion", at = @At("RETURN"), cancellable = true, require = 0)
    private static void flightcomputer$addSableOcclusion(Vec3 soundPosition, Vec3 listenerPosition,
                                                          CallbackInfoReturnable<Double> cir) {
        double extra = SableAcousticCache.cachedOcclusion(soundPosition, listenerPosition);
        if (extra <= 0.0D) return;
        cir.setReturnValue(Math.min(12.0D, cir.getReturnValueD() + extra));
    }
}
