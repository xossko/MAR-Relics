package com.vladisss.marrelics.items;

import com.vladisss.marrelics.MARRelicsMod;
import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.CastData;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastStage;
import it.hurts.sskirillss.relics.items.relics.base.data.cast.misc.CastType;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;

public class SaltedShiv extends RelicItem implements ICurioItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("stat_steal")
                                .maxLevel(15)
                                .stat(StatData.builder("duration")
                                        .initialValue(7.0D, 13.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 2.22D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("speed_steal")
                                .requiredLevel(5)
                                .maxLevel(5)
                                .stat(StatData.builder("attack_speed")
                                        .initialValue(0.0D, 0.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.01D)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .build())
                        .ability(AbilityData.builder("shadow_dance")
                                .requiredLevel(10)
                                .maxLevel(10)
                                .active(CastData.builder()
                                        .type(CastType.INSTANTANEOUS)
                                        .build())
                                .icon((player, stack, ability) -> ability)
                                .stat(StatData.builder("duration")
                                        .initialValue(4.0D, 8.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.4D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .stat(StatData.builder("cooldown")
                                        .initialValue(60.0D, 40.0D)
                                        .upgradeModifier(UpgradeOperation.ADD, -2.0D)
                                        .formatValue(v -> MathUtils.round(v, 0))
                                        .build())
                                .stat(StatData.builder("speed_bonus")
                                        .initialValue(0.3D, 0.5D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.02D)
                                        .formatValue(v -> MathUtils.round(v * 100, 0))
                                        .build())
                                .stat(StatData.builder("regeneration")
                                        .initialValue(0.5D, 1.5D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.1D)
                                        .formatValue(v -> MathUtils.round(v, 1))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 15, 200))
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // Проверка уровня реликвии
            int relicLevel = getRelicLevel(stack);
            if (relicLevel < 10) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cShadow Dance требует 10 уровень! Текущий: " + relicLevel),
                            true
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            // Проверка что способность разблокирована
            if (!canUseShadowDance(stack)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cShadow Dance недоступен!"),
                            true
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            // Проверка кулдауна
            if (player.getCooldowns().isOnCooldown(this)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cСпособность на перезарядке!"),
                            true
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide()) {
                castActiveAbility(stack, player, "shadow_dance", CastType.INSTANTANEOUS, CastStage.START);
            }

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }




    @Override
    public void castActiveAbility(ItemStack stack, Player player, String ability, CastType type, CastStage stage) {
        if (!ability.equals("shadow_dance")) return;
        if (stage != CastStage.START) return;
        if (player.level().isClientSide()) return;

        int durationTicks = (int)(getShadowDanceDuration(stack) * 20);
        int cooldownTicks = (int)(getShadowDanceCooldown(stack) * 20);

        player.getPersistentData().putBoolean("shadowdance_active", true);
        player.getPersistentData().putLong("shadowdance_endtime", player.level().getGameTime() + durationTicks);

        player.getCooldowns().addCooldown(this, cooldownTicks);

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§8Shadow Dance активирован!"),
                true
        );

        MARRelicsMod.LOGGER.info("Shadow Dance activated for {} seconds", getShadowDanceDuration(stack));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();

        if (slot == EquipmentSlot.MAINHAND) {
            multimap.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                            0.0,
                            AttributeModifier.Operation.ADDITION));

            multimap.put(Attributes.ATTACK_SPEED,
                    new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                            -2.4,
                            AttributeModifier.Operation.ADDITION));
        }

        return multimap;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return super.hurtEnemy(stack, target, attacker);
    }
    // Получить текущий уровень реликвии
    public static int getRelicLevel(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 0;
        return item.getLevel(stack);
    }

    // Проверка что способность Shadow Dance доступна (уровень 10+)
    public static boolean canUseShadowDance(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return false;

        // Проверяем уровень реликвии
        int level = item.getLevel(stack);
        if (level < 10) {
            return false;
        }

        // Проверяем что способность существует
        try {
            double cooldownValue = item.getAbilityValue(stack, "shadow_dance", "cooldown");
            return cooldownValue > 0;
        } catch (Exception e) {
            return false;
        }
    }


    public static int getStealDuration(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 200;
        double durationSeconds = item.getAbilityValue(stack, "stat_steal", "duration");
        return (int) (durationSeconds * 20);
    }

    public static double getAttackSpeedStealAmount(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 0.0;
        double value = item.getAbilityValue(stack, "speed_steal", "attack_speed");
        return value;
    }

    public static boolean hasSpeedSteal(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return false;
        double value = item.getAbilityValue(stack, "speed_steal", "attack_speed");
        MARRelicsMod.LOGGER.info("Speed steal ability value: {}", value);
        return value > 0;
    }

    public static double getShadowDanceDuration(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 4.0;
        return item.getAbilityValue(stack, "shadow_dance", "duration");
    }

    public static double getShadowDanceCooldown(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 60.0;
        return item.getAbilityValue(stack, "shadow_dance", "cooldown");
    }

    public static double getShadowDanceSpeed(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 0.3;
        return item.getAbilityValue(stack, "shadow_dance", "speed_bonus");
    }

    public static double getShadowDanceRegen(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return 0.5;
        return item.getAbilityValue(stack, "shadow_dance", "regeneration");
    }

    public static boolean hasShadowDance(ItemStack stack) {
        if (!(stack.getItem() instanceof SaltedShiv item)) return false;
        try {
            double cooldownValue = item.getAbilityValue(stack, "shadow_dance", "cooldown");
            return cooldownValue > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals("hands");
    }
}
