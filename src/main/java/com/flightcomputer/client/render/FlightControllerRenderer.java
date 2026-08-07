package com.flightcomputer.client.render;

import com.flightcomputer.block.FlightControllerBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FlightControllerRenderer extends GeoBlockRenderer<FlightControllerBlockEntity> {

    public FlightControllerRenderer() {
        super(new FlightControllerModel());
    }
}
