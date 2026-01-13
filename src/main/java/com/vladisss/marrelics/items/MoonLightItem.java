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

public class MoonLightItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("moonlight_aegis")
                                .icon((player, stack, ability) -> ability)
                                .maxLevel(10)
                                .stat(StatData.builder("damage_resist")
                                        // 1 уровень: 5–15% (от редкости)
                                        .initialValue(0.05D, 0.15D)
                                        // Прирост: (40% - начальное) / 9 уровней
                                        // Для минимальной редкости: (0.40 - 0.05) / 9 ≈ 0.0389
                                        // Для максимальной: (0.40 - 0.15) / 9 ≈ 0.0278
                                        // Среднее значение ~0.0333 (3.33% за уровень)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.0333D)
                                        .formatValue(v -> MathUtils.round(v * 100.0D, 1))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 10, 100))
                .build();
    }

    /**
     * Итоговый процент снижения входящего урона.
     * Кап: 40% (0.40).
     */
    public static float getDamageResist(ItemStack stack) {
        if (!(stack.getItem() instanceof MoonLightItem item))
            return 0.0F;

        double value = item.getAbilityValue(stack, "moonlight_aegis", "damage_resist");
        return (float) Math.min(value, 0.40D);
    }
}
