package com.vladisss.marrelics.event;

import com.vladisss.marrelics.duel.DuelArena;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.WarHelmetItem;
import com.vladisss.marrelics.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;


import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = MARRelicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WarHelmetEventHandler {
    private static final Map<UUID, DuelArena> ACTIVE_DUEL_ARENAS = new HashMap<>();
    private static final String DUEL_DAMAGE_TAG = "war_helmet_total_duel_damage";
    private static final Map<UUID, DuelStatus> ACTIVE_DUELS = new HashMap<>();
    private static final Set<UUID> IMMORTAL_PLAYERS = new HashSet<>();
    private static final UUID DUEL_DAMAGE_MODIFIER_UUID = UUID.fromString("a3b5c7d9-1234-5678-90ab-cdef12345678");

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // НОВАЯ ЛОГИКА: Бессмертие в дуэли
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            if (preventDeathInGrandDuel(event, player)) {
                return;
            }
        }
        // Проверка активации Великой Дуэли
        if (event.getSource().getEntity() instanceof Player attacker && !attacker.level().isClientSide()) {
            LivingEntity target = event.getEntity();

            // Проверяем флаг готовности к дуэли
            if (attacker.getPersistentData().getBoolean("war_helmet_duel_pending")) {
                long currentTime = attacker.level().getGameTime();
                long activationTime = attacker.getPersistentData().getLong("war_helmet_duel_activation_time");

                // Проверяем, что не прошло больше 5 секунд (100 тиков)
                if (currentTime - activationTime <= 100) {
                    int targetId = attacker.getPersistentData().getInt("war_helmet_target_id");

                    // Проверяем, что ударили именно ту цель (босса или игрока)
                    if (target.getId() == targetId && DuelArena.canDuel(target)) {
                        // Получаем шлем
                        AtomicReference<ItemStack> helmetStack = new AtomicReference<>(ItemStack.EMPTY);
                        CuriosApi.getCuriosInventory(attacker).ifPresent(handler -> {
                            handler.findFirstCurio(ModItems.WAR_HELMET.get()).ifPresent(slotResult -> {
                                helmetStack.set(slotResult.stack());
                            });
                        });

                        if (!helmetStack.get().isEmpty()) {
                            // Запускаем дуэль!
                            startGrandDuel(attacker, target, helmetStack.get());

                            attacker.getPersistentData().remove("war_helmet_duel_pending");
                            attacker.getPersistentData().remove("war_helmet_duel_activation_time");
                            attacker.getPersistentData().remove("war_helmet_target_id");
                            return;
                        }
                    } else {
                        attacker.displayClientMessage(
                                Component.literal("§cВы ударили не ту цель!"),
                                true
                        );
                    }
                }

                // Если время вышло - сбрасываем флаг (кулдаун уже идет)
                attacker.getPersistentData().remove("war_helmet_duel_pending");
                attacker.getPersistentData().remove("war_helmet_duel_activation_time");
                attacker.getPersistentData().remove("war_helmet_target_id");
            }
        }

        // Ситуация 1: Игрок ПОЛУЧАЕТ урон
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            handlePlayerDefense(event, player);
        }

        // Ситуация 2: Игрок НАНОСИТ урон (обычная дуэль 1 на 1)
        if (event.getSource().getEntity() instanceof Player attacker && !attacker.level().isClientSide()) {
            LivingEntity target = event.getEntity();
            handlePlayerAttack(attacker, target);
        }
    }
    private static boolean preventDeathInGrandDuel(LivingHurtEvent event, Player player) {
        UUID playerId = player.getUUID();

        DuelArena arena = ACTIVE_DUEL_ARENAS.get(playerId);
        if (arena == null || !arena.isActive()) {
            IMMORTAL_PLAYERS.remove(playerId);
            return false;
        }

        AtomicReference<ItemStack> helmetStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.WAR_HELMET.get()).ifPresent(slotResult -> {
                helmetStack.set(slotResult.stack());
            });
        });

        if (helmetStack.get().isEmpty()) {
            IMMORTAL_PLAYERS.remove(playerId);
            return false;
        }

        if (!WarHelmetItem.hasImmortalDuel(helmetStack.get())) {
            return false;
        }

        float incomingDamage = event.getAmount();
        float currentHealth = player.getHealth();

        if (currentHealth - incomingDamage <= 0) {
            if (!IMMORTAL_PLAYERS.contains(playerId)) {
                IMMORTAL_PLAYERS.add(playerId);

                if (player.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 60; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double radius = 1.5;
                        double height = Math.random() * 2;
                        serverLevel.sendParticles(
                                ParticleTypes.TOTEM_OF_UNDYING,
                                player.getX() + Math.cos(angle) * radius,
                                player.getY() + height,
                                player.getZ() + Math.sin(angle) * radius,
                                3, 0.1, 0.1, 0.1, 0.05
                        );
                    }

                    serverLevel.sendParticles(
                            ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1, player.getZ(),
                            20, 0.5, 1.0, 0.5, 0.1
                    );

                    serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.TOTEM_USE,
                            SoundSource.PLAYERS, 1.5f, 1.0f);
                }

                player.displayClientMessage(
                        Component.literal("§6§l⚔ БЕССМЕРТИЕ В ДУЭЛИ АКТИВНО! ⚔"),
                        false
                );

                MARRelicsMod.LOGGER.info("Immortal Duel activated for player: {}", player.getName().getString());
            }

            player.setHealth(0.5f);
            event.setCanceled(true);
            return true;
        }

        return false;
    }


    private static void startGrandDuel(Player player, LivingEntity opponent, ItemStack helmet) {
        double radius = WarHelmetItem.getArenaRadius(helmet);
        int durationTicks = (int)(WarHelmetItem.getDuration(helmet) * 20); // Секунды в тики

        DuelArena arena = new DuelArena(player, opponent, radius, durationTicks);

        ACTIVE_DUEL_ARENAS.put(player.getUUID(), arena);

        // Телепортируем обоих в центр арены
        Vec3 center = arena.getCenter();
        player.teleportTo(center.x - radius/2, center.y, center.z);
        opponent.teleportTo(center.x + radius/2, center.y, center.z);

        // Сообщение
        String opponentName = opponent.getName().getString();
        String duelType = opponent instanceof Player ? "ДУЭЛЬ С ИГРОКОМ" : "ВЕЛИКАЯ ДУЭЛЬ С БОССОМ";

        player.displayClientMessage(
                Component.literal("§6⚔ " + duelType + ": " + opponentName + " ⚔"),
                false
        );

        // Если противник - игрок, уведомляем его
        if (opponent instanceof Player opponentPlayer) {
            opponentPlayer.displayClientMessage(
                    Component.literal("§c⚔ " + player.getName().getString() + " вызвал вас на дуэль! ⚔"),
                    false
            );
        }

        MARRelicsMod.LOGGER.info("Grand Duel started: {} vs {} (duration: {} seconds)",
                player.getName().getString(),
                opponent.getName().getString(),
                WarHelmetItem.getDuration(helmet));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();

        // Проверяем все активные дуэли
        for (Map.Entry<UUID, DuelArena> entry : new HashMap<>(ACTIVE_DUEL_ARENAS).entrySet()) {
            DuelArena arena = entry.getValue();

            if (dead.getUUID().equals(arena.getOpponentId())) {
                // Противник умер - игрок победил!
                Player winner = (Player) arena.getLevel().getPlayerByUUID(arena.getPlayerId());
                if (winner != null) {
                    // Получаем шлем для определения награды
                    AtomicReference<ItemStack> helmetStack = new AtomicReference<>(ItemStack.EMPTY);
                    CuriosApi.getCuriosInventory(winner).ifPresent(handler -> {
                        handler.findFirstCurio(ModItems.WAR_HELMET.get()).ifPresent(slotResult -> {
                            helmetStack.set(slotResult.stack());
                        });
                    });

                    if (!helmetStack.get().isEmpty()) {
                        rewardDuelVictory(winner, dead, helmetStack.get());
                    }
                }
                arena.restoreArena(); // Восстанавливаем арену
                ACTIVE_DUEL_ARENAS.remove(entry.getKey());
            } else if (dead.getUUID().equals(arena.getPlayerId())) {
                // Игрок умер - дуэль проиграна
                // Уведомляем противника если он игрок
                if (arena.getLevel() instanceof ServerLevel serverLevel) {
                    // Ищем противника по UUID
                    for (Player player : serverLevel.players()) {
                        if (player.getUUID().equals(arena.getOpponentId())) {
                            player.displayClientMessage(
                                    Component.literal("§6⚔ ПОБЕДА В ДУЭЛИ! ⚔"),
                                    false
                            );
                            break;
                        }
                    }
                }
                arena.restoreArena(); // Восстанавливаем арену
                ACTIVE_DUEL_ARENAS.remove(entry.getKey());
            }
        }
    }

    private static void rewardDuelVictory(Player player, LivingEntity defeated, ItemStack helmet) {
        // Получаем бонус урона из шлема
        double damageReward = WarHelmetItem.getDamageReward(helmet);

        // Добавляем к общему накопленному бонусу
        double currentTotal = player.getPersistentData().getDouble(DUEL_DAMAGE_TAG);
        double newTotal = currentTotal + damageReward;
        player.getPersistentData().putDouble(DUEL_DAMAGE_TAG, newTotal);

        String defeatedName = defeated.getName().getString();
        String victoryMessage = defeated instanceof Player ?
                String.format("§6⚔ ПОБЕДА В ДУЭЛИ НАД %s! +%.2f к урону! ⚔", defeatedName, damageReward) :
                String.format("§6⚔ ПОБЕДА НАД БОССОМ %s! +%.2f к урону! ⚔", defeatedName, damageReward);

        player.displayClientMessage(
                Component.literal(victoryMessage),
                false
        );

        // Применяем бонус (если шлем надет)
        applyDuelDamageBonus(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;
        UUID playerId = player.getUUID();
        // НОВАЯ ЛОГИКА: Визуальные эффекты бессмертия
        if (IMMORTAL_PLAYERS.contains(playerId)) {
            DuelArena arena = ACTIVE_DUEL_ARENAS.get(playerId);

            if (arena == null || !arena.isActive()) {
                IMMORTAL_PLAYERS.remove(playerId);
            } else {
                if (player.tickCount % 3 == 0 && player.level() instanceof ServerLevel serverLevel) {
                    double angle = (player.tickCount * 0.1) % (Math.PI * 2);
                    for (int i = 0; i < 3; i++) {
                        double offset = (Math.PI * 2 / 3) * i;
                        double x = player.getX() + Math.cos(angle + offset) * 1.2;
                        double z = player.getZ() + Math.sin(angle + offset) * 1.2;

                        serverLevel.sendParticles(
                                ParticleTypes.END_ROD,
                                x, player.getY() + 1, z,
                                1, 0.0, 0.0, 0.0, 0.0
                        );
                    }

                    if (player.tickCount % 10 == 0) {
                        serverLevel.sendParticles(
                                ParticleTypes.ENCHANTED_HIT,
                                player.getX(), player.getY() + 1, player.getZ(),
                                3, 0.3, 0.5, 0.3, 0.0
                        );
                    }
                }

                if (player.tickCount % 20 == 0) {
                    player.displayClientMessage(
                            Component.literal("§e⚔ БЕССМЕРТИЕ АКТИВНО ⚔"),
                            true
                    );
                }
            }
        }

        // 1. Обработка обычных дуэлей (бонусы 1 на 1)
        if (ACTIVE_DUELS.containsKey(playerId)) {
            DuelStatus duel = ACTIVE_DUELS.get(playerId);

            if (!duel.opponent.isAlive()) {
                removeDuelBonuses(player);
            } else {
                double distance = player.distanceTo(duel.opponent);
                if (distance > 10.0) {
                    removeDuelBonuses(player);
                }
            }
        }

        // 2. Проверяем, надет ли шлем, и применяем/убираем бонус урона от дуэлей
        updateDuelDamageBonus(player);

        // 3. Обработка арены Великой Дуэли
        DuelArena arena = ACTIVE_DUEL_ARENAS.get(playerId);
        if (arena != null && player.level() instanceof ServerLevel serverLevel) {
            // Тикаем арену
            arena.tick();

            // Показываем таймер каждые 10 тиков (0.5 сек)
            if (player.tickCount % 10 == 0) {
                int secondsLeft = arena.getTicksRemaining() / 20;
                String timerColor = secondsLeft <= 10 ? "§c" : secondsLeft <= 30 ? "§e" : "§a";
                player.displayClientMessage(
                        Component.literal(timerColor + "⚔ Время дуэли: " + secondsLeft + " сек ⚔"),
                        true // actionbar
                );
            }

            // Проверяем, закончилось ли время
            if (!arena.isActive()) {
                // Время вышло - дуэль окончена
                player.displayClientMessage(
                        Component.literal("§cВремя дуэли истекло! Ничья."),
                        false
                );

                // Уведомляем противника если он игрок
                for (Player p : serverLevel.players()) {
                    if (p.getUUID().equals(arena.getOpponentId())) {
                        p.displayClientMessage(
                                Component.literal("§cВремя дуэли истекло! Ничья."),
                                false
                        );
                        break;
                    }
                }

                arena.restoreArena(); // Восстанавливаем арену
                ACTIVE_DUEL_ARENAS.remove(playerId);
                return;
            }

            // Не даем покинуть арену (игроку)
            Vec3 pos = player.position();
            if (!arena.isInside(pos)) {
                Vec3 newPos = arena.pushInside(pos);
                player.teleportTo(newPos.x, newPos.y, newPos.z);
            }

            // Спавним частицы барьера каждые 5 тиков
            if (player.tickCount % 5 == 0) {
                arena.spawnBarrierParticles(serverLevel);
            }

            // Проверяем противника (босса или игрока)
            LivingEntity opponent = null;
            for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(arena.getRadius() + 10))) {
                if (entity.getUUID().equals(arena.getOpponentId())) {
                    opponent = entity;
                    break;
                }
            }

            if (opponent != null && !opponent.isRemoved()) {
                Vec3 opponentPos = opponent.position();
                if (!arena.isInside(opponentPos)) {
                    Vec3 newOpponentPos = arena.pushInside(opponentPos);
                    opponent.teleportTo(newOpponentPos.x, newOpponentPos.y, newOpponentPos.z);
                }
            }
        }
    }

    private static void updateDuelDamageBonus(Player player) {
        // Проверяем, есть ли у игрока шлем
        AtomicReference<ItemStack> helmetStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.WAR_HELMET.get()).ifPresent(slotResult -> {
                helmetStack.set(slotResult.stack());
            });
        });

        boolean hasHelmet = !helmetStack.get().isEmpty();
        double totalDamage = player.getPersistentData().getDouble(DUEL_DAMAGE_TAG);

        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            AttributeModifier existingModifier = attackDamage.getModifier(DUEL_DAMAGE_MODIFIER_UUID);

            if (hasHelmet && totalDamage > 0) {
                // Шлем надет и есть бонусы - применяем
                if (existingModifier == null) {
                    AttributeModifier damageBonus = new AttributeModifier(
                            DUEL_DAMAGE_MODIFIER_UUID,
                            "war_helmet_duel_damage",
                            totalDamage,
                            AttributeModifier.Operation.ADDITION
                    );
                    attackDamage.addTransientModifier(damageBonus);
                } else if (existingModifier.getAmount() != totalDamage) {
                    // Обновляем модификатор если сумма изменилась
                    attackDamage.removeModifier(DUEL_DAMAGE_MODIFIER_UUID);
                    AttributeModifier damageBonus = new AttributeModifier(
                            DUEL_DAMAGE_MODIFIER_UUID,
                            "war_helmet_duel_damage",
                            totalDamage,
                            AttributeModifier.Operation.ADDITION
                    );
                    attackDamage.addTransientModifier(damageBonus);
                }
            } else {
                // Шлем снят или нет бонусов - убираем модификатор
                if (existingModifier != null) {
                    attackDamage.removeModifier(DUEL_DAMAGE_MODIFIER_UUID);
                }
            }
        }
    }

    private static void applyDuelDamageBonus(Player player) {
        updateDuelDamageBonus(player);
    }

    private static void handlePlayerDefense(LivingHurtEvent event, Player player) {
        AtomicReference<ItemStack> helmetStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.WAR_HELMET.get()).ifPresent(slotResult -> {
                helmetStack.set(slotResult.stack());
            });
        });

        if (helmetStack.get().isEmpty()) {
            removeDuelBonuses(player);
            return;
        }

        if (!(helmetStack.get().getItem() instanceof WarHelmetItem relic)) return;

        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            removeDuelBonuses(player);
            return;
        }

        if (checkDuelConditions(player, attacker, helmetStack.get(), relic)) {
            double damageReduction = WarHelmetItem.getDamageReduction(helmetStack.get());
            float reducedDamage = event.getAmount() * (1.0f - (float)damageReduction);
            event.setAmount(reducedDamage);
        }
    }

    private static void handlePlayerAttack(Player attacker, LivingEntity target) {
        AtomicReference<ItemStack> helmetStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(attacker).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.WAR_HELMET.get()).ifPresent(slotResult -> {
                helmetStack.set(slotResult.stack());
            });
        });

        if (helmetStack.get().isEmpty()) {
            removeDuelBonuses(attacker);
            return;
        }

        if (!(helmetStack.get().getItem() instanceof WarHelmetItem relic)) return;

        checkDuelConditions(attacker, target, helmetStack.get(), relic);
    }

    private static boolean checkDuelConditions(Player player, LivingEntity opponent, ItemStack helmetStack, WarHelmetItem relic) {
        double radius = WarHelmetItem.getDetectionRadius(helmetStack);

        AABB searchBox = player.getBoundingBox().inflate(radius);
        List<LivingEntity> nearbyEnemies = player.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity != player &&
                        entity != opponent &&
                        !entity.isAlliedTo(player) &&
                        entity.isAlive()
        );

        if (nearbyEnemies.isEmpty()) {
            applyDuelBonuses(player, helmetStack, opponent);
            relic.spreadExperience(player, helmetStack, 1);
            return true;
        } else {
            removeDuelBonuses(player);
            return false;
        }
    }

    private static void applyDuelBonuses(Player player, ItemStack stack, LivingEntity opponent) {
        double attackSpeedBonus = WarHelmetItem.getAttackSpeedBonus(stack);

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            UUID modifierId = UUID.nameUUIDFromBytes("war_helmet_speed".getBytes());

            if (attackSpeed.getModifier(modifierId) == null) {
                AttributeModifier speedModifier = new AttributeModifier(
                        modifierId,
                        "war_helmet_attack_speed",
                        attackSpeedBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                );
                attackSpeed.addPermanentModifier(speedModifier);
            }
        }

        ACTIVE_DUELS.put(player.getUUID(), new DuelStatus(opponent));
    }

    private static void removeDuelBonuses(Player player) {
        UUID playerId = player.getUUID();

        if (ACTIVE_DUELS.containsKey(playerId)) {
            AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                UUID modifierId = UUID.nameUUIDFromBytes("war_helmet_speed".getBytes());
                attackSpeed.removeModifier(modifierId);
            }

            ACTIVE_DUELS.remove(playerId);
        }
    }

    private static class DuelStatus {
        LivingEntity opponent;

        DuelStatus(LivingEntity opponent) {
            this.opponent = opponent;
        }
    }
}
