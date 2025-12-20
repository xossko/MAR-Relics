package com.vladisss.marrelics.event;

import com.vladisss.marrelics.items.ApostleOfDecayItem;
import com.vladisss.marrelics.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber
public class ApostleOfDecayEventHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        Level level = player.level();

        if (level.isClientSide()) return;

        // Каждую секунду (20 тиков)
        if (level.getGameTime() % 20 != 0) return;

        AtomicReference<ItemStack> apostleStack = new AtomicReference<>(ItemStack.EMPTY);

        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.APOSTLE_OF_DECAY.get()).ifPresent(slotResult -> {
                apostleStack.set(slotResult.stack());
            });
        });

        if (apostleStack.get().isEmpty()) return;
        if (!(apostleStack.get().getItem() instanceof ApostleOfDecayItem relic)) return;

        // Получаем параметры из реликвии
        double damagePercent = ApostleOfDecayItem.getDamagePercent(apostleStack.get());
        double radius = ApostleOfDecayItem.getRadius(apostleStack.get());

        applyDecayDamage(player, level, damagePercent, radius, apostleStack.get(), relic);
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();

        if (level.isClientSide()) return;

        // Ищем ближайших игроков с реликвией
        List<Player> nearbyPlayers = level.getEntitiesOfClass(
                Player.class,
                entity.getBoundingBox().inflate(20.0),
                player -> !player.equals(entity)
        );

        for (Player player : nearbyPlayers) {
            AtomicReference<ItemStack> apostleStack = new AtomicReference<>(ItemStack.EMPTY);

            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                handler.findFirstCurio(ModItems.APOSTLE_OF_DECAY.get()).ifPresent(slotResult -> {
                    apostleStack.set(slotResult.stack());
                });
            });

            if (apostleStack.get().isEmpty()) continue;
            if (!(apostleStack.get().getItem() instanceof ApostleOfDecayItem)) continue;

            double radius = ApostleOfDecayItem.getRadius(apostleStack.get());
            double distance = entity.distanceTo(player);

            // Если моб в радиусе действия
            if (distance <= radius && !entity.isAlliedTo(player)) {
                double healingReduction = ApostleOfDecayItem.getHealingReduction(apostleStack.get());

                if (healingReduction > 0) {
                    float originalHeal = event.getAmount();
                    float reducedHeal = originalHeal * (float)(1.0 - healingReduction);

                    event.setAmount(reducedHeal);

                    // Визуальный эффект - зеленые частицы в 3 раза больше
                    if (level instanceof ServerLevel serverLevel) {
                        for (int i = 0; i < 24; i++) { // 8 * 3 = 24 частицы
                            double ox = (level.random.nextDouble() - 0.5) * entity.getBbWidth();
                            double oy = level.random.nextDouble() * entity.getBbHeight();
                            double oz = (level.random.nextDouble() - 0.5) * entity.getBbWidth();

                            serverLevel.sendParticles(
                                    ParticleTypes.SPORE_BLOSSOM_AIR,
                                    entity.getX() + ox,
                                    entity.getY() + oy,
                                    entity.getZ() + oz,
                                    1, 0, 0.02, 0, 0.01
                            );
                        }
                    }
                }
                break;
            }
        }
    }

    private static void applyDecayDamage(Player player, Level level, double damagePercent,
                                         double radius, ItemStack stack, ApostleOfDecayItem relic) {
        AABB box = new AABB(
                player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                player.getX() + radius, player.getY() + radius, player.getZ() + radius
        );

        List<LivingEntity> mobs = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        for (LivingEntity target : mobs) {
            if (target.distanceTo(player) > radius) continue;

            float damage = (float) (target.getMaxHealth() * damagePercent);
            if (damage <= 0) continue;

            // Напрямую уменьшаем здоровье без анимации
            float newHealth = Math.max(0, target.getHealth() - damage);
            target.setHealth(newHealth);

            // Если здоровье достигло 0, убиваем моба
            if (newHealth <= 0) {
                target.hurt(level.damageSources().magic(), 0.1f);
            }

            // Добавляем опыт реликвии
            int experience = Math.max((int) (damage * 0.5), 1);
            relic.spreadExperience(player, stack, experience);

            // Спавним частицы разложения
            if (level instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 8; i++) {
                    double ox = (level.random.nextDouble() - 0.5) * target.getBbWidth();
                    double oy = level.random.nextDouble() * target.getBbHeight();
                    double oz = (level.random.nextDouble() - 0.5) * target.getBbWidth();

                    serverLevel.sendParticles(
                            ParticleTypes.SPORE_BLOSSOM_AIR,
                            target.getX() + ox,
                            target.getY() + oy,
                            target.getZ() + oz,
                            1, 0, 0.02, 0, 0.01
                    );
                }
            }
        }
    }
}
