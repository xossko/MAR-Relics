package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.ShieldRingItem;
import com.vladisss.marrelics.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber
public class ShieldRingEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void recordOriginalDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        player.getPersistentData().putFloat("OriginalDamage", event.getAmount());
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        AtomicReference<ItemStack> ringStack = new AtomicReference<>(ItemStack.EMPTY);

        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.BLOOD_SHIELD_RING.get()).ifPresent(slotResult -> {
                ringStack.set(slotResult.stack());
            });
        });

        if (ringStack.get().isEmpty()) return;
        if (!(ringStack.get().getItem() instanceof ShieldRingItem relic)) return;

        float originalDamage = player.getPersistentData().getFloat("OriginalDamage");
        float damageAfterArmor = event.getAmount();

        if (damageAfterArmor <= 0) return;

        float armorReduction = originalDamage - damageAfterArmor;
        float halfArmorReduction = armorReduction * 0.5f;
        float damageForShield = originalDamage - halfArmorReduction;

        float absorptionPercent = ShieldRingItem.getAbsorptionPercent(ringStack.get());
        int lpCost = ShieldRingItem.getLPCost(ringStack.get());

        float damageToAbsorb = damageForShield * absorptionPercent;
        int lpNeeded = (int) (damageToAbsorb * lpCost);

        try {
            Binding binding = new Binding(player.getUUID(), player.getName().getString());
            var network = NetworkHelper.getSoulNetwork(binding);
            int currentLP = network.getCurrentEssence();

            if (currentLP >= lpNeeded && lpNeeded > 0) {
                network.setCurrentEssence(currentLP - lpNeeded);

                float newDamage = Math.max(0, damageAfterArmor - damageToAbsorb);
                event.setAmount(newDamage);

                int experience = Math.max((int) (damageToAbsorb / 10), 1);
                relic.spreadExperience(player, ringStack.get(), experience);

            } else if (currentLP > 0) {
                float partialAbsorb = (float) currentLP / lpCost;
                partialAbsorb = Math.min(partialAbsorb, damageAfterArmor);

                int lpToUse = (int) (partialAbsorb * lpCost);
                network.setCurrentEssence(currentLP - lpToUse);

                float newDamage = Math.max(0, damageAfterArmor - partialAbsorb);
                event.setAmount(newDamage);

                int experience = Math.max((int) (partialAbsorb / 10), 1);
                relic.spreadExperience(player, ringStack.get(), experience);
            }
        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("Error with Blood Magic API: ", e);
        }
    }
}
