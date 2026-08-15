package com.flightcomputer.mixin;

import com.flightcomputer.identity.FlightIdentityAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(com.flightcomputer.block.FlightControllerBlockEntity.class)
public abstract class FlightControllerIdentityMixin implements FlightIdentityAccess {
    @Unique private String flightcomputer$subLevelName = "Unnamed Sub Level";
    @Unique private String flightcomputer$flightId = "UNASSIGNED";
    @Unique private final Map<UUID, Vec3> flightcomputer$homes = new HashMap<>();

    @Override public String flightcomputer$getSubLevelName() { return flightcomputer$subLevelName; }

    @Override public void flightcomputer$setSubLevelName(String name) {
        flightcomputer$subLevelName = sanitize(name, "Unnamed Sub Level", 64);
        flightcomputer$sync();
    }

    @Override public String flightcomputer$getFlightId() { return flightcomputer$flightId; }

    @Override public void flightcomputer$setFlightId(String id) {
        flightcomputer$flightId = sanitize(id, "UNASSIGNED", 32);
        flightcomputer$sync();
    }

    @Override public Vec3 flightcomputer$getHome(UUID playerId) {
        return playerId == null ? null : flightcomputer$homes.get(playerId);
    }

    @Override public void flightcomputer$setHome(UUID playerId, Vec3 position) {
        if (playerId == null || position == null || !finite(position)) return;
        flightcomputer$homes.put(playerId, position);
        flightcomputer$sync();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void flightcomputer$saveIdentity(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putString("FlightSubLevelName", flightcomputer$subLevelName);
        tag.putString("FlightId", flightcomputer$flightId);
        ListTag homes = new ListTag();
        for (Map.Entry<UUID, Vec3> entry : flightcomputer$homes.entrySet()) {
            Vec3 p = entry.getValue();
            if (!finite(p)) continue;
            CompoundTag home = new CompoundTag();
            home.putUUID("Player", entry.getKey());
            home.putDouble("X", p.x);
            home.putDouble("Y", p.y);
            home.putDouble("Z", p.z);
            homes.add(home);
        }
        tag.put("FlightHomes", homes);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void flightcomputer$loadIdentity(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        flightcomputer$subLevelName = sanitize(tag.getString("FlightSubLevelName"), "Unnamed Sub Level", 64);
        flightcomputer$flightId = sanitize(tag.getString("FlightId"), "UNASSIGNED", 32);
        flightcomputer$homes.clear();
        if (tag.contains("FlightHomes", Tag.TAG_LIST)) {
            ListTag homes = tag.getList("FlightHomes", Tag.TAG_COMPOUND);
            for (int i = 0; i < homes.size(); i++) {
                CompoundTag home = homes.getCompound(i);
                try {
                    UUID player = home.getUUID("Player");
                    Vec3 position = new Vec3(home.getDouble("X"), home.getDouble("Y"), home.getDouble("Z"));
                    if (finite(position)) flightcomputer$homes.put(player, position);
                } catch (RuntimeException ignored) { }
            }
        }
    }

    @Unique private void flightcomputer$sync() {
        BlockEntity self = (BlockEntity)(Object)this;
        self.setChanged();
        if (self.getLevel() != null && !self.getLevel().isClientSide())
            self.getLevel().sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
    }

    @Unique private static String sanitize(String value, String fallback, int maxLength) {
        if (value == null) return fallback;
        String clean = value.trim();
        if (clean.isEmpty()) return fallback;
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    @Unique private static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
}