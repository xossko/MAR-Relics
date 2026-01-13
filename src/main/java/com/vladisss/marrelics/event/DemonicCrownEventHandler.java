package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.DemonicCrownItem;
import com.vladisss.marrelics.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = MARRelicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DemonicCrownEventHandler {

    // Кэш проклятых сущностей - ПУБЛИЧНЫЙ для доступа из DemonicCrownItem
    private static final Set<UUID> DOOMED_ENTITIES = new HashSet<>();
    private static final Map<UUID, ItemStack> CROWN_CACHE = new HashMap<>();
    private static long lastCacheUpdate = 0;

    // ПУБЛИЧНЫЙ метод для добавления сущности в кэш
    public static void addDoomedEntity(UUID uuid) {
        DOOMED_ENTITIES.add(uuid);
        MARRelicsMod.LOGGER.info("Added entity to doom cache: " + uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;
        UUID playerId = player.getUUID();

        long currentTime = player.level().getGameTime();
        if (currentTime - lastCacheUpdate > 20) {
            updateCrownCache(player);
            lastCacheUpdate = currentTime;
        }

        handleScorchedEarthTick(player);

        if (DOOMED_ENTITIES.contains(playerId)) {
            handleDoomTick(player);
            handleDoomEffects(player);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Player) return;

        LivingEntity entity = event.getEntity();
        UUID entityId = entity.getUUID();

        // Проверяем NBT и добавляем в кэш если нужно
        if (entity.getPersistentData().getBoolean("doomed")) {
            DOOMED_ENTITIES.add(entityId);
            handleDoomTick(entity);

            if (entity.tickCount % 5 == 0 && entity.getAbsorptionAmount() > 0) {
                entity.setAbsorptionAmount(0);
            }
        } else {
            DOOMED_ENTITIES.remove(entityId);
        }
    }

    private static void updateCrownCache(Player player) {
        AtomicReference<ItemStack> crownStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.findFirstCurio(ModItems.DEMONIC_CROWN.get()).ifPresent(slotResult ->
                        crownStack.set(slotResult.stack())
                )
        );

        if (!crownStack.get().isEmpty()) {
            CROWN_CACHE.put(player.getUUID(), crownStack.get());
        } else {
            CROWN_CACHE.remove(player.getUUID());
        }
    }

    private static void handleDoomEffects(Player player) {
        if (player.tickCount % 10 == 0 && player.getAbsorptionAmount() > 0) {
            player.setAbsorptionAmount(0);
        }

        if (player.tickCount % 20 == 0) {
            if (player.hasEffect(MobEffects.REGENERATION)) {
                player.removeEffect(MobEffects.REGENERATION);
            }
            if (player.hasEffect(MobEffects.ABSORPTION)) {
                player.removeEffect(MobEffects.ABSORPTION);
            }
            if (player.hasEffect(MobEffects.HEALTH_BOOST)) {
                player.removeEffect(MobEffects.HEALTH_BOOST);
            }
        }
    }

    private static void handleScorchedEarthTick(Player player) {
        if (!player.getPersistentData().getBoolean("demonic_crown_active")) return;

        long currentTime = player.level().getGameTime();
        long endTime = player.getPersistentData().getLong("demonic_crown_end_time");

        if (currentTime >= endTime) {
            player.getPersistentData().putBoolean("demonic_crown_active", false);
            return;
        }

        ItemStack crown = CROWN_CACHE.get(player.getUUID());
        if (crown == null || crown.isEmpty()) {
            player.getPersistentData().putBoolean("demonic_crown_active", false);
            return;
        }

        double radius = DemonicCrownItem.getScorchedRadius(crown);
        double damage = DemonicCrownItem.getScorchedDamage(crown);

        if (player.tickCount % 10 == 0) {
            AABB searchBox = player.getBoundingBox().inflate(Math.min(radius, 8.0));
            List<LivingEntity> enemies = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    searchBox,
                    entity -> entity != player && !entity.isAlliedTo(player) && entity.isAlive()
            );

            if (!enemies.isEmpty()) {
                int maxTargets = 30;
                int targetCount = Math.min(enemies.size(), maxTargets);

                for (int i = 0; i < targetCount; i++) {
                    enemies.get(i).hurt(player.damageSources().playerAttack(player), (float) damage);
                }

                if (crown.getItem() instanceof DemonicCrownItem relic) {
                    relic.spreadExperience(player, crown, targetCount);
                }
            }
        }

        if (player.tickCount % 5 == 0 && player.level() instanceof ServerLevel serverLevel) {
            spawnFireParticles(serverLevel, player.position(), radius);
        }
    }

    private static void handleDoomTick(LivingEntity entity) {
        if (!entity.getPersistentData().getBoolean("doomed")) {
            DOOMED_ENTITIES.remove(entity.getUUID());
            return;
        }

        long currentTime = entity.level().getGameTime();
        long endTime = entity.getPersistentData().getLong("doom_end_time");

        if (currentTime >= endTime) {
            removeDoom(entity);
            if (entity instanceof Player player) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§aDoom expired."),
                        true
                );
            }
            return;
        }

        float lastHealth = entity.getPersistentData().getFloat("doom_last_health");
        float currentHealth = entity.getHealth();

        if (lastHealth == 0) {
            entity.getPersistentData().putFloat("doom_last_health", currentHealth);
            lastHealth = currentHealth;
        }

        if (currentHealth > lastHealth) {
            entity.setHealth(lastHealth);
        }

        entity.getPersistentData().putFloat("doom_last_health", entity.getHealth());

        if (entity.tickCount % 20 == 0) {
            applyDoomDamage(entity);
            entity.getPersistentData().putFloat("doom_last_health", entity.getHealth());
        }

        if (entity.tickCount % 5 == 0 && entity.level() instanceof ServerLevel serverLevel) {
            spawnDoomParticles(serverLevel, entity.position(), entity.getBbHeight());
        }
    }

    private static void applyDoomDamage(LivingEntity entity) {
        MARRelicsMod.LOGGER.info("Applying Doom damage to " + entity.getName().getString());

        String sourceUUID = entity.getPersistentData().getString("doom_source");
        if (sourceUUID.isEmpty()) {
            MARRelicsMod.LOGGER.warn("No sourceUUID for doomed entity");
            return;
        }

        try {
            UUID playerUUID = UUID.fromString(sourceUUID);
            if (!(entity.level() instanceof ServerLevel serverLevel)) return;

            Player sourcePlayer = serverLevel.getPlayerByUUID(playerUUID);
            if (sourcePlayer == null) {
                MARRelicsMod.LOGGER.warn("Source player not found");
                return;
            }

            ItemStack crown = CROWN_CACHE.get(playerUUID);
            if (crown == null || crown.isEmpty()) {
                MARRelicsMod.LOGGER.warn("No crown in cache");
                return;
            }

            double percentDamage = DemonicCrownItem.getDoomPercentDamage(crown);
            double flatDamage = DemonicCrownItem.getDoomFlatDamage(crown);

            float percentHP = (float) (entity.getMaxHealth() * (percentDamage / 100.0));
            float damageThisTick = percentHP + (float) flatDamage;

            MARRelicsMod.LOGGER.info("Doom damage: " + damageThisTick + " HP");

            boolean guaranteedDeath = entity.getPersistentData().getBoolean("doom_guaranteed_death");
            float damageDealt = entity.getPersistentData().getFloat("doom_damage_dealt");
            float totalDamage = entity.getPersistentData().getFloat("doom_total_damage");

            if (guaranteedDeath) {
                // АБСОЛЮТНЫЙ урон - игнорирует броню
                float currentHealth = entity.getHealth();
                float newHealth = currentHealth - damageThisTick;

                damageDealt += damageThisTick;
                entity.getPersistentData().putFloat("doom_damage_dealt", damageDealt);

                // Проверяем достаточно ли урона нанесено
                if (damageDealt >= totalDamage - 0.1f || newHealth <= 0) {
                    // 100% ГАРАНТИРОВАННАЯ СМЕРТЬ - убивает ЛЮБЫХ мобов
                    MARRelicsMod.LOGGER.info("DOOM EXECUTION: Killing " + entity.getName().getString() + " (Total damage dealt: " + damageDealt + ")");

                    // Метод 1: entity.kill() - обходит инвульнерабельность
                    entity.kill();

                    // Метод 2 (резервный): Если kill() не сработал, используем команду
                    if (entity.isAlive()) {
                        MARRelicsMod.LOGGER.warn("entity.kill() failed, using command kill");
                        serverLevel.getServer().getCommands().performPrefixedCommand(
                                serverLevel.getServer().createCommandSourceStack(),
                                "kill " + entity.getUUID()
                        );
                    }

                    // Эффекты смерти
                    serverLevel.playSound(
                            null,
                            entity.blockPosition(),
                            net.minecraft.sounds.SoundEvents.WITHER_DEATH,
                            net.minecraft.sounds.SoundSource.HOSTILE,
                            2.0F,
                            0.6F
                    );

                    // Взрыв частиц
                    for (int i = 0; i < 50; i++) {
                        double offsetX = (Math.random() - 0.5) * 2.0;
                        double offsetY = Math.random() * 2.0;
                        double offsetZ = (Math.random() - 0.5) * 2.0;

                        serverLevel.sendParticles(
                                ParticleTypes.SOUL_FIRE_FLAME,
                                entity.getX() + offsetX,
                                entity.getY() + offsetY,
                                entity.getZ() + offsetZ,
                                1,
                                0.0, 0.0, 0.0,
                                0.1
                        );
                    }

                    return; // Выходим, больше не наносим урон
                }

                // Если еще не время умирать - просто снижаем HP
                entity.setHealth(Math.max(0.1f, newHealth));
            } else {
                // Обычный урон через DamageSource (с учетом брони)
                entity.hurt(sourcePlayer.damageSources().magic(), damageThisTick);
                damageDealt += damageThisTick;
                entity.getPersistentData().putFloat("doom_damage_dealt", damageDealt);
            }

            if (crown.getItem() instanceof DemonicCrownItem relic) {
                relic.spreadExperience(sourcePlayer, crown, 1);
            }
        } catch (Exception e) {
            MARRelicsMod.LOGGER.error("Error applying Doom damage", e);
        }
    }



    private static void removeDoom(LivingEntity entity) {
        DOOMED_ENTITIES.remove(entity.getUUID());
        entity.getPersistentData().remove("doomed");
        entity.getPersistentData().remove("doom_end_time");
        entity.getPersistentData().remove("doom_source");
        entity.getPersistentData().remove("doom_last_health");
        entity.getPersistentData().remove("doom_guaranteed_death");
        entity.getPersistentData().remove("doom_total_damage");
        entity.getPersistentData().remove("doom_damage_dealt");
        MARRelicsMod.LOGGER.info("Doom removed from " + entity.getName().getString());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (DOOMED_ENTITIES.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player) {
            if (DOOMED_ENTITIES.contains(player.getUUID())) {
                event.setCanceled(true);
                if (player.tickCount % 20 == 0) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§4You are Doomed! Cannot use items!"),
                            true
                    );
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (DOOMED_ENTITIES.contains(player.getUUID())) {
            event.setCanceled(true);
            if (player.tickCount % 20 == 0) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4You are Doomed! Cannot use items!"),
                        true
                );
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (DOOMED_ENTITIES.contains(player.getUUID())) {
            ItemStack heldItem = player.getItemInHand(event.getHand());
            if (!heldItem.isEmpty()) {
                event.setCanceled(true);
                if (player.tickCount % 20 == 0) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§4You are Doomed! Cannot use items!"),
                            true
                    );
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!DOOMED_ENTITIES.contains(player.getUUID())) return;

        boolean guaranteedDeath = player.getPersistentData().getBoolean("doom_guaranteed_death");

        if (guaranteedDeath) {
            ItemStack totemStack = ItemStack.EMPTY;
            if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                totemStack = player.getMainHandItem();
            } else if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                totemStack = player.getOffhandItem();
            }

            if (!totemStack.isEmpty()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4§l☠ DOOM EXECUTED - Totem failed! ☠"),
                        false
                );

                player.removeEffect(MobEffects.REGENERATION);
                player.removeEffect(MobEffects.ABSORPTION);
                player.removeEffect(MobEffects.FIRE_RESISTANCE);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (DOOMED_ENTITIES.contains(entity.getUUID())) {
            boolean wasGuaranteedDeath = entity.getPersistentData().getBoolean("doom_guaranteed_death");
            float damageDealt = entity.getPersistentData().getFloat("doom_damage_dealt");
            float totalDamage = entity.getPersistentData().getFloat("doom_total_damage");

            removeDoom(entity);

            MARRelicsMod.LOGGER.info("Entity died under Doom: " + entity.getName().getString() +
                    " (Guaranteed: " + wasGuaranteedDeath + ", Damage: " + damageDealt + "/" + totalDamage + ")");

            if (wasGuaranteedDeath && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(
                        null,
                        entity.blockPosition(),
                        net.minecraft.sounds.SoundEvents.WITHER_BREAK_BLOCK,
                        net.minecraft.sounds.SoundSource.HOSTILE,
                        1.5F,
                        0.5F
                );

                for (int i = 0; i < 20; i++) {
                    double offsetX = (Math.random() - 0.5) * 1.5;
                    double offsetY = Math.random() * 1.5;
                    double offsetZ = (Math.random() - 0.5) * 1.5;

                    serverLevel.sendParticles(
                            ParticleTypes.LARGE_SMOKE,
                            entity.getX() + offsetX,
                            entity.getY() + offsetY,
                            entity.getZ() + offsetZ,
                            1,
                            0.0, 0.0, 0.0,
                            0.05
                    );
                }
            }
        }
    }

    private static void spawnFireParticles(ServerLevel level, Vec3 center, double radius) {
        int particleCount = (int) (radius * 16);

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);

            level.sendParticles(ParticleTypes.FLAME, x, center.y + 0.1, z, 2, 0.2, 0.1, 0.2, 0.02);

            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, center.y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }

        for (int i = 0; i < 5; i++) {
            double offsetX = (Math.random() - 0.5) * 1.5;
            double offsetZ = (Math.random() - 0.5) * 1.5;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + offsetX, center.y + 0.2, center.z + offsetZ, 1, 0.0, 0.1, 0.0, 0.02);
        }
    }

    private static void spawnDoomParticles(ServerLevel level, Vec3 center, double entityHeight) {
        for (int i = 0; i < 5; i++) {
            double angle1 = (2 * Math.PI * i / 5) - Math.PI / 2;
            double angle2 = (2 * Math.PI * (i + 2) / 5) - Math.PI / 2;

            for (int j = 0; j < 8; j++) {
                double t = j / 8.0;
                double x1 = center.x + 1.5 * Math.cos(angle1);
                double z1 = center.z + 1.5 * Math.sin(angle1);
                double x2 = center.x + 1.5 * Math.cos(angle2);
                double z2 = center.z + 1.5 * Math.sin(angle2);

                double x = x1 + (x2 - x1) * t;
                double z = z1 + (z2 - z1) * t;

                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, center.y + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.FLAME, x, center.y + 0.1, z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }

        for (int i = 0; i < 24; i++) {
            double angle = 2 * Math.PI * i / 24;
            double x = center.x + 1.8 * Math.cos(angle);
            double z = center.z + 1.8 * Math.sin(angle);
            level.sendParticles(ParticleTypes.FLAME, x, center.y + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        for (int i = 0; i < 3; i++) {
            double angle = (System.currentTimeMillis() / 100.0 + i * 2.0) % (2 * Math.PI);
            double radius = 0.8;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);

            for (int y = 0; y < entityHeight + 1.0; y++) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, center.y + y * 0.3, z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        for (int i = 0; i < 6; i++) {
            double angle = 2 * Math.PI * i / 6;
            double x = center.x + 1.2 * Math.cos(angle);
            double z = center.z + 1.2 * Math.sin(angle);
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, center.y + entityHeight / 2, z, 1, 0.1, 0.2, 0.1, 0.01);
        }

        level.sendParticles(ParticleTypes.CRIMSON_SPORE, center.x, center.y + entityHeight + 0.5, center.z, 4, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.2, center.z, 1, 0.2, 0.1, 0.2, 0.0);
    }
}
