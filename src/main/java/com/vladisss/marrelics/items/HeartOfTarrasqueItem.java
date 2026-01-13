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

public class HeartOfTarrasqueItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("vitality_surge")
                                .maxLevel(20)
                                .stat(StatData.builder("health_bonus")
                                        .initialValue(6.0D, 12.0D) // Всегда 10 HP базово
                                        .upgradeModifier(UpgradeOperation.ADD, 3.5D) // +2.63 за каждый уровень
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("regeneration")
                                        .initialValue(0.3D, 0.6D) // Всегда 0.5% базово
                                        .upgradeModifier(UpgradeOperation.ADD, 0.3D) // +0.3% за уровень
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("desperate_recovery")
                                .requiredLevel(5)
                                .maxLevel(10)
                                .stat(StatData.builder("heal_boost")
                                        .initialValue(0.5D, 1.0D) // 0.5% → 1.5% в зависимости от редкости
                                        .upgradeModifier(UpgradeOperation.ADD, 0.8D) // +1% за уровень
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 25, 200))
                .build();
    }

    public static double getHealthBonus(ItemStack stack) {
        if (!(stack.getItem() instanceof HeartOfTarrasqueItem item)) return 10.0;

        return item.getAbilityValue(stack, "vitality_surge", "health_bonus");
    }

    public static double getRegenerationPercent(ItemStack stack) {
        if (!(stack.getItem() instanceof HeartOfTarrasqueItem item)) return 0.005;

        double value = item.getAbilityValue(stack, "vitality_surge", "regeneration");

        return value / 100.0;
    }

    public static double getTotalHealingMultiplier(ItemStack stack, double currentHealthPercent) {
        if (!(stack.getItem() instanceof HeartOfTarrasqueItem item)) return 1.0;

        double baseBoost = item.getAbilityValue(stack, "desperate_recovery", "heal_boost");
        if (baseBoost <= 0) return 1.0;

        // Сколько "тиеров" недостающего HP (каждые 10%)
        int missingTiers = (int) ((100.0 - currentHealthPercent) / 10.0);

        // Итоговый бонус в процентах: baseBoost * количество тиров
        double totalPercent = baseBoost * missingTiers;

        // Переводим в мультипликатор
        return 1.0 + (totalPercent / 100.0);
    }
}
