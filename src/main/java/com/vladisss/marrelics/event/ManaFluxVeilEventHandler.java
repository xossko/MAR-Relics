package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.ManaFluxVeilItem;
import com.vladisss.marrelics.registry.ModItems;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = MARRelicsMod.MODID)
public class ManaFluxVeilEventHandler {

    private static final UUID HP_PENALTY_UUID = UUID.fromString("b1f1ad1e-4e0c-4c8a-9b01-6e8d8b5f1234");

    // 1. ЩИТ - блокирует ВЕСЬ урон через ману
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void ultimateShield(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!player.getPersistentData().getBoolean("mana_flux_shield_enabled")) return;

        AtomicReference<ItemStack> veilStackRef = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.findFirstCurio(ModItems.MANA_FLUX_VEIL.get()).ifPresent(slotResult ->
                        veilStackRef.set(slotResult.stack()))
        );
        ItemStack veilStack = veilStackRef.get();
        if (veilStack.isEmpty()) return;

        float incomingDamage = event.getAmount();
        if (incomingDamage <= 0.0F) return;

        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData == null) return;

            float currentMana = getMana(magicData);
            if (currentMana <= 0.0F) return;

            double dmgPerMana = ManaFluxVeilItem.getDamagePerMana(veilStack);
            float manaNeeded = (float) (incomingDamage / dmgPerMana);
            float manaToSpend = Math.min(currentMana, manaNeeded);
            float blockedDamage = (float) (manaToSpend * dmgPerMana);

            if (blockedDamage > 0.0F) {
                // Тратим ману
                float targetMana = currentMana - manaToSpend;
                addManaOrSet(magicData, targetMana - currentMana, targetMana);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    trySyncToPlayer(magicData, sp);
                }

                // БЛОКИРУЕМ урон
                event.setCanceled(true);
                event.setAmount(0.0F);

                // XP: 3 маны = 1 опыт
                int experience = Math.max((int) (manaToSpend / 3.0F), 1);
                if (veilStack.getItem() instanceof ManaFluxVeilItem relic) {
                    relic.spreadExperience(player, veilStack, experience);
                }

                MARRelicsMod.LOGGER.info("🛡️ Shield: {} dmg | {} mana | {} XP",
                        blockedDamage, manaToSpend, experience);
            }
        } catch (Throwable e) {
            MARRelicsMod.LOGGER.error("Shield failed: {}", e.getMessage());
        }
    }

    // 2. TICK - HP penalty + партикли
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;
        AtomicReference<ItemStack> veilStackRef = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.findFirstCurio(ModItems.MANA_FLUX_VEIL.get()).ifPresent(slotResult ->
                        veilStackRef.set(slotResult.stack()))
        );
        ItemStack veilStack = veilStackRef.get();

        // HP PENALTY (каждую секунду)
        if (player.tickCount % 20 == 0) {
            AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                if (veilStack.isEmpty()) {
                    maxHealthAttr.removeModifier(HP_PENALTY_UUID);
                } else {
                    double penalty = ManaFluxVeilItem.getHpPenalty(veilStack);

                    AttributeModifier old = maxHealthAttr.getModifier(HP_PENALTY_UUID);

// max HP без нашего штрафа (т.к. штраф у нас ADDITION)
                    double unpenalizedMax = maxHealthAttr.getValue();
                    if (old != null) unpenalizedMax -= old.getAmount(); // old.getAmount() отрицательный

                    double penaltyAmount = -unpenalizedMax * penalty;

                    if (old == null || Math.abs(old.getAmount() - penaltyAmount) > 0.01D) {
                        maxHealthAttr.removeModifier(HP_PENALTY_UUID);

                        AttributeModifier mod = new AttributeModifier(
                                HP_PENALTY_UUID,
                                "mana_flux_hp_penalty",
                                penaltyAmount,
                                AttributeModifier.Operation.ADDITION
                        );
                        maxHealthAttr.addTransientModifier(mod);
                    }

// Клампим HP только до текущего max HP (он уже со штрафом)
                    float maxNow = player.getMaxHealth();
                    if (player.getHealth() > maxNow) {
                        player.setHealth(maxNow);
                    }
                }
            }
        }


    }

    // REFLECTION (как в BloodGrimoire)
    private static float getMana(MagicData data) throws Exception {
        Method m = data.getClass().getMethod("getMana");
        Object v = m.invoke(data);
        return v instanceof Number n ? n.floatValue() : 0.0F;
    }

    private static void addManaOrSet(MagicData data, float deltaToAdd, float targetMana) throws Exception {
        try {
            Method add = data.getClass().getMethod("addMana", float.class);
            add.invoke(data, deltaToAdd);
            return;
        } catch (NoSuchMethodException ignored) {}
        try {
            Method setF = data.getClass().getMethod("setMana", float.class);
            setF.invoke(data, targetMana);
            return;
        } catch (NoSuchMethodException ignored) {}
        Method setI = data.getClass().getMethod("setMana", int.class);
        setI.invoke(data, (int) targetMana);
    }

    private static void trySyncToPlayer(MagicData data, net.minecraft.server.level.ServerPlayer player) {
        try {
            Method getSynced = data.getClass().getMethod("getSyncedData");
            Object synced = getSynced.invoke(data);
            if (synced == null) return;
            Method syncToPlayer = synced.getClass().getMethod("syncToPlayer", net.minecraft.server.level.ServerPlayer.class);
            syncToPlayer.invoke(synced, player);
        } catch (Throwable ignored) {}
    }
}
