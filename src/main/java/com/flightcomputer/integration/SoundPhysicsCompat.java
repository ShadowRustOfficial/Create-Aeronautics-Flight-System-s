package com.flightcomputer.integration;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional Sound Physics / Sable compatibility boundary.
 *
 * Flight Computer never hard-links against Sound Physics or Sable classes.
 * Runtime reflection is used only for optional Sable listener/sublevel checks;
 * if Sable is absent or its API is unavailable, the safe fallback is to treat a
 * player as external so an external-only flyby is not incorrectly suppressed.
 */
public final class SoundPhysicsCompat {
    private static final String SOUND_PHYSICS_MOD_ID = "sound_physics_remastered";
    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";

    private static volatile boolean sableLookupInitialised;
    private static volatile Object sableHelper;
    private static volatile Method containingEntityMethod;
    private static volatile Method containingPositionMethod;
    private static volatile Method subLevelIdMethod;

    private SoundPhysicsCompat() { }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(SOUND_PHYSICS_MOD_ID);
    }

    /** Returns the optional acoustic integration mode. */
    public static String mode() {
        return isLoaded() ? "SOUND_PHYSICS" : "VANILLA_POSITIONAL";
    }

    /**
     * True when the listener is outside the Sable sublevel containing the source.
     *
     * This is specifically for external-only effects such as aircraft flybys.
     * A player inside the same moving sublevel as the source is rejected before
     * the sound is created, rather than relying on acoustic attenuation to make
     * the effect merely quieter.
     */
    public static boolean isPlayerOutsideSourceSubLevel(Entity player, Level level, Vec3 sourcePosition) {
        if (player == null || level == null || sourcePosition == null) return true;
        if (!ensureSableApi()) return true;

        try {
            Object playerSubLevel = containingEntityMethod.invoke(sableHelper, player);
            Object sourceSubLevel = containingPositionMethod.invoke(sableHelper, level, sourcePosition);

            UUID playerId = subLevelUuid(playerSubLevel);
            UUID sourceId = subLevelUuid(sourceSubLevel);

            if (playerId == null || sourceId == null) {
                // No containing sublevel means the listener/source is in the normal world.
                // Different null/non-null states are therefore external to each other.
                return playerId == null || sourceId == null;
            }
            return !playerId.equals(sourceId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    private static UUID subLevelUuid(Object subLevel) throws ReflectiveOperationException {
        if (subLevel == null) return null;
        return (UUID) subLevelIdMethod.invoke(subLevel);
    }

    private static boolean ensureSableApi() {
        if (sableLookupInitialised) return sableHelper != null;
        synchronized (SoundPhysicsCompat.class) {
            if (sableLookupInitialised) return sableHelper != null;
            try {
                if (!ModList.get().isLoaded("sable")) return false;

                Class<?> sableClass = Class.forName(SABLE_CLASS, false, SoundPhysicsCompat.class.getClassLoader());
                Field helperField = sableClass.getField("HELPER");
                Object helper = helperField.get(null);
                if (helper == null) return false;

                containingEntityMethod = helper.getClass().getMethod("getContaining", Entity.class);
                containingPositionMethod = helper.getClass().getMethod("getContaining", Level.class, net.minecraft.core.Position.class);
                subLevelIdMethod = containingEntityMethod.getReturnType().getMethod("getUniqueId");
                sableHelper = helper;
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                sableHelper = null;
                return false;
            } finally {
                sableLookupInitialised = true;
            }
        }
    }
}
