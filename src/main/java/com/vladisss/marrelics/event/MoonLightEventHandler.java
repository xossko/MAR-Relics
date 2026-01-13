package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.MoonLightItem;
import com.vladisss.marrelics.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

@Mod.EventBusSubscriber
public class MoonLightEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        if (player.level().isClientSide)
            return;

        MARRelicsMod.LOGGER.info("[MoonLight] Player {} took damage", player.getName().getString());

        // Проверка: игрок должен быть оборотнем через capabilities
        boolean isWolf = isWerewolf(player);
        MARRelicsMod.LOGGER.info("[MoonLight] Is werewolf: {}", isWolf);

        if (!isWolf)
            return;

        // Ищем MoonLight в Curios
        ItemStack amuletStack = findMoonLightAmulet(player);
        MARRelicsMod.LOGGER.info("[MoonLight] Found amulet: {}", !amuletStack.isEmpty());

        if (amuletStack.isEmpty())
            return;

        if (!(amuletStack.getItem() instanceof MoonLightItem))
            return;

        float resist = MoonLightItem.getDamageResist(amuletStack);
        MARRelicsMod.LOGGER.info("[MoonLight] Resist value: {}%", resist * 100);

        if (resist <= 0.0F)
            return;

        float originalDamage = event.getAmount();
        if (originalDamage <= 0.0F)
            return;

        // Скрытое сопротивление: уменьшение любого входящего урона
        float newDamage = originalDamage * (1.0F - resist);
        event.setAmount(newDamage);

        MARRelicsMod.LOGGER.info("[MoonLight] Reduced damage: {} -> {}", originalDamage, newDamage);
    }

    /**
     * Проверка: является ли игрок оборотнем (уровень > 0)
     */
    private static boolean isWerewolf(Player player) {
        try {
            // Получаем полный NBT игрока
            CompoundTag playerData = new CompoundTag();
            player.saveWithoutId(playerData);

            if (!playerData.contains("ForgeCaps")) {
                return false;
            }

            CompoundTag forgeCaps = playerData.getCompound("ForgeCaps");

            // Проверка 1: через vampirism:ifactionplayerhandler (уровень фракции)
            if (forgeCaps.contains("vampirism:ifactionplayerhandler")) {
                CompoundTag factionData = forgeCaps.getCompound("vampirism:ifactionplayerhandler");
                String faction = factionData.getString("faction");
                int factionLevel = factionData.getInt("level");

                MARRelicsMod.LOGGER.info("[MoonLight] Faction: '{}', Level: {}", faction, factionLevel);

                // Если фракция werewolves:werewolf И уровень > 0
                if ("werewolves:werewolf".equals(faction) && factionLevel > 0) {
                    return true;
                }
            }

        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("[MoonLight] Error checking werewolf status", e);
        }

        return false;
    }

    private static ItemStack findMoonLightAmulet(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(handler -> handler.findFirstCurio(stack ->
                        stack.getItem() == ModItems.MOONLIGHT.get()))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
