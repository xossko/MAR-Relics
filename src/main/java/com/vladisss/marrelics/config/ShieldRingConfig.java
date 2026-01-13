package com.vladisss.marrelics.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ShieldRingConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue LP_PER_DAMAGE;

    static {
        BUILDER.push("Blood Shield Ring Config");

        LP_PER_DAMAGE = BUILDER
                .comment("Сколько LP тратится на 1 поглощённый урон")
                .defineInRange("lpPerDamage", 400, 1, 10000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
