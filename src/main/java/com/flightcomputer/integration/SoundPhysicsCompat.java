package com.flightcomputer.integration;

import net.neoforged.fml.ModList;

/**
 * Optional Sound Physics integration boundary.
 *
 * Flight Computer remains fully functional without Sound Physics. When the
 * sound_physics_remastered mod id is present, Flight Computer publishes normal
 * positional BLOCKS sounds so Sound Physics Remastered / Sound Physics: Aeronautics
 * can apply its own occlusion, absorption, reverberation and supported Doppler logic.
 *
 * Sound Physics: Aeronautics deliberately keeps the upstream mod id, so one soft
 * presence check covers both upstream SPR and the Aeronautics/Sable fork.
 */
public final class SoundPhysicsCompat {
    private static final String MOD_ID = "sound_physics_remastered";

    private SoundPhysicsCompat() { }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Returns the integration mode without hard-linking against Sound Physics classes.
     * This method is intentionally informational: the actual audio engine owns all
     * acoustic processing, while Flight Computer only supplies a moving positional source.
     */
    public static String mode() {
        return isLoaded() ? "SOUND_PHYSICS" : "VANILLA_POSITIONAL";
    }
}
