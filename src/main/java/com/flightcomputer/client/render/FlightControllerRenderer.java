package com.flightcomputer.client.render;

import com.flightcomputer.block.FlightControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FlightControllerRenderer extends GeoBlockRenderer<FlightControllerBlockEntity> {

    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "flightcomputer", "textures/block/flight_controller_overlay.png");

    public FlightControllerRenderer() {
        super(new FlightControllerModel());
    }

    @Override
    public void render(@NotNull FlightControllerBlockEntity animatable, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(OVERLAY_TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.001D, 0.0D);
        this.getGeoModel().renderToBuffer(poseStack, consumer, packedLight, packedOverlay,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
