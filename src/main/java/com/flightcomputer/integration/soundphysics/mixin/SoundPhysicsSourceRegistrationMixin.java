package com.flightcomputer.integration.soundphysics.mixin;

import com.flightcomputer.integration.soundphysics.SableAcousticCache;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers Flight Computer sound positions before SPR processes them. */
@Mixin(targets = "net.minecraft.client.sounds.SoundManager", remap = false)
public abstract class SoundPhysicsSourceRegistrationMixin {
    @Inject(method = "play", at = @At("HEAD"), require = 0)
    private void flightcomputer$registerSound(SoundInstance sound, CallbackInfo ci) {
        if (sound == null || sound.getLocation() == null) return;
        if (!"flightcomputer".equals(sound.getLocation().getNamespace())) return;
        SableAcousticCache.registerSource(new net.minecraft.world.phys.Vec3(sound.getX(), sound.getY(), sound.getZ()));
    }
}
