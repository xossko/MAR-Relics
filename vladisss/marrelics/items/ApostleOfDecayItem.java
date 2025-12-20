package com.vladisss.marrelics.items;

import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.item.ItemStack;

public class ApostleOfDecayItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("decay")
                                .icon((player, stack, ability) -> ability)
                                .maxLevel(15)
                                .stat(StatData.builder("damage")
                                        .initialValue(0.1D, 1.0D) // 0.1% → 1%
                                        .upgradeModifier(UpgradeOperation.ADD, 0.1D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("radius")
                                        .initialValue(5.0D, 10.0D) // 5 → 10 блоков
                                        .upgradeModifier(UpgradeOperation.ADD, 0.556D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("rot")
                                .requiredLevel(5)
                                .maxLevel(5)
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("healing_reduction")
                                        .initialValue(6.0D, 30.0D) // 6% → 30% базовый
                                        .upgradeModifier(UpgradeOperation.ADD, 6.0D) // (30-6)/(5-1) = 6.0
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 15, 200))
                .build();
    }

    public static double getDamagePercent(ItemStack stack) {
        if (!(stack.getItem() instanceof ApostleOfDecayItem item)) return 0.001;

        double value = item.getAbilityValue(stack, "decay", "damage");

        return value / 100.0;
    }

    public static double getRadius(ItemStack stack) {
        if (!(stack.getItem() instanceof ApostleOfDecayItem item)) return 5.0;

        double value = item.getAbilityValue(stack, "decay", "radius");

        return Math.min(value, 15.0);
    }

    public static double getHealingReduction(ItemStack stack) {
        if (!(stack.getItem() instanceof ApostleOfDecayItem item)) return 0.0;

        double value = item.getAbilityValue(stack, "rot", "healing_reduction");

        // Жесткий лимит 40% (30% базовый + ~10% от редкости)
        return Math.min(value / 100.0, 0.40);
    }

}
