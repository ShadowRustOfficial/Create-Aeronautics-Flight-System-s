package com.flightcomputer.integration.soundphysics.mixin;

import com.flightcomputer.integration.soundphysics.SableAcousticCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds prepared Sable hull/interior occlusion to normal Sound Physics direct occlusion.
 * The mixin is optional and only affects the external Sound Physics class when it is present.
 */
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsOcclusionMixin {
    @Inject(method = "calculateOcclusion", at = @At("RETURN"), cancellable = true, require = 0)
    private static void flightcomputer$addSableOcclusion(
            Vec3 soundPosition,
            Vec3 listenerPosition,
            SoundSource category,
            ResourceLocation sound,
            CallbackInfoReturnable<Double> cir) {
        if (soundPosition == null || listenerPosition == null) return;

        // The acoustic cache is prepared on the client thread. Only the immutable prepared
        // result is queried here, avoiding world/Sable access from Sound Physics' processing path.
        double extra = SableAcousticCache.cachedOcclusion(soundPosition, listenerPosition);
        if (extra <= 0.0D || !Double.isFinite(extra)) return;

        double base = cir.getReturnValueD();
        if (!Double.isFinite(base)) return;
        cir.setReturnValue(Math.min(12.0D, Math.max(0.0D, base) + extra));
    }
}
