package com.flightcomputer.item;

import net.minecraft.world.item.Item;

/** Marker item for Flight Controller cooling upgrade tiers. */
public class CoolingUpgradeItem extends Item {
    public enum Tier { NONE, BASIC, IMPROVED, ADVANCED }

    private final Tier tier;

    public CoolingUpgradeItem(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }
}
