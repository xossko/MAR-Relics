package com.vladisss.marrelics.items;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.event.DemonicCrownEventHandler;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public class DemonicCrownItem extends RelicItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        // Первая способность - Scorched Earth
                        .ability(AbilityData.builder("scorched_earth")
                                .maxLevel(10)
                                .active(CastData.builder()
                                        .type(CastType.INSTANTANEOUS)
                                        .build())
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("damage")
                                        .initialValue(2.0D, 4.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.3D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("radius")
                                        .initialValue(3.0D, 6.0D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("duration")
                                        .initialValue(8.0D, 12.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.5D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("cooldown")
                                        .initialValue(100.0D, 70.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, -3.4D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .build())
                        // Вторая способность - Doom
                        .ability(AbilityData.builder("doom")
                                .requiredLevel(10)
                                .maxLevel(10)
                                .active(CastData.builder()
                                        .type(CastType.INSTANTANEOUS)
                                        .build())
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("duration")
                                        .initialValue(8.0D, 13.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.7D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("cooldown")
                                        .initialValue(1000.0D, 800.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, -25.0D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("percent_damage")
                                        .initialValue(0.5D, 1.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.11D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("flat_damage")
                                        .initialValue(2.0D, 4.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.22D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 15, 150))
                .build();
    }

    @Override
    public void castActiveAbility(ItemStack stack, Player player, String ability, CastType type, CastStage stage) {
        if (ability.equals("scorched_earth")) {
            handleScorchedEarth(stack, player, stage);
        } else if (ability.equals("doom")) {
            handleDoom(stack, player, stage);
        }
    }

    private void handleScorchedEarth(ItemStack stack, Player player, CastStage stage) {
        if (stage != CastStage.END) return;

        if (player.getPersistentData().getBoolean("demonic_crown_active")) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cScorched Earth is already active!"),
                    true
            );
            return;
        }

        int durationTicks = (int) (getScorchedDuration(stack) * 20);
        int cooldownTicks = (int) (getScorchedCooldown(stack) * 20);

        player.getPersistentData().putBoolean("demonic_crown_active", true);
        player.getPersistentData().putLong("demonic_crown_end_time",
                player.level().getGameTime() + durationTicks);

        addAbilityCooldown(stack, "scorched_earth", cooldownTicks);
    }

    private void handleDoom(ItemStack stack, Player player, CastStage stage) {
        if (stage != CastStage.END) return;
        if (player.level().isClientSide) return;

        MARRelicsMod.LOGGER.info("Doom ability activated by " + player.getName().getString());

        // ИЗМЕНЕНО: Радиус только 5 блоков
        EntityHitResult result = EntityUtils.rayTraceEntity(player,
                entity -> !entity.isSpectator() && entity instanceof LivingEntity && entity != player, 5);

        if (result == null || !(result.getEntity() instanceof LivingEntity target)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cNo target found within 5 blocks!"),
                    true
            );
            return;
        }

        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Точная дистанция
        double distance = player.distanceTo(target);
        if (distance > 5.0) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cTarget too far! Distance: " + String.format("%.1f", distance) + " blocks (max 5)"),
                    true
            );
            return;
        }

        int durationTicks = (int) (getDoomDuration(stack) * 20);
        int cooldownTicks = (int) (getDoomCooldown(stack) * 20);

        float totalDamage = calculateTotalDoomDamage(stack, target);
        float currentHealth = target.getHealth();
        boolean guaranteedDeath = totalDamage >= currentHealth;

        target.getPersistentData().putBoolean("doomed", true);
        target.getPersistentData().putLong("doom_end_time",
                player.level().getGameTime() + durationTicks);
        target.getPersistentData().putString("doom_source", player.getUUID().toString());
        target.getPersistentData().putFloat("doom_last_health", target.getHealth());
        target.getPersistentData().putBoolean("doom_guaranteed_death", guaranteedDeath);
        target.getPersistentData().putFloat("doom_total_damage", totalDamage);
        target.getPersistentData().putFloat("doom_damage_dealt", 0);

        target.addEffect(new MobEffectInstance(
                MobEffects.WITHER,
                durationTicks,
                0,
                false,
                true
        ));

        DemonicCrownEventHandler.addDoomedEntity(target.getUUID());
        addAbilityCooldown(stack, "doom", cooldownTicks);

        // Устрашающий звук
        player.level().playSound(
                null,
                target.blockPosition(),
                net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                net.minecraft.sounds.SoundSource.HOSTILE,
                2.0F,
                0.5F
        );

        player.level().playSound(
                null,
                target.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.HOSTILE,
                1.5F,
                0.7F
        );

        String deathStatus = guaranteedDeath ? "§c§lGUARANTEED DEATH" : "§6Survivable";
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§4Doom cast! §7[" + deathStatus + "§7] §8(" +
                                String.format("%.1f", totalDamage) + " vs " +
                                String.format("%.1f", currentHealth) + " HP) [" +
                                String.format("%.1f", distance) + "m]"
                ),
                false
        );

        if (target instanceof Player targetPlayer) {
            if (guaranteedDeath) {
                targetPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4§l☠ YOU ARE DOOMED TO DIE ☠"),
                        false
                );
            } else {
                targetPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4§lYOU ARE DOOMED!"),
                        true
                );
            }
        }

        MARRelicsMod.LOGGER.info("Doom applied at " + String.format("%.1f", distance) + " blocks: " + totalDamage + " total damage vs " + currentHealth + " HP (Death: " + guaranteedDeath + ")");
    }



    // Getters для Scorched Earth
    public static double getScorchedDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 2.0;
        return item.getAbilityValue(stack, "scorched_earth", "damage");
    }

    public static double getScorchedRadius(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 3.0;
        return item.getAbilityValue(stack, "scorched_earth", "radius");
    }

    public static double getScorchedDuration(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 8.0;
        return item.getAbilityValue(stack, "scorched_earth", "duration");
    }

    public static double getScorchedCooldown(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 50.0;
        return item.getAbilityValue(stack, "scorched_earth", "cooldown");
    }

    // Getters для Doom
    public static double getDoomDuration(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 6.0;
        return item.getAbilityValue(stack, "doom", "duration");
    }

    public static double getDoomCooldown(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 60.0;
        return item.getAbilityValue(stack, "doom", "cooldown");
    }

    public static double getDoomPercentDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 1.0;
        return item.getAbilityValue(stack, "doom", "percent_damage");
    }

    public static double getDoomFlatDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 2.0;
        return item.getAbilityValue(stack, "doom", "flat_damage");
    }

    public static boolean hasDoom(ItemStack stack) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return false;
        try {
            double cooldownValue = item.getAbilityValue(stack, "doom", "cooldown");
            return cooldownValue > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static float calculateTotalDoomDamage(ItemStack stack, LivingEntity target) {
        if (!(stack.getItem() instanceof DemonicCrownItem item)) return 0;

        double duration = getDoomDuration(stack);
        double percentDamage = getDoomPercentDamage(stack);
        double flatDamage = getDoomFlatDamage(stack);

        float percentHP = (float) (target.getMaxHealth() * (percentDamage / 100.0));
        float damagePerSecond = percentHP + (float) flatDamage;
        float totalDamage = damagePerSecond * (float) duration;

        return totalDamage;
    }
}
