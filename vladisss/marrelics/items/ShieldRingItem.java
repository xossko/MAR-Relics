package com.vladisss.marrelics.items;

import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.item.ItemStack;

public class ShieldRingItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("blood_shield")
                                .icon((player, stack, ability) -> ability)
                                .maxLevel(10)
                                .stat(StatData.builder("absorption")
                                        .initialValue(0.1D, 0.25D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.0778D)
                                        .formatValue(v -> MathUtils.round(v * 100, 1))
                                        .build())
                                .stat(StatData.builder("lp_cost")
                                        .initialValue(400D, 300D)
                                        .upgradeModifier(UpgradeOperation.ADD, -16.67D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("devour")
                                .requiredLevel(5)
                                .maxLevel(5)
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("lp_multiplier")
                                        .initialValue(5D, 25D) // x5 → x25
                                        .upgradeModifier(UpgradeOperation.ADD, 5D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())

                        .build())
                .leveling(new LevelingData(100, 15, 50))
                .build();
    }


    public static float getAbsorptionPercent(ItemStack stack) {
        if (!(stack.getItem() instanceof ShieldRingItem item)) return 0.1f;

        double value = item.getAbilityValue(stack, "blood_shield", "absorption");

        return (float) Math.min(value, 0.95);
    }

    public static int getLPCost(ItemStack stack) {
        if (!(stack.getItem() instanceof ShieldRingItem item)) return 400;

        double value = item.getAbilityValue(stack, "blood_shield", "lp_cost");

        return (int) Math.max(value, 150);
    }
}
