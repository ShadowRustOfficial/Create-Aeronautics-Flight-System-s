package com.flightcomputer.mixin;

import com.flightcomputer.identity.FlightIdentityAccess;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flight Computer identity storage.
 *
 * The Sub-Level Name is deliberately NOT a second custom name. It is the actual Sable
 * SubLevel display name, the same field changed by /sable name set. This keeps the
 * Navigation Console, Sable's nameplate/debug output and other Sable consumers in sync.
 */
@Mixin(com.flightcomputer.block.FlightControllerBlockEntity.class)
public abstract class FlightControllerIdentityMixin implements FlightIdentityAccess {
    @Unique private String flightComputer$legacySubLevelName = "";
    @Unique private String flightComputer$flightId = "";
    @Unique private final Map<UUID, Vec3> flightComputer$homes = new HashMap<>();

    @Override
    public String flightcomputer$getSubLevelName() {
        String sableName = flightComputer$readSableSubLevelName();
        return sableName != null ? sableName : flightComputer$legacySubLevelName;
    }

    @Override
    public void flightcomputer$setSubLevelName(String name) {
        String cleaned = sanitise(name, 64, "");
        flightComputer$legacySubLevelName = cleaned;

        BlockEntity controller = (BlockEntity) (Object) this;
        Level level = controller.getLevel();
        if (level != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                // Sable resolves plots by GLOBAL CHUNK position, not BlockPos.
                // The previous implementation passed BlockPos directly to a method
                // that does not exist in Sable's API, which is why v10 did not compile.
                LevelPlot plot = container.getPlot(new ChunkPos(controller.getBlockPos()));
                if (plot != null) {
                    SubLevel subLevel = plot.getSubLevel();
                    if (subLevel != null) {
                        // This is the same setter used by Sable's /sable name set command.
                        // ServerSubLevel handles the authoritative value; Sable's normal
                        // name packet path remains responsible for client synchronization.
                        subLevel.setName(cleaned.isEmpty() ? null : cleaned);
                    }
                }
            }
        }
        controller.setChanged();
    }

    @Override
    public String flightcomputer$getFlightId() {
        return flightComputer$flightId;
    }

    @Override
    public void flightcomputer$setFlightId(String id) {
        flightComputer$flightId = sanitise(id, 32, "");
        ((BlockEntity) (Object) this).setChanged();
    }

    @Override
    public Vec3 flightcomputer$getHome(UUID playerId) {
        return playerId == null ? null : flightComputer$homes.get(playerId);
    }

    @Override
    public void flightcomputer$setHome(UUID playerId, Vec3 position) {
        if (playerId == null || position == null
                || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) return;
        flightComputer$homes.put(playerId, position);
        ((BlockEntity) (Object) this).setChanged();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void flightComputer$saveIdentity(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        String sableName = flightComputer$readSableSubLevelName();
        tag.putString("FlightSubLevelName", sableName == null ? flightComputer$legacySubLevelName : sableName);
        tag.putString("FlightId", flightComputer$flightId);
        ListTag homes = new ListTag();
        for (Map.Entry<UUID, Vec3> entry : flightComputer$homes.entrySet()) {
            Vec3 pos = entry.getValue();
            CompoundTag home = new CompoundTag();
            home.putUUID("Player", entry.getKey());
            home.putDouble("X", pos.x);
            home.putDouble("Y", pos.y);
            home.putDouble("Z", pos.z);
            homes.add(home);
        }
        tag.put("FlightHomes", homes);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void flightComputer$loadIdentity(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        flightComputer$legacySubLevelName = sanitise(tag.getString("FlightSubLevelName"), 64, "");
        flightComputer$flightId = sanitise(tag.getString("FlightId"), 32, "");
        flightComputer$homes.clear();
        if (tag.contains("FlightHomes", CompoundTag.TAG_LIST)) {
            ListTag homes = tag.getList("FlightHomes", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < homes.size(); i++) {
                CompoundTag home = homes.getCompound(i);
                if (!home.hasUUID("Player")) continue;
                double x = home.getDouble("X");
                double y = home.getDouble("Y");
                double z = home.getDouble("Z");
                if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z))
                    flightComputer$homes.put(home.getUUID("Player"), new Vec3(x, y, z));
            }
        }
    }

    @Unique
    private String flightComputer$readSableSubLevelName() {
        BlockEntity controller = (BlockEntity) (Object) this;
        Level level = controller.getLevel();
        if (level == null) return null;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        LevelPlot plot = container.getPlot(new ChunkPos(controller.getBlockPos()));
        if (plot == null) return null;
        SubLevel subLevel = plot.getSubLevel();
        return subLevel == null ? null : subLevel.getName();
    }

    @Unique
    private static String sanitise(String value, int maxLength, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return fallback;
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
