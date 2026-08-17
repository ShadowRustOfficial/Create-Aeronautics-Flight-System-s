package com.flightcomputer.mixin;

import com.flightcomputer.avionics.FlightControllerAction;
import com.flightcomputer.avionics.FlightControllerActionResult;
import com.flightcomputer.avionics.ThermalState;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.control.ControlAxis;
import com.flightcomputer.control.ManualControlBridge;
import com.flightcomputer.control.FlightMode;
import com.flightcomputer.identity.FlightIdentityAccess;
import com.flightcomputer.registry.ModSounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Edge-triggered controller audio plus persistent flight identity and manual push handling. */
@Mixin(FlightControllerBlockEntity.class)
public abstract class FlightControllerAudioMixin implements FlightIdentityAccess {
    private static final int FIRE_DELAY_TICKS = 100;
    private static final int EMERGENCY_SOUND_COOLDOWN = 200;
    private static final Map<UUID, Integer> FIRE_PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_EMERGENCY_SOUND = new ConcurrentHashMap<>();

    @Unique private String flightcomputer$subLevelName = "Unnamed Sub Level";
    @Unique private String flightcomputer$flightId = "UNASSIGNED";
    @Unique private final Map<UUID, Vec3> flightcomputer$homes = new HashMap<>();

    @Override public String flightcomputer$getSubLevelName() { return flightcomputer$subLevelName; }
    @Override public void flightcomputer$setSubLevelName(String name) {
        flightcomputer$subLevelName = sanitize(name, "Unnamed Sub Level", 64);
        flightcomputer$syncIdentity();
    }
    @Override public String flightcomputer$getFlightId() { return flightcomputer$flightId; }
    @Override public void flightcomputer$setFlightId(String id) {
        flightcomputer$flightId = sanitize(id, "UNASSIGNED", 32);
        flightcomputer$syncIdentity();
    }
    @Override public Vec3 flightcomputer$getHome(UUID playerId) { return playerId == null ? null : flightcomputer$homes.get(playerId); }
    @Override public void flightcomputer$setHome(UUID playerId, Vec3 position) {
        if (playerId == null || position == null || !finite(position)) return;
        flightcomputer$homes.put(playerId, position);
        flightcomputer$syncIdentity();
    }

    @Inject(method = "applyAction", at = @At("HEAD"), cancellable = true)
    private void flightcomputer$independentPush(FlightControllerAction action,
                                                  CallbackInfoReturnable<FlightControllerActionResult> cir) {
        if (action == null || !action.isIndependentPush()) return;
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity)(Object)this;
        if (!controller.isOperationPermitted(action)) {
            cir.setReturnValue(FlightControllerActionResult.rejected(controller.getControllerState(), action,
                    controller.isThermalLockout() ? "THERMAL_SHUTDOWN" : "NO_POWER"));
            return;
        }
        if (!controller.isEngaged()) {
            cir.setReturnValue(FlightControllerActionResult.rejected(controller.getControllerState(), action, "SYSTEM_DISENGAGED"));
            return;
        }
        ControlAxis axis;
        double value;
        switch (action) {
            case PUSH_FORWARD -> { axis = ControlAxis.LONGITUDINAL; value = 0.65D; }
            case PUSH_BACKWARD -> { axis = ControlAxis.LONGITUDINAL; value = -0.65D; }
            case PUSH_UP -> { axis = ControlAxis.VERTICAL; value = 0.65D; }
            case PUSH_DOWN -> { axis = ControlAxis.VERTICAL; value = -0.65D; }
            case PUSH_LEFT -> { axis = ControlAxis.LATERAL; value = -0.65D; }
            case PUSH_RIGHT -> { axis = ControlAxis.LATERAL; value = 0.65D; }
            default -> { return; }
        }
        ManualControlBridge.request(controller.getControllerId(), axis, value);
        cir.setReturnValue(FlightControllerActionResult.accepted(controller.getControllerState(), action, "PUSH"));
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
            home.putDouble("X", p.x); home.putDouble("Y", p.y); home.putDouble("Z", p.z);
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

    @Unique private void flightcomputer$syncIdentity() {
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
    @Unique private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }

    @Inject(method = "applyAction", at = @At("HEAD"))
    private void flightcomputer$emergencySound(FlightControllerAction action,
                                                CallbackInfoReturnable<?> cir) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (action != FlightControllerAction.EMERGENCY_SHUTDOWN || controller.getLevel() == null
                || controller.getLevel().isClientSide() || !controller.isEngaged()) return;

        long now = controller.getLevel().getGameTime();
        long previous = LAST_EMERGENCY_SOUND.getOrDefault(controller.getControllerId(), Long.MIN_VALUE);
        if (now - previous < EMERGENCY_SOUND_COOLDOWN) return;

        LAST_EMERGENCY_SOUND.put(controller.getControllerId(), now);
        controller.getLevel().playSound(null,
                controller.getBlockPos(), ModSounds.EMERGENCY_SHUTDOWN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /**
     * The UI does NOT have its own sound transport. The GUI already sends the same
     * FlightControllerAction that every other controller control uses. Once the server
     * successfully applies that action, emit the sound directly from the controller block,
     * using the exact same Level.playSound(null, blockPos, ..., SoundSource.BLOCKS, ...) call
     * used by Emergency Shutdown.
     */
    @Inject(method = "applyAction", at = @At("RETURN"))
    private void flightcomputer$uiButtonSound(FlightControllerAction action,
                                                CallbackInfoReturnable<FlightControllerActionResult> cir) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (action == null || cir.getReturnValue() == null || !cir.getReturnValue().accepted()
                || controller.getLevel() == null || controller.getLevel().isClientSide()) return;

        ModSounds sound = flightcomputer$buttonSound(action, controller);
        if (sound != null) {
            controller.getLevel().playSound(null, controller.getBlockPos(), sound.get(),
                    SoundSource.BLOCKS, 0.78F, 1.0F);
        }
    }

    @Unique
    private static ModSounds flightcomputer$buttonSound(FlightControllerAction action, FlightControllerBlockEntity controller) {
        if (action == FlightControllerAction.EMERGENCY_SHUTDOWN) return null;
        return switch (action) {
            case TOGGLE_ENGAGED -> controller.isEngaged() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_STABILISER -> controller.isStabiliser() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_AUTOPILOT -> controller.getControllerState().flightMode() == FlightMode.AUTOPILOT ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_ALTITUDE_HOLD -> controller.getControllerState().altitudeHold() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_HEADING_HOLD -> controller.getControllerState().headingHold() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_POSITION_HOLD -> controller.getControllerState().positionHold() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_VELOCITY_HOLD -> controller.getControllerState().velocityHold() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_NAVIGATION -> controller.getControllerState().navigationEnabled() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case TOGGLE_TERRAIN -> controller.isTerrainEnabled() ? ModSounds.UI_TOGGLE_ON.get() : ModSounds.UI_TOGGLE_OFF.get();
            case CYCLE_MODE -> ModSounds.UI_OPEN.get();
            case START_ROUTE, ABORT_ROUTE, PULSE_DISPLAY,
                 PUSH_FORWARD, PUSH_BACKWARD, PUSH_UP, PUSH_DOWN, PUSH_LEFT, PUSH_RIGHT -> ModSounds.UI_INTERACT.get();
            case EMERGENCY_SHUTDOWN -> null;
        };
    }

    @Inject(method = "onThermalStateChanged", at = @At("HEAD"))
    private void flightcomputer$thermalTransition(ThermalState previous, ThermalState current, CallbackInfo ci) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || previous == current) return;

        if (current == ThermalState.CRITICAL) {
            controller.getLevel().playSound(null, controller.getBlockPos(),
                    ModSounds.ENGINE_HEAT_CRITICAL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        if (current == ThermalState.THERMAL_SHUTDOWN && previous != ThermalState.THERMAL_SHUTDOWN) {
            controller.getLevel().playSound(null, controller.getBlockPos(),
                    ModSounds.WARNING_ENGINE_OVERHEAT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            FIRE_PENDING.put(controller.getControllerId(), FIRE_DELAY_TICKS);
        }
        if (previous == ThermalState.THERMAL_SHUTDOWN && current != ThermalState.THERMAL_SHUTDOWN) {
            FIRE_PENDING.remove(controller.getControllerId());
            controller.getLevel().playSound(null, controller.getBlockPos(),
                    ModSounds.FIRE_NEUTRALISED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void flightcomputer$fireTimer(CallbackInfo ci) {
        FlightControllerBlockEntity controller = (FlightControllerBlockEntity) (Object) this;
        if (controller.getLevel() == null || controller.getLevel().isClientSide()) return;
        UUID id = controller.getControllerId();
        Integer remaining = FIRE_PENDING.get(id);
        if (remaining == null) return;
        if (!controller.isThermalShutdown()) { FIRE_PENDING.remove(id); return; }
        if (remaining > 1) { FIRE_PENDING.put(id, remaining - 1); return; }
        FIRE_PENDING.remove(id);
        controller.getLevel().playSound(null, controller.getBlockPos(),
                ModSounds.FIRE_SYSTEMS_ACTIVE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}