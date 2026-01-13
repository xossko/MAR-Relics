package com.vladisss.marrelics.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.vladisss.marrelics.event.SethBladeEventHandler;
import com.vladisss.marrelics.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class SethBladeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seth")
                .requires(source -> source.hasPermission(2)) // OP level 2

                // /seth demand - запустить жертвоприношение
                .then(Commands.literal("demand")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return startDemand(player);
                        }))

                // /seth hunt [stage] - запустить охоту (опционально стадия)
                .then(Commands.literal("hunt")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return startHunt(player, 1);
                        })
                        .then(Commands.argument("stage", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int stage = IntegerArgumentType.getInteger(ctx, "stage");
                                    return startHunt(player, stage);
                                })))

                // /seth stop - остановить всё
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return stopAll(player);
                        }))

                // /seth unlock - разблокировать клинок
                .then(Commands.literal("unlock")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return unlockBlade(player);
                        }))

                // /seth debug - включить/выключить debug
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return toggleDebug(player);
                        }))

                // /seth reset - полный сброс
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return resetAll(player);
                        }))

                // /seth power <damage> <health> - установить силу
                .then(Commands.literal("power")
                        .then(Commands.argument("damage", IntegerArgumentType.integer(0, 10))
                                .then(Commands.argument("health", IntegerArgumentType.integer(0, 20))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            int dmg = IntegerArgumentType.getInteger(ctx, "damage");
                                            int hp = IntegerArgumentType.getInteger(ctx, "health");
                                            return setPower(player, dmg, hp);
                                        }))))
        );
    }

    private static int startDemand(ServerPlayer player) {
        ItemStack blade = findBlade(player);
        if (blade.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cУ тебя нет клинка Seth!"));
            return 0;
        }

        LivingEntity target = findNearestMob(player, 50.0);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cНет мобов в радиусе 50 блоков!"));
            return 0;
        }

        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        // Останавливаем охоту если активна
        data.putBoolean("seth_hunt_active", false);

        // Запускаем жертвоприношение
        data.putBoolean("seth_pre_active", true);
        data.putInt("seth_pre_pull_last_step", 0);
        data.putString("seth_pre_target", target.getUUID().toString());
        data.putLong("seth_pre_last_anger", now);

        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0, false, false, true));

        int idx = blade.getOrCreateTag().getInt("seth_sacrifice_index");
        player.sendSystemMessage(Component.literal("§4Seth demands: §f" + target.getName().getString() + " §7(" + idx + "/5)"));
        return 1;
    }

    private static int startHunt(ServerPlayer player, int stage) {
        ItemStack blade = findBlade(player);
        if (blade.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cУ тебя нет клинка Seth!"));
            return 0;
        }

        if (!blade.getOrCreateTag().getBoolean("seth_unlocked")) {
            player.sendSystemMessage(Component.literal("§cКлинок должен быть разблокирован!"));
            return 0;
        }

        LivingEntity target = findNearestMob(player, 50.0);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cНет мобов в радиусе 50 блоков!"));
            return 0;
        }

        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        // Останавливаем жертвоприношение если активно
        data.putBoolean("seth_pre_active", false);

        // Запускаем охоту
        data.putBoolean("seth_hunt_active", true);
        data.putString("seth_hunt_target", target.getUUID().toString());
        data.putInt("seth_hunt_stage", stage);

        // Устанавливаем время начала в зависимости от стадии
        long stageOffset = 0;
        if (stage == 2) {
            stageOffset = 20 * 60 * 2; // 2 минуты назад = стадия 2
        } else if (stage == 3) {
            stageOffset = 20 * 60 * 4; // 4 минуты назад = стадия 3
        }
        data.putLong("seth_hunt_start", now - stageOffset);

        data.remove("seth_stage1_tp_done");
        data.remove("seth_locked_slot");
        data.putLong("seth_control_pause_until", 0);

        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0, false, false, true));

        player.sendSystemMessage(Component.literal("§4Hunt Stage " + stage + " started: §f" + target.getName().getString()));
        return 1;
    }

    private static int stopAll(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean("seth_pre_active", false);
        data.putBoolean("seth_hunt_active", false);
        data.remove("seth_pre_target");
        data.remove("seth_hunt_target");
        data.remove("seth_locked_slot");
        data.remove("seth_cached_target_id");
        data.remove("seth_cached_target_tick");

        player.sendSystemMessage(Component.literal("§7Seth activity stopped."));
        return 1;
    }

    private static int unlockBlade(ServerPlayer player) {
        ItemStack blade = findBlade(player);
        if (blade.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cУ тебя нет клинка Seth!"));
            return 0;
        }

        CompoundTag tag = blade.getOrCreateTag();
        tag.putBoolean("seth_unlocked", true);
        tag.putInt("seth_sacrifice_index", 5);
        tag.remove("seth_sacrifice_target");

        player.sendSystemMessage(Component.literal("§cКлинок разблокирован!"));
        return 1;
    }

    private static int toggleDebug(ServerPlayer player) {
        boolean hasTag = player.getTags().contains("seth_debug");

        if (hasTag) {
            player.removeTag("seth_debug");
            player.sendSystemMessage(Component.literal("§7Debug режим §cвыключен"));
        } else {
            player.addTag("seth_debug");
            player.sendSystemMessage(Component.literal("§7Debug режим §aвключен"));
        }

        return 1;
    }

    private static int resetAll(ServerPlayer player) {
        ItemStack blade = findBlade(player);

        // Сброс NBT игрока
        CompoundTag data = player.getPersistentData();
        data.remove("seth_bonus_damage");
        data.remove("seth_bonus_health");
        data.remove("seth_pre_active");
        data.remove("seth_pre_target");
        data.remove("seth_pre_next");
        data.remove("seth_hunt_active");
        data.remove("seth_hunt_target");
        data.remove("seth_hunt_next");
        data.remove("seth_hunt_start");
        data.remove("seth_hunt_stage");
        data.remove("seth_locked_slot");
        data.remove("seth_cached_target_id");
        data.remove("seth_cached_target_tick");

        // Сброс NBT клинка
        if (!blade.isEmpty()) {
            CompoundTag tag = blade.getOrCreateTag();
            tag.putBoolean("seth_unlocked", false);
            tag.putInt("seth_sacrifice_index", 0);
            tag.putInt("seth_total_kills", 0);
            tag.remove("seth_sacrifice_target");
            tag.putBoolean("seth_awaken_sounds_done", false);
            tag.putBoolean("seth_setepai_done", false);
            tag.remove("seth_setepai_at");
        }

        player.sendSystemMessage(Component.literal("§7Полный сброс Seth выполнен!"));
        return 1;
    }

    private static int setPower(ServerPlayer player, int damage, int health) {
        CompoundTag data = player.getPersistentData();
        data.putDouble("seth_bonus_damage", (double) damage);
        data.putDouble("seth_bonus_health", (double) health);

        player.sendSystemMessage(Component.literal("§7Установлено: §c+" + damage + " урона, §a+" + health + " здоровья"));
        return 1;
    }

    private static ItemStack findBlade(ServerPlayer player) {
        if (player.getMainHandItem().is(ModItems.SETH_BLADE.get())) return player.getMainHandItem();
        if (player.getOffhandItem().is(ModItems.SETH_BLADE.get())) return player.getOffhandItem();
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.SETH_BLADE.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    private static LivingEntity findNearestMob(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> mobs = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator());

        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (LivingEntity mob : mobs) {
            double dist = player.distanceTo(mob);
            if (dist < nearestDist) {
                nearest = mob;
                nearestDist = dist;
            }
        }

        return nearest;
    }
}
