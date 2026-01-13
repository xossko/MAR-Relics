package com.vladisss.marrelics.items;

import com.vladisss.marrelics.MARRelicsMod;
import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.nbt.CompoundTag;

public class BloodRapierItem extends RelicItem implements ICurioItem {

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder("blood_empowerment")
                                .maxLevel(10)
                                // Урон за каждые 10000 LP: с 0.2 на уровне 1 до 0.8 на уровне 10
                                .stat(StatData.builder("damage_per_10k_lp")
                                        .initialValue(0.05D, 0.25D) // Уровень 1: +0.2 урона
                                        .upgradeModifier(UpgradeOperation.ADD, 0.0667D) // +0.0667 за уровень (0.6/9 уровней)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 10, 200))
                .build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();

        if (slot == EquipmentSlot.MAINHAND) {
            // Базовый урон рапиры: 3.0
            multimap.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 3.0, AttributeModifier.Operation.ADDITION));
            // Скорость атаки рапиры: -2.0 (быстрее обычного меча)
            multimap.put(Attributes.ATTACK_SPEED,
                    new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.2, AttributeModifier.Operation.ADDITION));
        }

        return multimap;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return super.hurtEnemy(stack, target, attacker);
    }


    public static double getBonusDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof BloodRapierItem item)) {
            return 0.0;
        }

        try {
            CompoundTag tag = stack.getOrCreateTag();
            int currentLP = tag.getInt("CachedPlayerLP");

            // Расчет количества "стаков" по 10000 LP
            int lpStacks = currentLP / 10000;

            // Получаем урон за каждые 10000 LP (уже включает влияние редкости)
            double damagePerStack = item.getAbilityValue(stack, "blood_empowerment", "damage_per_10k_lp");

            // Итоговый урон = стаки LP * урон за стак
            double totalDamage = lpStacks * damagePerStack;

            return totalDamage;

        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("Error calculating Blood Rapier damage", e);
            return 0.0;
        }
    }


    // Curios API методы
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals("hands");
    }
}
