package com.flightcomputer.client.render;

import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FlightControllerRenderer extends GeoBlockRenderer<FlightControllerBlockEntity> {

    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "flightcomputer", "textures/block/flight_controller_overlay.png");

    public FlightControllerRenderer() {
        super(new FlightControllerModel());
    }

    @Override
    public ResourceLocation getTextureLocation(FlightControllerBlockEntity animatable) {
        return OVERLAY_TEXTURE;
    }
}
