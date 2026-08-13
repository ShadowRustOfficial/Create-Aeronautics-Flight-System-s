package com.flightcomputer.integration.soundphysics;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Soft-gates optional Sound Physics/SoundManager mixins. */
public final class SoundPhysicsMixinPlugin implements IMixinConfigPlugin {
    private static final String SPR = "com.sonicether.soundphysics.SoundPhysics";
    private static final String SOUND_MANAGER = "net.minecraft.client.sounds.SoundManager";
    private static volatile Boolean available;

    private static boolean sprAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        synchronized (SoundPhysicsMixinPlugin.class) {
            cached = available;
            if (cached != null) return cached;
            try {
                Class.forName(SPR, false, SoundPhysicsMixinPlugin.class.getClassLoader());
                available = true;
            } catch (Throwable ignored) {
                available = false;
            }
            return available;
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!sprAvailable()) return false;
        if (mixinClassName.endsWith("SoundPhysicsOcclusionMixin")) return SPR.equals(targetClassName);
        if (mixinClassName.endsWith("SoundPhysicsSourceRegistrationMixin")) return SOUND_MANAGER.equals(targetClassName);
        return false;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, Class<?> targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, Class<?> targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
