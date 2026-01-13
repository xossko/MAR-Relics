package com.vladisss.marrelics.duel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DuelArena {
    private final UUID playerId;
    private final UUID opponentId;
    private final Vec3 center;
    private final double radius;
    private final Level level;
    private int ticksRemaining;
    private boolean isPlayerDuel;

    // Сохраняем оригинальные блоки для восстановления
    private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();

    public int getTicksRemaining() {
        return ticksRemaining;
    }


    public DuelArena(Player player, LivingEntity opponent, double radius, int durationTicks) {
        this.playerId = player.getUUID();
        this.opponentId = opponent.getUUID();
        this.center = player.position().add(opponent.position()).scale(0.5);
        this.radius = radius;
        this.level = player.level();
        this.ticksRemaining = durationTicks;
        this.isPlayerDuel = opponent instanceof Player;

        // Создаем платформу
        createArenaPlatform();

        // ВЫТАЛКИВАЕМ всех существ из арены!
        pushOutAllEntities();

        // Звук начала дуэли
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    private void createArenaPlatform() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Определяем уровень платформы (на 1 блок ниже центра)
        int platformY = (int) Math.floor(center.y) - 1;

        // Создаем круглую платформу
        int radiusInt = (int) Math.ceil(radius) + 1;

        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int z = -radiusInt; z <= radiusInt; z++) {
                double distance = Math.sqrt(x * x + z * z);

                if (distance <= radius + 0.5) {
                    BlockPos pos = new BlockPos(
                            (int) Math.floor(center.x) + x,
                            platformY,
                            (int) Math.floor(center.z) + z
                    );

                    // Сохраняем оригинальный блок
                    BlockState originalState = serverLevel.getBlockState(pos);
                    originalBlocks.put(pos, originalState);

                    // Определяем блок для платформы
                    BlockState platformBlock;

                    // Внешнее кольцо - красный нетеритовый кирпич
                    if (distance > radius - 1.5) {
                        platformBlock = Blocks.RED_NETHER_BRICKS.defaultBlockState();
                    }
                    // Среднее кольцо - обычный нетеритовый кирпич
                    else if (distance > radius / 2) {
                        platformBlock = Blocks.NETHER_BRICKS.defaultBlockState();
                    }
                    // Центр - полированный чернит
                    else {
                        platformBlock = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    }

                    // Устанавливаем блок с эффектом
                    serverLevel.setBlock(pos, platformBlock, 3);

                    // Частицы появления платформы
                    if (Math.random() < 0.3) {
                        serverLevel.sendParticles(
                                ParticleTypes.LAVA,
                                pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                                3, 0.3, 0.3, 0.3, 0.0
                        );
                    }
                }
            }
        }

        // Убираем блоки над платформой (чтобы не было препятствий)
        for (int y = 0; y < 3; y++) {
            for (int x = -radiusInt; x <= radiusInt; x++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double distance = Math.sqrt(x * x + z * z);

                    if (distance <= radius) {
                        BlockPos pos = new BlockPos(
                                (int) Math.floor(center.x) + x,
                                platformY + 1 + y,
                                (int) Math.floor(center.z) + z
                        );

                        BlockState state = serverLevel.getBlockState(pos);

                        // Сохраняем только если это не воздух
                        if (!state.isAir() && !state.is(Blocks.CAVE_AIR)) {
                            originalBlocks.put(pos, state);
                            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    public void restoreArena() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Восстанавливаем все оригинальные блоки
        for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState originalState = entry.getValue();

            serverLevel.setBlock(pos, originalState, 3);

            // Частицы восстановления
            if (Math.random() < 0.2) {
                serverLevel.sendParticles(
                        ParticleTypes.POOF,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        2, 0.2, 0.2, 0.2, 0.0
                );
            }
        }

        originalBlocks.clear();
    }

    private void pushOutAllEntities() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        AABB arenaBox = new AABB(
                center.x - radius - 5, center.y - 5, center.z - radius - 5,
                center.x + radius + 5, center.y + 10, center.z + radius + 5
        );

        List<LivingEntity> entities = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                arenaBox,
                entity -> !entity.getUUID().equals(playerId) &&
                        !entity.getUUID().equals(opponentId)
        );

        for (LivingEntity entity : entities) {
            Vec3 entityPos = entity.position();
            Vec3 fromCenter = entityPos.subtract(center);
            double distance = fromCenter.length();

            if (distance < radius + 2) {
                // Выталкиваем за пределы арены
                Vec3 pushDirection = fromCenter.normalize();
                Vec3 newPos = center.add(pushDirection.scale(radius + 3));
                entity.teleportTo(newPos.x, newPos.y, newPos.z);

                // Эффект выталкивания
                serverLevel.sendParticles(
                        ParticleTypes.EXPLOSION,
                        entityPos.x, entityPos.y + 1, entityPos.z,
                        3, 0.3, 0.3, 0.3, 0.0
                );
            }
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getOpponentId() {
        return opponentId;
    }

    // Совместимость со старым кодом
    public UUID getBossId() {
        return opponentId;
    }

    public Vec3 getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public Level getLevel() {
        return level;
    }

    public boolean isActive() {
        return ticksRemaining > 0;
    }

    public void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public void end() {
        ticksRemaining = 0;
        restoreArena(); // Восстанавливаем блоки
    }

    public boolean isInside(Vec3 pos) {
        return center.distanceTo(pos) <= radius;
    }

    public void spawnBarrierParticles(ServerLevel serverLevel) {
        int particleCount = (int)(radius * 12);

        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);

            // Огненная стена (выше)
            for (int y = 0; y < 8; y++) {
                serverLevel.sendParticles(
                        ParticleTypes.FLAME,
                        x, center.y + y * 0.5, z,
                        2, 0.05, 0.05, 0.05, 0.02
                );

                // Лава-эффект через каждый блок
                if (y % 2 == 0) {
                    serverLevel.sendParticles(
                            ParticleTypes.LAVA,
                            x, center.y + y * 0.5, z,
                            1, 0.0, 0.0, 0.0, 0.0
                    );
                }
            }

            // Дым сверху
            if (i % 3 == 0) {
                serverLevel.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        x, center.y + 4, z,
                        1, 0.1, 0.1, 0.1, 0.0
                );
            }
        }

        // Огненное кольцо на земле (как у Марса)
        int groundParticles = (int)(radius * 16);
        for (int i = 0; i < groundParticles; i++) {
            double angle = (2 * Math.PI * i) / groundParticles;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);

            serverLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x, center.y + 0.1, z,
                    1, 0.0, 0.0, 0.0, 0.0
            );
        }

        // Огненные столбы по 4 сторонам
        if (serverLevel.getGameTime() % 10 == 0) {
            double[] angles = {0, Math.PI/2, Math.PI, 3*Math.PI/2};
            for (double angle : angles) {
                double x = center.x + radius * Math.cos(angle);
                double z = center.z + radius * Math.sin(angle);

                for (int y = 0; y < 10; y++) {
                    serverLevel.sendParticles(
                            ParticleTypes.FLAME,
                            x, center.y + y * 0.3, z,
                            5, 0.2, 0.1, 0.2, 0.05
                    );
                }
            }
        }
    }

    public Vec3 pushInside(Vec3 pos) {
        Vec3 fromCenter = pos.subtract(center);
        double distance = fromCenter.length();

        if (distance > radius) {
            return center.add(fromCenter.normalize().scale(radius - 0.5));
        }

        return pos;
    }

    public static boolean isBoss(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss) return true;
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) return true;

        String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString();

        return entityId.contains("boss") ||
                entityId.contains("dragon") ||
                entityId.contains("wither") ||
                entity.getMaxHealth() >= 100.0f;
    }

    public static boolean canDuel(LivingEntity entity) {
        // Можно вызвать на дуэль игроков или боссов
        return entity instanceof Player || isBoss(entity);
    }
}
