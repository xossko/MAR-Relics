package com.vladisss.marrelics.items;

import com.vladisss.marrelics.duel.DuelArena;
import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.CastData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastStage;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastType;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public class WarHelmetItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("duelists_advantage")
                                .icon((player, stack, ability) -> ability)
                                .maxLevel(10)
                                .stat(StatData.builder("damage_reduction")
                                        .initialValue(0.05D, 0.10D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.015D)
                                        .formatValue(v -> MathUtils.round(v * 100, 1))
                                        .build())
                                .stat(StatData.builder("attack_speed_bonus")
                                        .initialValue(0.08D, 0.15D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.015D)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .stat(StatData.builder("detection_radius")
                                        .initialValue(5.0D, 5.0D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("grand_duel")
                                .requiredLevel(5)
                                .maxLevel(10)
                                .active(CastData.builder()
                                        .type(CastType.INSTANTANEOUS)
                                        .build())
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("cooldown")
                                        .initialValue(700.0D, 400.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, -20.0D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("duration")
                                        .initialValue(4.0D, 6.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.67D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("arena_radius")
                                        .initialValue(6.0D, 11.0D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("damage_reward")
                                        .initialValue(0.4D, 1.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.1D)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("immortal_duel")
                                .requiredLevel(15)
                                .maxLevel(1)
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("enabled")
                                        .initialValue(1.0D, 1.0D)  // Добавляем стат для проверки
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 15, 50))
                .build();
    }

    @Override
    public void castActiveAbility(ItemStack stack, Player player, String ability, CastType type, CastStage stage) {
        if (!ability.equals("grand_duel")) {
            return;
        }

        if (stage != CastStage.END) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        EntityHitResult result = EntityUtils.rayTraceEntity(player,
                (entity) -> !entity.isSpectator() && entity instanceof LivingEntity && entity != player,
                32);

        if (result == null || !(result.getEntity() instanceof LivingEntity target)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cНет цели в поле зрения!"),
                    true
            );
            return;
        }

        if (!DuelArena.canDuel(target)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cМожно вызвать на дуэль только боссов или игроков!"),
                    true
            );
            return;
        }

        player.getPersistentData().putInt("war_helmet_target_id", target.getId());
        player.getPersistentData().putBoolean("war_helmet_duel_pending", true);
        player.getPersistentData().putLong("war_helmet_duel_activation_time", player.level().getGameTime());

        int cooldownTicks = (int) (getCooldown(stack) * 20);
        addAbilityCooldown(stack, ability, cooldownTicks);

        String targetType = target instanceof Player ? "игрока" : "босса";
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§6⚔ Ударьте " + targetType + " в течение 5 секунд! ⚔"),
                true
        );
    }

    public static double getDamageReduction(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 0.15;
        return item.getAbilityValue(stack, "duelists_advantage", "damage_reduction");
    }

    public static double getAttackSpeedBonus(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 0.1;
        return item.getAbilityValue(stack, "duelists_advantage", "attack_speed_bonus");
    }

    public static double getDetectionRadius(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 5.0;
        return item.getAbilityValue(stack, "duelists_advantage", "detection_radius");
    }

    public static boolean hasGrandDuel(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return false;
        try {
            double cooldownValue = item.getAbilityValue(stack, "grand_duel", "cooldown");
            return cooldownValue > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static double getCooldown(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 500.0;
        return item.getAbilityValue(stack, "grand_duel", "cooldown");
    }

    public static double getDuration(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 4.0;
        return item.getAbilityValue(stack, "grand_duel", "duration");
    }

    public static double getArenaRadius(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 8.0;
        return item.getAbilityValue(stack, "grand_duel", "arena_radius");
    }

    public static double getDamageReward(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return 1.0;
        return item.getAbilityValue(stack, "grand_duel", "damage_reward");
    }

    // НОВЫЙ МЕТОД: Проверка бессмертия (15 уровень)
    // НОВЫЙ КОД (РАБОТАЕТ КАК В ВАШИХ ДРУГИХ РЕЛИКВИЯХ):
    public static boolean hasImmortalDuel(ItemStack stack) {
        if (!(stack.getItem() instanceof WarHelmetItem item)) return false;
        try {
            double value = item.getAbilityValue(stack, "immortal_duel", "enabled");
            return value > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
