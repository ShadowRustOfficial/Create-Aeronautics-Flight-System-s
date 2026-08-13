package com.flightcomputer.client.sound;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sable-aware local-space acoustic and Doppler context for Flight Computer sounds. */
public final class SableFlightSoundContext {
    private static final double MAX_OCCLUSION = 6.0D;
    private static final double SPEED_OF_SOUND = 343.0D;
    private SableFlightSoundContext() { }

    public static AcousticSample sample(ClientLevel level, Vec3 source, Vec3 listener) {
        if (level == null || source == null || listener == null) return AcousticSample.DEFAULT;
        try {
            ClientSubLevel sourceLevel = containing(level, source);
            ClientSubLevel listenerLevel = containing(level, listener);
            ClientSubLevel acousticLevel = sourceLevel != null ? sourceLevel : listenerLevel;
            if (acousticLevel == null) return AcousticSample.DEFAULT;
            Vec3 sourceLocal = acousticLevel.logicalPose().transformPositionInverse(source);
            Vec3 listenerLocal = acousticLevel.logicalPose().transformPositionInverse(listener);
            EmbeddedPlotLevelAccessor accessor = new EmbeddedPlotLevelAccessor(acousticLevel.getPlot());
            double occlusion = 0.0D;
            Vec3 start = sourceLocal;
            BlockPos last = null;
            for (int i = 0; i < 8; i++) {
                BlockHitResult hit = accessor.clip(new net.minecraft.world.level.ClipContext(start, listenerLocal,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                if (hit.getType() != HitResult.Type.BLOCK) break;
                BlockPos pos = hit.getBlockPos();
                if (pos.equals(last)) break;
                last = pos;
                BlockState state = accessor.getBlockState(pos);
                occlusion += state.isCollisionShapeFullBlock(accessor, pos) ? 0.9D : 0.35D;
                if (occlusion >= MAX_OCCLUSION) break;
                Vec3 delta = listenerLocal.subtract(hit.getLocation());
                if (delta.lengthSqr() < 1.0E-8D) break;
                start = hit.getLocation().add(delta.normalize().scale(0.01D));
            }
            return new AcousticSample(Math.min(MAX_OCCLUSION, occlusion), sourceLevel != null && listenerLevel != null
                    && sourceLevel.getUniqueId().equals(listenerLevel.getUniqueId()));
        } catch (Throwable ignored) {
            return AcousticSample.DEFAULT;
        }
    }

    public static float dopplerPitch(Vec3 source, Vec3 previousSource, Vec3 listener, Vec3 previousListener, double dtSeconds) {
        if (source == null || previousSource == null || listener == null || previousListener == null || dtSeconds <= 1.0E-4D) return 1.0F;
        Vec3 sourceVelocity = source.subtract(previousSource).scale(1.0D / dtSeconds);
        Vec3 listenerVelocity = listener.subtract(previousListener).scale(1.0D / dtSeconds);
        Vec3 line = listener.subtract(source);
        double length = line.length();
        if (length <= 1.0E-4D) return 1.0F;
        Vec3 direction = line.scale(1.0D / length);
        double sourceRadial = sourceVelocity.dot(direction);
        double listenerRadial = listenerVelocity.dot(direction);
        double denominator = SPEED_OF_SOUND - sourceRadial;
        if (Math.abs(denominator) < 1.0D) return 1.0F;
        double ratio = (SPEED_OF_SOUND - listenerRadial) / denominator;
        return Double.isFinite(ratio) ? (float)Math.max(0.82D, Math.min(1.18D, ratio)) : 1.0F;
    }

    private static ClientSubLevel containing(ClientLevel level, Vec3 world) {
        ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(level);
        if (container == null) return null;
        for (ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved() || !subLevel.isFinalized()) continue;
            try {
                var bounds = subLevel.boundingBox();
                if (world.x < bounds.minX() || world.x > bounds.maxX()
                        || world.y < bounds.minY() || world.y > bounds.maxY()
                        || world.z < bounds.minZ() || world.z > bounds.maxZ()) continue;
                Vec3 local = subLevel.logicalPose().transformPositionInverse(world);
                var localBounds = subLevel.getPlot().getBoundingBox();
                if (local.x >= localBounds.minX() && local.x <= localBounds.maxX() + 1D
                        && local.y >= localBounds.minY() && local.y <= localBounds.maxY() + 1D
                        && local.z >= localBounds.minZ() && local.z <= localBounds.maxZ() + 1D) return subLevel;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    public record AcousticSample(double occlusion, boolean sameSublevel) {
        public static final AcousticSample DEFAULT = new AcousticSample(0.0D, false);
    }
}
