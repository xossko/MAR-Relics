package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.BloodGrimoireItem;
import com.vladisss.marrelics.registry.ModItems;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.util.helper.NetworkHelper;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;


import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = MARRelicsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BloodGrimoireEventHandler {

    private static final int MANA_CONVERSION_INTERVAL = 10; // каждые 3 секунды (60 тиков)
    private static final float MANA_RESTORE_PERCENT = 0.2f;  // 10% от макс. маны

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;
        ItemStack grimoire = findGrimoire(player);
        if (grimoire.isEmpty()) return;

        // Crimson Convergence активная способность
        if (player.getPersistentData().getBoolean("grimoireactive")) {
            handleCrimsonConvergence(player, grimoire);
        }

        // LP -> Mana конвертация (каждые 60 тиков = 3 секунды)
        if (player.tickCount % MANA_CONVERSION_INTERVAL == 0) {
            convertLPToMana(player, grimoire);
        }
    }

    /**
     * Безопасная (устойчивая к разным API) конвертация LP -> Mana.
     * Тут НЕЛЬЗЯ напрямую вызывать magicData.getMana()/setMana(...) иначе снова словите NoSuchMethodError.
     */
    private static void convertLPToMana(Player player, ItemStack grimoire) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);

            if (magicData == null) {
                MARRelicsMod.LOGGER.error("❌ MagicData is null для игрока: {}", player.getName().getString());
                return;
            }

            float currentMana = issGetMana(magicData);
            float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());

            float missingManaF = maxMana - currentMana;
            if (missingManaF <= 0.0001f) return;

            int missingMana = Math.max(1, (int) Math.ceil(missingManaF)); // сколько не хватает в целых

            int lpPerMana = Math.max(1, BloodGrimoireItem.getLPConversion(grimoire));

            Binding binding = new Binding(player.getUUID(), player.getName().getString());
            var network = NetworkHelper.getSoulNetwork(binding);
            int currentLP = network.getCurrentEssence();

// сколько маны вообще можем оплатить текущим LP
            int affordableMana = currentLP / lpPerMana;
            if (affordableMana <= 0) {
                if (player.tickCount % 200 == 0) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c[Blood Grimoire] Недостаточно LP для регенерации маны"),
                            true
                    );
                }
                return;
            }

// сколько маны хотим восстановить по проценту
            int desiredMana = Math.max(1, (int) Math.floor(maxMana * MANA_RESTORE_PERCENT));

// итог: восстанавливаем минимум 1, но не больше чем хотим/можем/не хватает
            int manaToRestore = Math.max(1, Math.min(missingMana, Math.min(desiredMana, affordableMana)));

            float targetMana = Math.min(currentMana + manaToRestore, maxMana);
            float delta = targetMana - currentMana;
            if (delta <= 0.0001f) return;

            issAddManaOrSet(magicData, delta, targetMana);

            if (player instanceof ServerPlayer sp) {
                issTrySyncToPlayer(magicData, sp);
            }

            network.setCurrentEssence(currentLP - (manaToRestore * lpPerMana));


            MARRelicsMod.LOGGER.info("✅ [{}] LP→Mana: -{} LP → +{} mana ({}/{})",
                    player.getName().getString(), (manaToRestore * lpPerMana), manaToRestore, (int) targetMana, (int) maxMana);


            // Частицы
            if (player.level() instanceof ServerLevel serverLevel) {
                spawnManaConversionParticles(serverLevel, player);
            }

        } catch (Throwable e) {
            MARRelicsMod.LOGGER.error("❌ Ошибка конвертации LP→Mana для {}", player.getName().getString(), e);
        }
    }

    // ----------------------------
    // Reflection helpers (Iron's Spells API-safe)
    // ----------------------------

    private static float issGetMana(MagicData data) throws Exception {
        Method m = data.getClass().getMethod("getMana");
        Object v = m.invoke(data);
        if (v instanceof Number n) return n.floatValue();

        // На всякий случай, если вернули что-то странное
        return 0f;
    }

    /**
     * Пробуем:
     * 1) addMana(float) — если есть
     * 2) setMana(float) — если есть
     * 3) setMana(int)   — если есть
     */
    private static void issAddManaOrSet(MagicData data, float deltaToAdd, float targetMana) throws Exception {
        // 1) addMana(float)
        try {
            Method add = data.getClass().getMethod("addMana", float.class);
            add.invoke(data, deltaToAdd);
            return;
        } catch (NoSuchMethodException ignored) {
            // идём дальше
        }

        // 2) setMana(float)
        try {
            Method setF = data.getClass().getMethod("setMana", float.class);
            setF.invoke(data, targetMana);
            return;
        } catch (NoSuchMethodException ignored) {
            // идём дальше
        }

        // 3) setMana(int)
        Method setI = data.getClass().getMethod("setMana", int.class);
        setI.invoke(data, (int) targetMana);
    }

    /**
     * Безопасная синхронизация:
     * data.getSyncedData().syncToPlayer(serverPlayer)
     * но без прямой ссылки на классы syncedData, чтобы не ловить "cannot find symbol".
     */
    private static void issTrySyncToPlayer(MagicData data, ServerPlayer player) {
        try {
            Method getSynced = data.getClass().getMethod("getSyncedData");
            Object synced = getSynced.invoke(data);
            if (synced == null) return;

            Method syncToPlayer = synced.getClass().getMethod("syncToPlayer", ServerPlayer.class);
            syncToPlayer.invoke(synced, player);
        } catch (Throwable ignored) {
            // Если в конкретной версии ISS синк устроен иначе — просто пропускаем.
        }
    }

    // ----------------------------
    // Crimson Convergence (как было)
    // ----------------------------

    private static void handleCrimsonConvergence(Player player, ItemStack grimoire) {
        long currentTime = player.level().getGameTime();
        long endTime = player.getPersistentData().getLong("grimoireendtime");

        if (currentTime >= endTime) {
            player.getPersistentData().putBoolean("grimoireactive", false);
            player.getPersistentData().remove("grimoirelpspent");
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4§lCrimson Convergence завершилось!"), true);
            return;
        }

        // Проверка LP стоимости (один раз при активации)
        if (!player.getPersistentData().getBoolean("grimoirelpspent")) {
            int lpCost = BloodGrimoireItem.getLPCost(grimoire);

            try {
                Binding binding = new Binding(player.getUUID(), player.getName().getString());
                var network = NetworkHelper.getSoulNetwork(binding);
                int currentLP = network.getCurrentEssence();

                if (currentLP < lpCost) {
                    player.getPersistentData().putBoolean("grimoireactive", false);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cНедостаточно LP!"), true);
                    return;
                }

                network.setCurrentEssence(currentLP - lpCost);
                player.getPersistentData().putBoolean("grimoirelpspent", true);

                // Взрыв крови при активации
                if (player.level() instanceof ServerLevel serverLevel) {
                    spawnActivationExplosion(serverLevel, player.position());
                }
            } catch (Exception e) {
                MARRelicsMod.LOGGER.error("Error spending LP for Crimson Convergence", e);
                return;
            }
        }

        // Эффект каждые 20 тиков (1 секунда)
        if (player.tickCount % 20 == 0) {
            double radius = BloodGrimoireItem.getRadius(grimoire);
            double damagePercent = BloodGrimoireItem.getDamagePercent(grimoire);

            AABB searchBox = player.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = player.level().getEntitiesOfClass(
                    LivingEntity.class, searchBox,
                    entity -> entity != player && entity.isAlive()
            );

            int hitCount = 0;
            for (LivingEntity entity : entities) {
                if (!entity.isAlliedTo(player)) {
                    float damage = (float) (entity.getMaxHealth() * damagePercent);
                    entity.hurt(player.damageSources().magic(), damage);
                    entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1));
                    hitCount++;
                } else {
                    float heal = entity.getMaxHealth() * 0.01f;
                    entity.heal(heal);
                }
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                spawnCrimsonConvergenceParticles(serverLevel, player.position(), radius, currentTime);
            }

            if (grimoire.getItem() instanceof BloodGrimoireItem relic) {
                relic.spreadExperience(player, grimoire, hitCount);
            }
        }
    }

    // ----------------------------
    // Particles (как было)
    // ----------------------------

    private static void spawnManaConversionParticles(ServerLevel level, Player player) {
        Vec3 pos = player.position();

        // Цвета (можешь менять)
        DustParticleOptions BLOOD_RED = new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.10f), 1.15f);
        DustParticleOptions ARCANE_BLUE = new DustParticleOptions(new Vector3f(0.10f, 0.55f, 1.00f), 1.05f);

        int points = 14;          // сколько точек по кругу
        double radius = 0.75;     // радиус кольца
        double height = 1.0;      // высота кольца
        double spin = level.getGameTime() * 0.18; // скорость вращения

        for (int i = 0; i < points; i++) {
            double a = spin + i * (2 * Math.PI / points);

            double x = pos.x + radius * Math.cos(a);
            double z = pos.z + radius * Math.sin(a);
            double y = pos.y + height + 0.15 * Math.sin(a * 2);

            // Чередуем красный/синий, чтобы было "кроваво-синее"
            level.sendParticles((i % 2 == 0) ? BLOOD_RED : ARCANE_BLUE,
                    x, y, z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0);

            // Немного "крови" внутри кольца (не спамим)
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.CRIMSON_SPORE,
                        pos.x, pos.y + 0.9, pos.z,
                        1,
                        0.25, 0.15, 0.25,
                        0.01);
            }
        }
    }


    private static void spawnActivationExplosion(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 60; i++) {
            double angle = 2 * Math.PI * i / 60;
            double distance = 0.5 + (Math.random() * 3.0);
            double x = center.x + distance * Math.cos(angle);
            double z = center.z + distance * Math.sin(angle);
            double y = center.y + 0.5 + (Math.random() * 1.5);
            Vec3 velocity = new Vec3(Math.cos(angle) * 0.3, 0.1 + Math.random() * 0.2, Math.sin(angle) * 0.3);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 2, velocity.x, velocity.y, velocity.z, 0.1);
        }
        for (int i = 0; i < 30; i++) {
            double offsetX = (Math.random() - 0.5) * 0.5;
            double offsetZ = (Math.random() - 0.5) * 0.5;
            level.sendParticles(ParticleTypes.LAVA, center.x + offsetX, center.y + 0.2, center.z + offsetZ,
                    1, 0.0, 0.5 + Math.random() * 0.3, 0.0, 0.1);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void spawnCrimsonConvergenceParticles(ServerLevel level, Vec3 center, double radius, long gameTime) {
        int needleCount = (int) (radius * 12);
        for (int i = 0; i < needleCount; i++) {
            double angle = 2 * Math.PI * i / needleCount;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            for (int j = 0; j < 3; j++) {
                double y = center.y + 2.5 - (j * 0.5);
                level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, x, y, z, 1, 0.0, -0.2, 0.0, 0.0);
            }
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, x, center.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        int spiralPoints = 20;
        for (int i = 0; i < spiralPoints; i++) {
            double spiralAngle = ((gameTime * 0.05 + i * (2.0 * Math.PI / spiralPoints)) % (2.0 * Math.PI));
            double spiralRadius = radius * 0.3 * (1.0 - (double) i / spiralPoints);
            double sx = center.x + spiralRadius * Math.cos(spiralAngle);
            double sz = center.z + spiralRadius * Math.sin(spiralAngle);
            level.sendParticles(ParticleTypes.FALLING_NECTAR, sx, center.y + 0.5, sz, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (gameTime % 20 < 5) {
            int ringCount = (int) (radius * 15);
            for (int i = 0; i < ringCount; i++) {
                double angle = 2 * Math.PI * i / ringCount;
                double x = center.x + radius * 0.7 * Math.cos(angle);
                double z = center.z + radius * 0.7 * Math.sin(angle);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, center.y + 0.1, z, 1, 0.0, 0.1, 0.0, 0.0);
            }
        }
    }

    private static ItemStack findGrimoire(Player player) {
        AtomicReference<ItemStack> grimoireStack = new AtomicReference<>(ItemStack.EMPTY);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findFirstCurio(ModItems.BLOODGRIMOIRE.get()).ifPresent(slotResult -> {
                grimoireStack.set(slotResult.stack());
            });
        });
        return grimoireStack.get();
    }
}
