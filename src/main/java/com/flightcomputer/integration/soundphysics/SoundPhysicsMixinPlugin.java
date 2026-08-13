package com.flightcomputer.integration.soundphysics;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Soft-gates the optional Sound Physics mixin. Flight Computer must remain fully
 * functional when Sound Physics Remastered is not installed or its implementation changes.
 */
public final class SoundPhysicsMixinPlugin implements IMixinConfigPlugin {
    private static final String SOUND_PHYSICS_CLASS = "com.sonicether.soundphysics.SoundPhysics";
    private static volatile Boolean available;

    private static boolean soundPhysicsAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        synchronized (SoundPhysicsMixinPlugin.class) {
            cached = available;
            if (cached != null) return cached;
            try {
                Class.forName(SOUND_PHYSICS_CLASS, false, SoundPhysicsMixinPlugin.class.getClassLoader());
                available = true;
            } catch (Throwable ignored) {
                available = false;
            }
            return available;
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return soundPhysicsAvailable() && SOUND_PHYSICS_CLASS.equals(targetClassName);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, Class<?> targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, Class<?> targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
