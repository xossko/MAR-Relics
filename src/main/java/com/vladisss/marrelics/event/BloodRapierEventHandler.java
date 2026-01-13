package com.vladisss.marrelics.event;

import com.vladisss.marrelics.items.BloodRapierItem;
import com.vladisss.marrelics.registry.ModItems;
import com.vladisss.marrelics.MARRelicsMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber
public class BloodRapierEventHandler {

    private static Method getSoulNetworkMethod = null;
    private static Method getCurrentEssenceMethod = null;
    private static boolean bloodMagicLoaded = false;

    static {
        // Проверяем, загружен ли Blood Magic
        bloodMagicLoaded = ModList.get().isLoaded("bloodmagic");

        if (bloodMagicLoaded) {
            try {
                // Пытаемся получить методы через рефлексию
                Class<?> networkHelper = Class.forName("wayoftime.bloodmagic.util.helper.NetworkHelper");
                getSoulNetworkMethod = networkHelper.getMethod("getSoulNetwork", Player.class);

                Class<?> soulNetwork = Class.forName("wayoftime.bloodmagic.core.data.SoulNetwork");
                getCurrentEssenceMethod = soulNetwork.getMethod("getCurrentEssence");

                MARRelicsMod.LOGGER.info("Blood Magic API успешно подключен!");
            } catch (Exception e) {
                MARRelicsMod.LOGGER.error("Не удалось подключить Blood Magic API: ", e);
                bloodMagicLoaded = false;
            }
        }
    }

    /**
     * Обновление кэша LP каждые 20 тиков (1 секунда)
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (event.player.tickCount % 20 != 0) return; // Каждую секунду

        Player player = event.player;

        // Ищем Blood Rapier у игрока
        ItemStack weapon = ItemStack.EMPTY;

        if (player.getMainHandItem().getItem() == ModItems.BLOOD_RAPIER.get()) {
            weapon = player.getMainHandItem();
        } else if (player.getOffhandItem().getItem() == ModItems.BLOOD_RAPIER.get()) {
            weapon = player.getOffhandItem();
        } else {
            // Проверяем слот Curios
            weapon = CuriosApi.getCuriosInventory(player)
                    .resolve()
                    .flatMap(handler -> handler.findFirstCurio(ModItems.BLOOD_RAPIER.get()))
                    .map(slotResult -> slotResult.stack())
                    .orElse(ItemStack.EMPTY);
        }

        if (!weapon.isEmpty()) {
            updateLPCache(weapon, player);
        }
    }

    /**
     * Обновляет кэш LP в NBT предмета
     */
    private static void updateLPCache(ItemStack stack, Player player) {
        if (!bloodMagicLoaded) {
            return;
        }

        try {
            // Получаем сеть через рефлексию
            Object network = getSoulNetworkMethod.invoke(null, player);
            int currentLP = (int) getCurrentEssenceMethod.invoke(network);

            // Сохраняем в NBT
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt("CachedPlayerLP", currentLP);

        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("Ошибка при обновлении LP кэша: ", e);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Проверяем, что атакующий - игрок
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }

        if (attacker.level().isClientSide) {
            return;
        }

        // Ищем Blood Rapier в руке или в слоте Curios
        ItemStack weapon = ItemStack.EMPTY;

        if (attacker.getMainHandItem().getItem() == ModItems.BLOOD_RAPIER.get()) {
            weapon = attacker.getMainHandItem();
        } else {
            // Проверяем слот Curios "hands"
            weapon = CuriosApi.getCuriosInventory(attacker)
                    .resolve()
                    .flatMap(handler -> handler.findFirstCurio(ModItems.BLOOD_RAPIER.get()))
                    .map(slotResult -> slotResult.stack())
                    .orElse(ItemStack.EMPTY);
        }

        if (weapon.isEmpty()) {
            return;
        }

        // Обновляем кэш перед ударом
        updateLPCache(weapon, attacker);

        // Получаем бонусный урон от LP
        double bonusDamage = BloodRapierItem.getBonusDamage(weapon);

        // Запоминаем изначальный урон для расчета опыта
        float originalDamage = event.getAmount();

        if (bonusDamage > 0) {
            // Увеличиваем урон
            float newDamage = originalDamage + (float) bonusDamage;
            event.setAmount(newDamage);

            MARRelicsMod.LOGGER.debug("Blood Rapier: {} -> {} (+{})",
                    originalDamage, newDamage, bonusDamage);
        }

        // Даем опыт = итоговый нанесенный урон (округленный до целого)
        float finalDamage = event.getAmount();
        int expToGive = (int) Math.ceil(finalDamage); // Округляем вверх

        if (expToGive > 0 && weapon.getItem() instanceof BloodRapierItem relic) {
            relic.spreadExperience(attacker, weapon, expToGive);

            MARRelicsMod.LOGGER.debug("Blood Rapier получила {} опыта за {} урона",
                    expToGive, finalDamage);
        }
    }
}
