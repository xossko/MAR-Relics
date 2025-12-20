package com.vladisss.marrelics.event;

import com.vladisss.marrelics.items.HeartOfTarrasqueItem;
import it.hurts.sskirillss.relics.items.relics.base.IRelicItem;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "marrelics")
public class HeartOfTarrasqueEventHandler {

    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a8b91c4d-1234-5678-9abc-def012345678");
    private static final String ACCUMULATED_EXP_TAG = "HeartOfTarrasqueAccumulatedExp";
    private static final String LAST_HEALTH_BONUS_TAG = "HeartOfTarrasqueLastHealthBonus";

    // Прямое исцеление каждые 0.5 секунды + проверка изменения HP бонуса
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        Player player = event.player;

        if (player.level().isClientSide || event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (player.tickCount % 10 != 0) return; // Каждые 0.5 секунды

        ItemStack heartStack = findHeartOfTarrasque(player);
        if (heartStack.isEmpty()) return;

        // Проверяем изменился ли бонус здоровья (уровень способности)
        if (heartStack.getItem() instanceof HeartOfTarrasqueItem) {
            double currentHealthBonus = HeartOfTarrasqueItem.getHealthBonus(heartStack);
            double lastHealthBonus = player.getPersistentData().getDouble(LAST_HEALTH_BONUS_TAG);

            // Если бонус здоровья изменился - обновляем
            if (Math.abs(currentHealthBonus - lastHealthBonus) > 0.01) {
                player.getPersistentData().putDouble(LAST_HEALTH_BONUS_TAG, currentHealthBonus);

                var healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
                if (healthAttribute != null) {
                    healthAttribute.removeModifier(HEALTH_MODIFIER_UUID);
                    AttributeModifier modifier = new AttributeModifier(
                            HEALTH_MODIFIER_UUID,
                            "Heart of Tarrasque Health Bonus",
                            currentHealthBonus,
                            AttributeModifier.Operation.ADDITION
                    );
                    healthAttribute.addPermanentModifier(modifier);
                }
            }
        }

        double maxHealth = player.getMaxHealth();
        double currentHealth = player.getHealth();

        if (currentHealth >= maxHealth) return;

        // Запоминаем здоровье ДО исцеления
        float healthBefore = player.getHealth();

        double regenPercent = HeartOfTarrasqueItem.getRegenerationPercent(heartStack);
        float healAmount = (float) (maxHealth * regenPercent / 2.0);

        // Исцеляем
        player.heal(healAmount);

        // Считаем РЕАЛЬНО восстановленное здоровье
        float healthAfter = player.getHealth();
        float actualHealed = healthAfter - healthBefore;

        // Начисляем опыт за реально восстановленное HP
        if (actualHealed > 0 && heartStack.getItem() instanceof IRelicItem relic) {
            float accumulated = player.getPersistentData().getFloat(ACCUMULATED_EXP_TAG);
            accumulated += actualHealed;

            int expToAdd = (int) accumulated;
            accumulated -= expToAdd;

            player.getPersistentData().putFloat(ACCUMULATED_EXP_TAG, accumulated);

            if (expToAdd > 0) {
                relic.spreadExperience(player, heartStack, expToAdd);
            }
        }
    }

    // Усиление ВСЕЙ регенерации через Desperate Recovery
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack heartStack = findHeartOfTarrasque(player);
        if (heartStack.isEmpty()) return;

        double healthPercent = (player.getHealth() / player.getMaxHealth()) * 100.0;
        double multiplier = HeartOfTarrasqueItem.getTotalHealingMultiplier(heartStack, healthPercent);

        float originalAmount = event.getAmount();
        float newAmount = (float) (originalAmount * multiplier);
        event.setAmount(newAmount);
    }

    // Применение бонуса здоровья при экипировке
    @SubscribeEvent
    public static void onCurioEquip(top.theillusivec4.curios.api.event.CurioEquipEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getStack().getItem() instanceof HeartOfTarrasqueItem)) return;

        ItemStack stack = event.getStack();
        double healthBonus = HeartOfTarrasqueItem.getHealthBonus(stack);

        // Сохраняем текущий бонус
        player.getPersistentData().putDouble(LAST_HEALTH_BONUS_TAG, healthBonus);

        var healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            AttributeModifier modifier = new AttributeModifier(
                    HEALTH_MODIFIER_UUID,
                    "Heart of Tarrasque Health Bonus",
                    healthBonus,
                    AttributeModifier.Operation.ADDITION
            );

            healthAttribute.removeModifier(HEALTH_MODIFIER_UUID);
            healthAttribute.addPermanentModifier(modifier);
        }
    }

    // Удаление бонуса здоровья при снятии
    @SubscribeEvent
    public static void onCurioUnequip(top.theillusivec4.curios.api.event.CurioUnequipEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getStack().getItem() instanceof HeartOfTarrasqueItem)) return;

        var healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(HEALTH_MODIFIER_UUID);
        }

        // Сбрасываем сохраненный бонус
        player.getPersistentData().remove(LAST_HEALTH_BONUS_TAG);
    }

    private static ItemStack findHeartOfTarrasque(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(handler -> handler.findFirstCurio(stack -> stack.getItem() instanceof HeartOfTarrasqueItem))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
