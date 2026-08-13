package com.flightcomputer.integration.soundphysics;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates optional integration mixins by target class name. Sound Physics Remastered is a
 * hard dependency for this branch, but keeping the guard prevents a malformed target class
 * from turning a mixin lookup into an uncontrolled startup failure.
 */
public final class SoundPhysicsMixinPlugin implements IMixinConfigPlugin {
    private static final String SPR = "com.sonicether.soundphysics.SoundPhysics";
    private static final String SOUND_MANAGER = "net.minecraft.client.sounds.SoundManager";

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("SoundPhysicsOcclusionMixin")) {
            return SPR.equals(targetClassName);
        }
        if (mixinClassName.endsWith("SoundPhysicsSourceRegistrationMixin")) {
            return SOUND_MANAGER.equals(targetClassName);
        }
        return false;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) { }
}
