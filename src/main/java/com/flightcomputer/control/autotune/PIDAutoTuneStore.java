package com.flightcomputer.control.autotune;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side profile cache keyed to the Flight Computer controller UUID. */
public final class PIDAutoTuneStore {
    private static final ConcurrentHashMap<UUID, TuningResult> PROFILES = new ConcurrentHashMap<>();
    private PIDAutoTuneStore() { }

    public static TuningResult get(UUID id) { return id == null ? null : PROFILES.get(id); }
    public static void put(UUID id, TuningResult profile) { if (id != null && profile != null) PROFILES.put(id, profile); }
    public static void clear(UUID id) { if (id != null) PROFILES.remove(id); }

    public static CompoundTag toTag(TuningResult p) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Tuned", true);
        t.putInt("Version", p.version());
        t.putLong("Fingerprint", p.fingerprint());
        put(t, "Pitch", p.pitch()); put(t, "Roll", p.roll()); put(t, "Yaw", p.yaw());
        put(t, "Vertical", p.vertical()); put(t, "Longitudinal", p.longitudinal()); put(t, "Lateral", p.lateral());
        return t;
    }

    public static TuningResult fromTag(CompoundTag t) {
        if (t == null || !t.getBoolean("Tuned")) return null;
        return new TuningResult(read(t, "Pitch"), read(t, "Roll"), read(t, "Yaw"), read(t, "Vertical"),
                read(t, "Longitudinal"), read(t, "Lateral"), t.getLong("Fingerprint"), Math.max(1, t.getInt("Version")));
    }

    private static void put(CompoundTag parent, String name, TuningResult.Gains g) {
        CompoundTag t = new CompoundTag();
        t.putDouble("P", g.p()); t.putDouble("I", g.i()); t.putDouble("D", g.d()); t.putDouble("Max", g.maxOutput());
        parent.put(name, t);
    }
    private static TuningResult.Gains read(CompoundTag parent, String name) {
        CompoundTag t = parent.getCompound(name);
        return new TuningResult.Gains(t.getDouble("P"), t.getDouble("I"), t.getDouble("D"), t.getDouble("Max"));
    }
}
