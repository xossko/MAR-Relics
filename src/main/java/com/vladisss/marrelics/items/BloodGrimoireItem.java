package com.vladisss.marrelics.items;

import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.CastData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastStage;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastType;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.*;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BloodGrimoireItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        // Пассивная способность: Blood Magic
                        .ability(AbilityData.builder("bloodmagic")
                                .icon((player, stack, ability) -> ability)
                                .maxLevel(10)
                                .stat(StatData.builder("blooddamage")
                                        .initialValue(0.10D, 0.20D) // +15% -> +30%
                                        .upgradeModifier(UpgradeOperation.ADD, 0.015D)
                                        .formatValue(v -> MathUtils.round(v * 100, 1)) // Убрал %
                                        .build())
                                .stat(StatData.builder("lpconversion")
                                        .initialValue(500D, 300D) // 300 LP -> 200 LP за 1 ману
                                        .upgradeModifier(UpgradeOperation.ADD, -10D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())

                        // Активная способность: Crimson Convergence
                        .ability(AbilityData.builder("crimsonconvergence")
                                .requiredLevel(5)
                                .maxLevel(10)
                                .active(CastData.builder()
                                        .type(CastType.INSTANTANEOUS)
                                        .build())
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("cooldown")
                                        .initialValue(180.0D, 120.0D) // 180s -> 120s
                                        .upgradeModifier(UpgradeOperation.ADD, -6D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("radius")
                                        .initialValue(8.0D, 12.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.4D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("duration")
                                        .initialValue(10.0D, 15.0D) // секунды
                                        .upgradeModifier(UpgradeOperation.ADD, 0.5D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("damage")
                                        .initialValue(0.02D, 0.03D) // 2% -> 3% макс хп/сек
                                        .upgradeModifier(UpgradeOperation.ADD, 0.001D)
                                        .formatValue(v -> MathUtils.round(v * 100, 1))
                                        .build())
                                .stat(StatData.builder("lpcost")
                                        .initialValue(5000D, 3000D)
                                        .upgradeModifier(UpgradeOperation.ADD, -200D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 15, 50))
                .build();
    }

    @Override
    public void castActiveAbility(ItemStack stack, Player player, String ability, CastType type, CastStage stage) {
        if (!ability.equals("crimsonconvergence")) return;
        if (stage != CastStage.END) return;
        if (player.level().isClientSide()) return;

        // Активируем эффект Crimson Convergence
        player.getPersistentData().putBoolean("grimoireactive", true);
        player.getPersistentData().putLong("grimoireendtime",
                player.level().getGameTime() + (long)(getDuration(stack) * 20));
        player.getPersistentData().remove("grimoirelpspent"); // Сброс флага

        int cooldownTicks = (int)(getCooldown(stack) * 20);
        addAbilityCooldown(stack, ability, cooldownTicks);
    }

    // Геттеры для статов
    public static double getBloodDamageBonus(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 0.15;
        return item.getAbilityValue(stack, "bloodmagic", "blooddamage");
    }

    public static int getLPConversion(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 300;
        return (int) item.getAbilityValue(stack, "bloodmagic", "lpconversion");
    }

    public static double getCooldown(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 180.0;
        return item.getAbilityValue(stack, "crimsonconvergence", "cooldown");
    }

    public static double getRadius(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 8.0;
        return item.getAbilityValue(stack, "crimsonconvergence", "radius");
    }

    public static double getDuration(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 10.0;
        return item.getAbilityValue(stack, "crimsonconvergence", "duration");
    }

    public static double getDamagePercent(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 0.02;
        return item.getAbilityValue(stack, "crimsonconvergence", "damage");
    }

    public static int getLPCost(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodGrimoireItem item)) return 5000;
        return (int) item.getAbilityValue(stack, "crimsonconvergence", "lpcost");
    }
}
