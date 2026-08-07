package com.flightcomputer.client.render;

import com.flightcomputer.FlightComputer;
import com.flightcomputer.block.FlightControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FlightControllerModel extends GeoModel<FlightControllerBlockEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            FlightComputer.MOD_ID, "geo/flight_controller.geo.json");
    private static final ResourceLocation BODY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FlightComputer.MOD_ID, "textures/block/flight_controller.png");
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FlightComputer.MOD_ID, "textures/block/flight_controller_overlay.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            FlightComputer.MOD_ID, "animations/flight_controller.animation.json");

    @Override
    public ResourceLocation getModelResource(FlightControllerBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FlightControllerBlockEntity animatable) {
        return BODY_TEXTURE;
    }

    public ResourceLocation getOverlayTextureResource(FlightControllerBlockEntity animatable) {
        return OVERLAY_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FlightControllerBlockEntity animatable) {
        return ANIMATIONS;
    }
}