package com.flightcomputer.control;

import java.util.List;
import java.util.Map;

/** Converts six-axis force/torque demands into linked thruster commands. */
public final class ThrustAllocator {
    private static final PropulsionType PREFERRED_TYPE = PropulsionType.CREATE_PROPULSION_SIMULATED;

    public void apply(ThrusterRegistry registry, FlightMode mode, Map<ControlAxis, Double> commands) {
        for (ControlAxis axis : ControlAxis.values()) {
            double command = commands.getOrDefault(axis, 0.0);
            applyAxis(registry.getLinks(mode, axis), command);
        }
    }

    private void applyAxis(List<ThrusterLink> links, double command) {
        if (links.isEmpty()) return;
        double preferredAuthority=authorityOf(links,PREFERRED_TYPE), totalAuthority=authorityOf(links,null);
        if(totalAuthority<=0)return;
        double remaining=command;
        double preferredUsed=clamp(remaining,-preferredAuthority,preferredAuthority);
        remaining-=preferredUsed;
        for(ThrusterLink link:links){
            if(link.source.getType()!=PREFERRED_TYPE)continue;
            double max=Math.max(0,link.source.getMaxThrust());
            double share=preferredAuthority>0?max/preferredAuthority:0;
            double fraction=max>0?(preferredUsed*share)/max:0;
            link.source.applyThrust(clamp(fraction*link.polarity,-1,1));
        }
        double otherAuthority=totalAuthority-preferredAuthority;
        for(ThrusterLink link:links){
            if(link.source.getType()==PREFERRED_TYPE)continue;
            double max=Math.max(0,link.source.getMaxThrust());
            double share=otherAuthority>0?max/otherAuthority:0;
            double sourceCommand=otherAuthority>0?clamp(remaining,-otherAuthority,otherAuthority)*share:0;
            double fraction=max>0?sourceCommand/max:0;
            link.source.applyThrust(clamp(fraction*link.polarity,-1,1));
        }
    }

    private double authorityOf(List<ThrusterLink> links,PropulsionType type){
        double total=0;for(ThrusterLink l:links)if(type==null||l.source.getType()==type)total+=Math.max(0,l.source.getMaxThrust());return total;
    }
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
