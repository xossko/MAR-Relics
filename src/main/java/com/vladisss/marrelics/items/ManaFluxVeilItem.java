package com.vladisss.marrelics.items;

import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.CastData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastType;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastStage;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ManaFluxVeilItem extends RelicItem {

    public static final String ABILITY_MANA_SHIELD = "manashield";
    public static final String STAT_DMG_PER_MANA = "dmg_per_mana";
    public static final String STAT_HP_PENALTY = "hpp";


    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder(ABILITY_MANA_SHIELD)
                                .icon((player, stack, ability) -> ability)
                                .maxLevel(10)
                                .stat(StatData.builder(STAT_DMG_PER_MANA)
                                        .initialValue(0.5D, 0.8D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.22D)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .stat(StatData.builder(STAT_HP_PENALTY)
                                        .initialValue(0.70D, 0.90D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.01D)
                                        .formatValue(v -> MathUtils.round(v * 100D, 0))
                                        .build())
                                .active(CastData.builder()
                                        .type(CastType.INSTANTANEOUS)
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 20, 50))
                .build();
    }

    @Override
    public void castActiveAbility(ItemStack stack, Player player, String ability, CastType type, CastStage stage) {
        if (!ABILITY_MANA_SHIELD.equals(ability)) return;
        if (player.level().isClientSide) return;

        boolean enabled = player.getPersistentData().getBoolean("mana_flux_shield_enabled");
        enabled = !enabled;
        player.getPersistentData().putBoolean("mana_flux_shield_enabled", enabled);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.vladisss.marrelics.network.ModNetwork.sendManaShieldState(sp, enabled);
        }


        // ЗВУК + ПАРТИКЛЫ
        if (player.level() instanceof ServerLevel serverLevel) {
            if (enabled) {
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.0F, 1.5F);
                spawnActivationParticles(serverLevel, player, true);
            } else {
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.PLAYERS, 0.8F, 1.2F);
                spawnActivationParticles(serverLevel, player, false);
            }
        }

        String status = enabled ? "§bактивирован" : "§7отключен";
        player.displayClientMessage(Component.literal("§eМана-щит " + status), true);
    }

    private static void spawnActivationParticles(ServerLevel level, Player player, boolean active) {
        int count = active ? 50 : 35;
        for (int i = 0; i < count; i++) {
            // Взрыв по сфере
            double theta = Math.PI * 2 * i / count;
            double phi = Math.acos(2 * level.getRandom().nextDouble() - 1);
            double radius = 2.0 + level.getRandom().nextDouble() * 0.5;

            double x = player.getX() + radius * Math.sin(phi) * Math.cos(theta);
            double y = player.getY() + 1.0 + (level.getRandom().nextDouble() - 0.5) * 0.5;
            double z = player.getZ() + radius * Math.sin(phi) * Math.sin(theta);

            if (active) {
                // ON: яркий синий взрыв
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.1, 0.1, 0.1, 0.05);
                level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 4, 0.08, 0.08, 0.08, 0.1);
            } else {
                // OFF: фиолетовое затухание
                level.sendParticles(ParticleTypes.SONIC_BOOM, x, y, z, 2, 0.15, 0.15, 0.15, 0.08);
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.1, 0.1, 0.1, 0.03);
            }
        }
    }


    




    public static double getDamagePerMana(ItemStack stack) {
        if (!(stack.getItem() instanceof ManaFluxVeilItem item)) return 0.5D;
        return item.getAbilityValue(stack, ABILITY_MANA_SHIELD, STAT_DMG_PER_MANA);
    }

    public static double getHpPenalty(ItemStack stack) {
        if (!(stack.getItem() instanceof ManaFluxVeilItem item)) return 0.70D;
        return item.getAbilityValue(stack, ABILITY_MANA_SHIELD, STAT_HP_PENALTY);
    }
}
