package com.vladisss.marrelics.event;

import com.vladisss.marrelics.items.ShieldRingItem;
import com.vladisss.marrelics.registry.ModItems;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

@Mod.EventBusSubscriber
public class DevourEventHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Проверка на серверную сторону (важно!)
        if (event.getEntity().level().isClientSide())
            return;

        if (!(event.getSource().getEntity() instanceof Player player))
            return;

        ItemStack stack = EntityUtils.findEquippedCurio(player, ModItems.BLOOD_SHIELD_RING.get());
        if (stack.isEmpty() || !(stack.getItem() instanceof ShieldRingItem relic))
            return;

        if (relic.getLevel(stack) < 5)
            return;

        double mult = relic.getAbilityValue(stack, "devour", "lp_multiplier");
        if (mult <= 0D)
            return;

        float maxHealth = event.getEntity().getMaxHealth();
        int lpGain = (int) (maxHealth * mult);
        if (lpGain <= 0)
            return;

        // Добавляем LP через setCurrentEssence
        Binding binding = new Binding(player.getUUID(), player.getName().getString());
        var network = NetworkHelper.getSoulNetwork(binding);

        int currentLP = network.getCurrentEssence();
        network.setCurrentEssence(currentLP + lpGain);

        // Опыт реликвии
        int exp = Math.max(lpGain / 400, 1);
        relic.spreadExperience(player, stack, exp);


    }
}
