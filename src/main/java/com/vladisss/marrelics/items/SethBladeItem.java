package com.vladisss.marrelics.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.hurts.sskirillss.relics.items.relics.base.RelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.RelicData;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.*;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.misc.UpgradeOperation;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class SethBladeItem extends RelicItem {

    public static final String ABILITY_SETHS_COMMAND = "seths_command";

    @Override
    public RelicData constructDefaultRelicData() {
        return RelicData.builder()
                .abilities(AbilitiesData.builder()
                        .ability(AbilityData.builder(ABILITY_SETHS_COMMAND)
                                .requiredLevel(1)
                                .maxLevel(10)
                                .stat(StatData.builder("damage_per_kill")
                                        .initialValue(0.10D, 0.25D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.015D)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .stat(StatData.builder("health_per_kill")
                                        .initialValue(0.05D, 0.10D)
                                        .upgradeModifier(UpgradeOperation.ADD, 0.05D)
                                        .formatValue(v -> MathUtils.round(v, 2))
                                        .build())
                                .build())
                        .build())
                .leveling(new LevelingData(100, 15, 200))
                .build();
    }
    @Override
    public int getLevel(ItemStack stack) {
        boolean unlocked = stack.getOrCreateTag().getBoolean("seth_unlocked");
        if (!unlocked) return 0;                 // пока запечатан — всегда 0 (замок виден)
        return Math.max(1, super.getLevel(stack)); // пробудился — минимум 1 (замок снимается)
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        var tag = stack.getOrCreateTag();
        boolean unlocked = tag.getBoolean("seth_unlocked");
        int sacrifices = tag.getInt("seth_sacrifice_index");

        if (!unlocked) {
            tooltip.add(Component.literal("§cПриношения: " + sacrifices + "/5"));
        } else {
            int kills = tag.getInt("seth_total_kills");
            tooltip.add(Component.literal("§cВсего жертв: " + kills));
        }
    }



    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();

        if (slot == EquipmentSlot.MAINHAND) {
            map.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                            7.0D, AttributeModifier.Operation.ADDITION));
            map.put(Attributes.ATTACK_SPEED,
                    new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                            -2.4D, AttributeModifier.Operation.ADDITION));
        }

        return map;
    }


    public static double getDamagePerKill(ItemStack stack) {
        if (!(stack.getItem() instanceof SethBladeItem item)) return 0.1D;
        return item.getAbilityValue(stack, ABILITY_SETHS_COMMAND, "damage_per_kill");
    }

    public static double getHealthPerKill(ItemStack stack) {
        if (!(stack.getItem() instanceof SethBladeItem item)) return 0.5D;
        return item.getAbilityValue(stack, ABILITY_SETHS_COMMAND, "health_per_kill");
    }
}
