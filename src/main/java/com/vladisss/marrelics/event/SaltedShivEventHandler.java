package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.SaltedShiv;
import com.vladisss.marrelics.registry.ModItems;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import com.vladisss.marrelics.util.VanishManager;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber
public class SaltedShivEventHandler {

    private static final Map<UUID, List<StealStack>> ENTITY_STACKS = new HashMap<>();
    private static final Map<UUID, UUID> SHADOW_DANCE_MODIFIERS = new HashMap<>();

    // САМЫЙ ВЫСОКИЙ ПРИОРИТЕТ - блокируем урон ДО всех остальных
    // САМЫЙ ВЫСОКИЙ ПРИОРИТЕТ - блокируем урон ДО всех остальных


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurtHighest(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            if (player.getPersistentData().getBoolean("shadowdance_active")) {
                DamageSource source = event.getSource();

                // Пропускаем только магический урон (магия обходит броню и защиту)
                String damageType = source.getMsgId();

                // Список магических источников урона
                if (damageType.contains("magic") ||
                        damageType.contains("spell") ||
                        damageType.contains("indirectMagic") ||
                        damageType.contains("thorns") ||
                        damageType.contains("wither") ||
                        source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) ||
                        source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) {
                    // Пропускаем магический урон
                    return;
                }

                // Блокируем всё остальное БЕЗ анимации и звука
                event.setCanceled(true);
                player.invulnerableTime = 0; // Сбрасываем invulnerable чтобы не было анимации
                return;
            }
        }
    }


    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Существующая логика кражи
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker.level().isClientSide()) return;

        ItemStack weapon = ItemStack.EMPTY;

        if (attacker.getMainHandItem().getItem() == ModItems.SALTED_SHIV.get()) {
            weapon = attacker.getMainHandItem();
        } else {
            AtomicReference<ItemStack> curioStack = new AtomicReference<>(ItemStack.EMPTY);
            CuriosApi.getCuriosInventory(attacker).ifPresent(handler -> {
                handler.findFirstCurio(ModItems.SALTED_SHIV.get()).ifPresent(slotResult -> {
                    curioStack.set(slotResult.stack());
                });
            });
            weapon = curioStack.get();
        }

        if (weapon.isEmpty()) return;

        LivingEntity target = event.getEntity();
        int duration = SaltedShiv.getStealDuration(weapon);

        applyStealStack(attacker, target, duration, weapon);
    }

    // Запрет мобам таргетить игрока в Shadow Dance
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player) {
            if (player.getPersistentData().getBoolean("shadowdance_active")) {
                event.setCanceled(true);
            }
        }
    }

    private static void applyStealStack(Player attacker, LivingEntity target, int duration, ItemStack weapon) {
        try {
            boolean hasSpeedSteal = SaltedShiv.hasSpeedSteal(weapon);
            double speedStealAmount = hasSpeedSteal ? SaltedShiv.getAttackSpeedStealAmount(weapon) : 0.0;

            UUID targetHealthModId = UUID.randomUUID();
            UUID targetDamageModId = UUID.randomUUID();
            UUID targetSpeedModId = hasSpeedSteal ? UUID.randomUUID() : null;

            UUID attackerHealthModId = UUID.randomUUID();
            UUID attackerDamageModId = UUID.randomUUID();
            UUID attackerSpeedModId = hasSpeedSteal ? UUID.randomUUID() : null;

            AttributeModifier targetHealthDebuff = new AttributeModifier(
                    targetHealthModId, "salted_shiv_health_steal", -1.0,
                    AttributeModifier.Operation.ADDITION);

            AttributeModifier targetDamageDebuff = new AttributeModifier(
                    targetDamageModId, "salted_shiv_damage_steal", -1.0,
                    AttributeModifier.Operation.ADDITION);

            AttributeInstance targetHealth = target.getAttribute(Attributes.MAX_HEALTH);
            AttributeInstance targetDamage = target.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance targetSpeed = target.getAttribute(Attributes.ATTACK_SPEED);

            if (targetHealth != null) {
                targetHealth.addTransientModifier(targetHealthDebuff);
                float newMaxHealth = target.getMaxHealth();
                if (target.getHealth() > newMaxHealth) {
                    target.setHealth(newMaxHealth);
                }
            }

            if (targetDamage != null) {
                targetDamage.addTransientModifier(targetDamageDebuff);
            }

            if (hasSpeedSteal && targetSpeed != null) {
                AttributeModifier targetSpeedDebuff = new AttributeModifier(
                        targetSpeedModId, "salted_shiv_speed_steal", -speedStealAmount,
                        AttributeModifier.Operation.ADDITION);
                targetSpeed.addTransientModifier(targetSpeedDebuff);
            }

            AttributeModifier attackerHealthBuff = new AttributeModifier(
                    attackerHealthModId, "salted_shiv_health_gain", 1.0,
                    AttributeModifier.Operation.ADDITION);

            AttributeModifier attackerDamageBuff = new AttributeModifier(
                    attackerDamageModId, "salted_shiv_damage_gain", 1.0,
                    AttributeModifier.Operation.ADDITION);

            AttributeInstance attackerHealth = attacker.getAttribute(Attributes.MAX_HEALTH);
            AttributeInstance attackerDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance attackerSpeed = attacker.getAttribute(Attributes.ATTACK_SPEED);

            if (attackerHealth != null) {
                attackerHealth.addTransientModifier(attackerHealthBuff);
                float currentHealth = attacker.getHealth();
                float maxHealth = attacker.getMaxHealth();
                if (currentHealth < maxHealth) {
                    attacker.setHealth(Math.min(currentHealth + 1.0F, maxHealth));
                }
            }

            if (attackerDamage != null) {
                attackerDamage.addTransientModifier(attackerDamageBuff);
            }

            if (hasSpeedSteal && attackerSpeed != null) {
                AttributeModifier attackerSpeedBuff = new AttributeModifier(
                        attackerSpeedModId, "salted_shiv_speed_gain", speedStealAmount,
                        AttributeModifier.Operation.ADDITION);
                attackerSpeed.addTransientModifier(attackerSpeedBuff);
            }

            ENTITY_STACKS.computeIfAbsent(target.getUUID(), k -> new ArrayList<>())
                    .add(new StealStack(targetHealthModId, targetDamageModId, targetSpeedModId, duration, target));

            ENTITY_STACKS.computeIfAbsent(attacker.getUUID(), k -> new ArrayList<>())
                    .add(new StealStack(attackerHealthModId, attackerDamageModId, attackerSpeedModId, duration, attacker));

            if (weapon.getItem() instanceof SaltedShiv relic) {
                relic.spreadExperience(attacker, weapon, 1);
            }

        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("Error applying Salted Shiv stack: ", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;
        processEntityStacks(player);
        handleShadowDance(player);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player) return;

        processEntityStacks(entity);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        UUID entityId = event.getEntity().getUUID();
        if (ENTITY_STACKS.containsKey(entityId)) {
            List<StealStack> stacks = ENTITY_STACKS.get(entityId);
            for (StealStack stack : stacks) {
                removeModifiers(event.getEntity(), stack);
            }
            ENTITY_STACKS.remove(entityId);
        }
    }

    private static void processEntityStacks(LivingEntity entity) {
        UUID entityId = entity.getUUID();
        if (!ENTITY_STACKS.containsKey(entityId)) return;

        List<StealStack> stacks = ENTITY_STACKS.get(entityId);
        List<StealStack> toRemove = new ArrayList<>();

        for (StealStack stack : stacks) {
            stack.ticksRemaining--;
            if (stack.ticksRemaining <= 0) {
                removeModifiers(entity, stack);
                toRemove.add(stack);
            }
        }

        if (!toRemove.isEmpty()) {
            stacks.removeAll(toRemove);
        }

        if (stacks.isEmpty()) {
            ENTITY_STACKS.remove(entityId);
        }
    }

    private static void removeModifiers(LivingEntity entity, StealStack stack) {
        try {
            AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
            AttributeInstance damage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance speed = entity.getAttribute(Attributes.ATTACK_SPEED);

            if (health != null) {
                health.removeModifier(stack.healthModifierId);
            }
            if (damage != null) {
                damage.removeModifier(stack.damageModifierId);
            }
            if (speed != null && stack.speedModifierId != null) {
                speed.removeModifier(stack.speedModifierId);
            }
        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("Error removing modifiers: ", e);
        }
    }

    // ============ SHADOW DANCE ============
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            VanishManager.onPlayerJoin(serverPlayer);
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = (Player) event.getEntity();

        // Убираем vanish при выходе
        if (player.getPersistentData().getBoolean("shadowdance_active")) {
            VanishManager.setVanished(player, false);
        }
    }



    private static void handleShadowDance(Player player) {
        if (!player.getPersistentData().getBoolean("shadowdance_active")) {
            removeShadowDanceEffects(player);
            return;
        }

        long currentTime = player.level().getGameTime();
        long endTime = player.getPersistentData().getLong("shadowdance_endtime");

        if (currentTime >= endTime) {
            player.getPersistentData().putBoolean("shadowdance_active", false);
            removeShadowDanceEffects(player);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Shadow Dance закончился"),
                    true
            );
            return;
        }

        ItemStack shivStack = findSaltedShiv(player);
        if (shivStack.isEmpty()) {
            player.getPersistentData().putBoolean("shadowdance_active", false);
            removeShadowDanceEffects(player);
            return;
        }

        applyShadowDanceEffects(player, shivStack);

        // Постоянно сбрасываем aggro у ближайших мобов
        if (player.tickCount % 10 == 0) {
            clearNearbyMobsTarget(player);
        }

        // Уменьшено до каждые 3 тика
        if (player.tickCount % 3 == 0 && player.level() instanceof ServerLevel serverLevel) {
            spawnShadowParticles(serverLevel, player);
        }

        if (player.tickCount % 20 == 0) {
            double regen = SaltedShiv.getShadowDanceRegen(shivStack);
            player.heal((float)regen);
        }
    }

    private static void clearNearbyMobsTarget(Player player) {
        AABB searchBox = player.getBoundingBox().inflate(20.0);
        List<Mob> nearbyMobs = player.level().getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> mob.getTarget() == player
        );

        for (Mob mob : nearbyMobs) {
            mob.setTarget(null);
        }
    }

    private static ItemStack findSaltedShiv(Player player) {
        if (player.getMainHandItem().getItem() == ModItems.SALTED_SHIV.get()) {
            return player.getMainHandItem();
        }

        AtomicReference<ItemStack> curioStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.SALTED_SHIV.get()).ifPresent(slotResult -> {
                curioStack.set(slotResult.stack());
            });
        });
        return curioStack.get();
    }

    private static void applyShadowDanceEffects(Player player, ItemStack stack) {
        double speedBonus = SaltedShiv.getShadowDanceSpeed(stack);

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            UUID modId = SHADOW_DANCE_MODIFIERS.computeIfAbsent(
                    player.getUUID(),
                    k -> UUID.randomUUID()
            );

            if (speedAttr.getModifier(modId) == null) {
                AttributeModifier speedMod = new AttributeModifier(
                        modId, "shadowdance_speed", speedBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE);
                speedAttr.addTransientModifier(speedMod);
            }
        }

        // НОВОЕ: Настоящий vanish
        VanishManager.setVanished(player, true);
    }

    private static void removeShadowDanceEffects(Player player) {
        UUID modId = SHADOW_DANCE_MODIFIERS.remove(player.getUUID());
        if (modId != null) {
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(modId);
            }
        }

        // НОВОЕ: Убираем vanish
        VanishManager.setVanished(player, false);
    }

    private static void makeInvisibleToOthers(Player player) {
        if (!player.isInvisible()) {
            player.setInvisible(true);
            player.getPersistentData().putBoolean("shadowdance_invis", true);
        }

        // Максимальная невидимость
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 255, false, false, false));

        // НОВОЕ: Уменьшаем размер модели до 0 (но это работает только локально)
        if (player.level().isClientSide()) {
            player.getPersistentData().putFloat("pehkui:scale_width", 0.01F);
            player.getPersistentData().putFloat("pehkui:scale_height", 0.01F);
        }
    }

    private static void restoreVisibility(Player player) {
        if (player.getPersistentData().getBoolean("shadowdance_invis")) {
            player.setInvisible(false);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.getPersistentData().remove("shadowdance_invis");

            // Восстанавливаем размер
            if (player.level().isClientSide()) {
                player.getPersistentData().remove("pehkui:scale_width");
                player.getPersistentData().remove("pehkui:scale_height");
            }
        }
    }




    private static void spawnShadowParticles(ServerLevel level, Player player) {
        double radius = 2.0;

        // Основной круг дымки - уменьшено количество
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI * i) / 16;
            double x = player.getX() + radius * Math.cos(angle);
            double z = player.getZ() + radius * Math.sin(angle);

            level.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    x, player.getY() + 0.5, z,
                    2, 0.1, 0.2, 0.1, 0.01
            );

            if (i % 2 == 0) {
                level.sendParticles(
                        ParticleTypes.SQUID_INK,
                        x, player.getY() + 1.0, z,
                        1, 0.05, 0.1, 0.05, 0.0
                );
            }
        }

        // Дополнительные слои - уменьшено
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < 8; i++) {
                double angle = (2 * Math.PI * i) / 8;
                double r = radius * (0.6 + Math.random() * 0.4);
                double x = player.getX() + r * Math.cos(angle);
                double z = player.getZ() + r * Math.sin(angle);

                level.sendParticles(
                        ParticleTypes.SMOKE,
                        x, player.getY() + y * 0.5, z,
                        1, 0.1, 0.1, 0.1, 0.005
                );
            }
        }

        // Частицы вокруг игрока - уменьшено
        for (int i = 0; i < 6; i++) {
            double offsetX = (Math.random() - 0.5) * 1.5;
            double offsetY = Math.random() * 2.0;
            double offsetZ = (Math.random() - 0.5) * 1.5;

            level.sendParticles(
                    ParticleTypes.SMOKE,
                    player.getX() + offsetX,
                    player.getY() + offsetY,
                    player.getZ() + offsetZ,
                    1, 0.0, 0.0, 0.0, 0.0
            );
        }
    }

    private static class StealStack {
        UUID healthModifierId;
        UUID damageModifierId;
        UUID speedModifierId;
        int ticksRemaining;
        LivingEntity entity;

        StealStack(UUID healthId, UUID damageId, UUID speedId, int duration, LivingEntity entity) {
            this.healthModifierId = healthId;
            this.damageModifierId = damageId;
            this.speedModifierId = speedId;
            this.ticksRemaining = duration;
            this.entity = entity;
        }
    }
}
