package com.flightcomputer.integration.soundphysics.mixin;

import com.flightcomputer.integration.soundphysics.SableAcousticCache;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the precomputed Sable hull/interior occlusion to normal SPR direct occlusion.
 * The mixin is optional and is only applied when the external SPR class is present.
 */
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsOcclusionMixin {
    @Inject(method = "calculateOcclusion", at = @At("RETURN"), cancellable = true, require = 0)
    private static void flightcomputer$addSableOcclusion(CallbackInfoReturnable<Double> cir) {
        // The current SPR call computes sound position and listener position before calling
        // calculateOcclusion. We cannot safely touch the Minecraft world here, so the bridge
        // is keyed by the prepared source/listener snapshot captured on the client thread.
        // A source match is enough to prevent unrelated sounds from receiving the extra hull term.
        // The snapshot itself is responsible for rejecting stale positions.
        //
        // We intentionally obtain the positions from SPR's current static context through a
        // reflection-free thread-local bridge populated by SoundPhysicsSourceRegistrationMixin.
        Vec3[] context = SoundPhysicsSourceRegistrationMixin.currentContext();
        if (context == null) return;

        double extra = SableAcousticCache.cachedOcclusion(context[0], context[1]);
        if (extra <= 0.0D) return;
        double base = cir.getReturnValueD();
        cir.setReturnValue(Math.min(12.0D, base + extra));
    }
}
