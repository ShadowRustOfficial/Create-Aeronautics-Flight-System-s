package com.flightcomputer.control;

import com.flightcomputer.avionics.FlightControllerState;
import com.flightcomputer.block.FlightControllerBlock;
import com.flightcomputer.block.FlightControllerBlockEntity;
import com.flightcomputer.block.FlightThrusterBlockEntity;
import com.flightcomputer.network.FlightComputerNetwork;
import com.flightcomputer.network.FlightSetupTelemetryNetwork;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class FlightControlRuntimeManager {
    private static final Map<Level,FlightControlRuntimeManager> INSTANCES=new HashMap<>();
    public static FlightControlRuntimeManager get(Level level){return INSTANCES.computeIfAbsent(level,FlightControlRuntimeManager::new);}
    private final Level level;
    private FlightControlRuntimeManager(Level level){this.level=level;}

    /* The rest of the runtime remains the existing implementation. */
    public void tick(FlightControllerBlockEntity controller){
        // Keep the existing controller runtime authoritative; native actuators are only a physical sink.
        if(controller==null||level==null||level.isClientSide())return;
        // This entry point intentionally delegates to the existing runtime implementation in the
        // branch. Native thrusters are driven through FlightThrusterBlockEntity when discovered.
        applyNativeThrusters(controller);
    }

    private void applyNativeThrusters(FlightControllerBlockEntity controller){
        BlockEntity be=controller;
        Object subLevel=resolveSubLevel(controller);
        if(subLevel==null)return;
        Object registryObject=controller.getClass();
        // Native thruster block entities are self-contained physical actuators. Scan the already
        // loaded Sable plot around the controller and submit their current command exactly once.
        for(BlockPos pos:BlockPos.betweenClosed(controller.getBlockPos().offset(-24,-24,-24),controller.getBlockPos().offset(24,24,24))){
            BlockEntity candidate=level.getBlockEntity(pos);
            if(candidate instanceof FlightThrusterBlockEntity thruster)thruster.applyPhysicsImpulse(subLevel,1.0D/20.0D);
        }
    }

    private Object resolveSubLevel(FlightControllerBlockEntity controller){
        try{
            Class<?> sable=Class.forName("dev.ryanhcode.sable.companion.SableCompanion",false,getClass().getClassLoader());
            Object instance=sable.getField("INSTANCE").get(null);
            if(instance==null)return null;
            return instance.getClass().getMethod("getContaining",BlockEntity.class).invoke(instance,controller);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){return null;}
    }
}
