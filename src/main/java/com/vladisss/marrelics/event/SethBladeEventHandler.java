package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.SethBladeItem;
import com.vladisss.marrelics.registry.ModItems;
import com.vladisss.marrelics.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = MARRelicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SethBladeEventHandler {

    // ====== ВОЗВРАТ ПОСЛЕ СМЕРТИ ======
    private static final Map<UUID, ItemStack> RETURN_ON_RESPAWN = new HashMap<>();

    // ====== БАФЫ СИЛЫ ======
    private static final UUID SETH_DAMAGE_UUID = UUID.fromString("b4e2b5e0-5c41-4d2b-9c44-2d8f7d0e8a01");
    private static final UUID SETH_HEALTH_UUID = UUID.fromString("a0f0e2c7-9df0-4e70-8a6a-9b5a0b4f90c2");
    private static final UUID SETH_OBSESSION_DMG_MULT_UUID = UUID.fromString("2df0a1d1-9b72-4c0a-b9f7-0c0c0f8f1a11");
    private static final UUID SETH_OBSESSION_AS_MULT_UUID = UUID.fromString("6d3f1b22-3a2a-4d2f-9d53-3f2d1a8d4b22");

    private static final int SACRIFICES_REQUIRED = 5;

    // ====== ИНТЕРВАЛЫ ======
    private static final int INTERVAL_MIN_TICKS = 20 * 60;
    private static final int INTERVAL_MAX_TICKS = 20 * 60 * 5;

    // ====== ДО РАЗБЛОКИРОВКИ ======
    private static final int PRE_ESCAPE_DISTANCE = 70;
    private static final int PRE_ACTIVE_DISTANCE = 40;
    private static final int PRE_ANGER_MIN_TICKS = 20 * 8;

    // ====== ПОСЛЕ РАЗБЛОКИРОВКИ ======
    private static final int HUNT_TOTAL_TICKS = 20 * 60 * 5;
    private static final int STAGE1_END = 20 * 60 * 2;
    private static final int STAGE2_END = 20 * 60 * 4;
    private static final int CONTROL_PAUSE_TICKS = 20 * 2;
    private static final double STAGE1_MAX_DISTANCE = 28.0;
    private static final double STAGE2_MAX_DISTANCE = 40.0;

    // ====== ПРИТЯЖЕНИЕ ======
    private static final double PRE_PULL_START_DISTANCE = 10.0;
    private static final double AWAKEN_PULL_STRENGTH = 0.35;
    private static final int PULL_LOCK_TICKS = 12;

    private static final String TAG_PRE_PULL_LAST_STEP = "seth_pre_pull_last_step";
    private static final double PRE_TUG_STEP = 15.0;
    private static final double PRE_TUG_STRENGTH = 0.28;

    private static final String TAG_LOCKED_SLOT = "seth_locked_slot";
    private static final String TAG_STUCK_TICKS = "seth_stuck_ticks";
    private static final String TAG_LAST_X = "seth_last_x";
    private static final String TAG_LAST_Z = "seth_last_z";

    private static final double STAGE23_MIN_DISTANCE = 2.8;
    private static final double TELEPORT_IF_FAR = 10.0;
    private static final double BEHIND_DISTANCE = 3.0;
    private static final int STUCK_TICKS_LIMIT = 10;

    private static final String TAG_PULL_UNTIL = "seth_pull_until";
    private static final String TAG_STAGE1_TP_DONE = "seth_stage1_tp_done";
    private static final String TAG_BLADE_AWAKEN_SOUNDS_DONE = "seth_awaken_sounds_done";
    private static final String TAG_BLADE_SETEPAI_AT = "seth_setepai_at";
    private static final String TAG_BLADE_SETEPAI_DONE = "seth_setepai_done";
    private static final int SETEPAI_DELAY_TICKS = 20 * 60;
    private static final String TAG_WAS_HOLDING_BLADE = "seth_was_holding_blade";

    // ====== ОПТИМИЗАЦИЯ: КЭШ ЦЕЛЕЙ ======
    private static final int TARGET_CACHE_INTERVAL = 10;
    private static final String TAG_CACHED_TARGET_ID = "seth_cached_target_id";
    private static final String TAG_CACHED_TARGET_TICK = "seth_cached_target_tick";

    // ====== DEBUG ======
    private static boolean debugEnabled(Player p) {
        return p.getTags().contains("seth_debug");
    }

    private static void debugLog(Player p, String msg) {
        if (!debugEnabled(p)) return;
        MARRelicsMod.LOGGER.info("[SethBlade][{}] {}", p.getGameProfile().getName(), msg);
    }

    private static void debugActionbar(Player p, String msg) {
        if (!debugEnabled(p)) return;
        if (p.tickCount % 20 == 0) p.displayClientMessage(Component.literal("§8[Seth] §7" + msg), true);
    }

    // ====== ПОИСК КЛИНКА И ЦЕЛИ ======
    private static ItemStack findBlade(Player p) {
        if (p.getMainHandItem().is(ModItems.SETH_BLADE.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(ModItems.SETH_BLADE.get())) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.SETH_BLADE.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isHoldingBlade(Player p) {
        return p.getMainHandItem().is(ModItems.SETH_BLADE.get())
                || p.getOffhandItem().is(ModItems.SETH_BLADE.get());
    }

    // ====== ВЫБОР САМОГО БЛИЖАЙШЕГО МОБА В РАДИУСЕ (даже через стены) ======
    private static LivingEntity pickTargetInFov(Player player, double maxDistance, Predicate<LivingEntity> filter) {
        Vec3 playerPos = player.position();

        AABB searchBox = new AABB(playerPos.add(-maxDistance, -maxDistance, -maxDistance),
                playerPos.add(maxDistance, maxDistance, maxDistance));

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator());

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            if (!filter.test(entity)) continue;

            double distance = player.distanceTo(entity);

            // Выбираем самого близкого в радиусе
            if (distance <= maxDistance && distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }

        return closest;
    }


    // ====== КЭШ ЦЕЛИ С ОБНОВЛЕНИЕМ РАЗ В 10 ТИКОВ ======
    private static LivingEntity getCachedTarget(Player player, String targetKey, double maxSearchDistance, long now) {
        CompoundTag data = player.getPersistentData();

        String targetUuid = data.getString(targetKey);
        if (targetUuid.isEmpty()) return null;

        long lastCacheTick = data.getLong(TAG_CACHED_TARGET_TICK);

        // Обновляем кэш каждые 10 тиков
        if (now - lastCacheTick >= TARGET_CACHE_INTERVAL || !data.contains(TAG_CACHED_TARGET_ID)) {
            UUID uuid = UUID.fromString(targetUuid);
            LivingEntity target = null;

            AABB box = player.getBoundingBox().inflate(maxSearchDistance);
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (e.getUUID().equals(uuid) && e.isAlive()) {
                    target = e;
                    break;
                }
            }

            if (target != null) {
                data.putString(TAG_CACHED_TARGET_ID, target.getUUID().toString());
                data.putLong(TAG_CACHED_TARGET_TICK, now);
                return target;
            } else {
                data.remove(TAG_CACHED_TARGET_ID);
                data.remove(TAG_CACHED_TARGET_TICK);
                return null;
            }
        }

        // Используем закэшированную цель
        String cachedId = data.getString(TAG_CACHED_TARGET_ID);
        if (cachedId.isEmpty()) return null;

        UUID uuid = UUID.fromString(cachedId);
        AABB box = player.getBoundingBox().inflate(maxSearchDistance);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e.getUUID().equals(uuid) && e.isAlive()) {
                return e;
            }
        }

        return null;
    }

    // ====== МАНИПУЛЯЦИИ С ИГРОКОМ ======
    private static void hardLockCameraToTarget(Player player, LivingEntity target) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(sp.level() instanceof ServerLevel lvl)) return;

        Vec3 from = sp.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 d = to.subtract(from);

        double dx = d.x, dy = d.y, dz = d.z;
        double h = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, h)));

        sp.teleportTo(lvl, sp.getX(), sp.getY(), sp.getZ(), yaw, pitch);
    }

    private static void teleportBehindTarget(Player player, LivingEntity target) {
        spawnTeleportSmoke(player);

        Vec3 look = target.getLookAngle().normalize();
        Vec3 dest = target.position().subtract(look.scale(BEHIND_DISTANCE));

        player.teleportTo(dest.x, target.getY() + 0.1, dest.z);

        spawnTeleportSmoke(player);
    }

    private static void spawnTeleportSmoke(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 25, 0.4, 0.6, 0.4, 0.01);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 35, 0.6, 0.8, 0.6, 0.01);
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 20, 0.4, 0.6, 0.4, 0.03);

        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private static void playSoundIfExists(ServerLevel level, double x, double y, double z,
                                          String soundId, float volume, float pitch) {
        var key = new ResourceLocation(soundId);
        var opt = BuiltInRegistries.SOUND_EVENT.getOptional(key);
        opt.ifPresent(se -> level.playSound(null, x, y, z, se, SoundSource.PLAYERS, volume, pitch));
    }

    private static void playAwakeningWhispers(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        RandomSource r = level.getRandom();

        for (int i = 0; i < 8; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double dist = 2.0 + r.nextDouble() * 4.0;

            double x = player.getX() + Math.cos(ang) * dist;
            double y = player.getY() + 0.5 + r.nextDouble() * 1.5;
            double z = player.getZ() + Math.sin(ang) * dist;

            playSoundIfExists(level, x, y, z, "minecraft:ambient.cave", 0.7F, 0.6F + r.nextFloat() * 0.3F);
            playSoundIfExists(level, x, y, z, "minecraft:block.sculk_shrieker.shriek", 0.35F, 0.85F + r.nextFloat() * 0.2F);
            playSoundIfExists(level, x, y, z, "minecraft:entity.warden.heartbeat", 0.25F, 0.75F + r.nextFloat() * 0.15F);
        }

        playSoundIfExists(level, player.getX(), player.getY() + 1.0, player.getZ(),
                "minecraft:entity.elder_guardian.curse", 0.45F, 0.9F);
    }

    private static void ensureBladeLockedInMainHand(Player player) {
        var inv = player.getInventory();

        if (!player.getPersistentData().contains(TAG_LOCKED_SLOT)) {
            player.getPersistentData().putInt(TAG_LOCKED_SLOT, inv.selected);
        }

        int locked = player.getPersistentData().getInt(TAG_LOCKED_SLOT);

        if (inv.selected != locked) {
            inv.selected = locked;

            if (player instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetCarriedItemPacket(locked));
            }
        }

        if (inv.getItem(locked).is(ModItems.SETH_BLADE.get())) return;

        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.items.get(i).is(ModItems.SETH_BLADE.get())) {
                ItemStack a = inv.getItem(locked);
                ItemStack b = inv.getItem(i);
                inv.setItem(locked, b);
                inv.setItem(i, a);
                return;
            }
        }

        if (player.getOffhandItem().is(ModItems.SETH_BLADE.get())) {
            ItemStack a = inv.getItem(locked);
            inv.setItem(locked, player.getOffhandItem());
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, a);
        }
    }

    private static boolean handleStuckAndMaybeTeleport(Player player, LivingEntity target) {
        double dist = player.distanceTo(target);
        if (dist <= (STAGE23_MIN_DISTANCE + 1.0)) {
            player.getPersistentData().putInt(TAG_STUCK_TICKS, 0);
            player.getPersistentData().putDouble(TAG_LAST_X, player.getX());
            player.getPersistentData().putDouble(TAG_LAST_Z, player.getZ());
            return false;
        }
        double lastX = player.getPersistentData().getDouble(TAG_LAST_X);
        double lastZ = player.getPersistentData().getDouble(TAG_LAST_Z);

        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        double moved2 = dx * dx + dz * dz;

        int stuck = player.getPersistentData().getInt(TAG_STUCK_TICKS);
        stuck = (moved2 < 0.001) ? (stuck + 1) : 0;

        player.getPersistentData().putInt(TAG_STUCK_TICKS, stuck);
        player.getPersistentData().putDouble(TAG_LAST_X, player.getX());
        player.getPersistentData().putDouble(TAG_LAST_Z, player.getZ());

        if (stuck >= STUCK_TICKS_LIMIT) {
            teleportBehindTarget(player, target);
            player.getPersistentData().putInt(TAG_STUCK_TICKS, 0);
            return true;
        }

        return false;
    }

    private static boolean isUnlocked(ItemStack blade) {
        return blade.getOrCreateTag().getBoolean("seth_unlocked");
    }

    private static int getSacrificeIndex(ItemStack blade) {
        return blade.getOrCreateTag().getInt("seth_sacrifice_index");
    }

    private static String getRequiredSacrificeType(ItemStack blade) {
        var tag = blade.getOrCreateTag();
        int idx = Math.max(0, Math.min(getSacrificeIndex(blade), SACRIFICES_REQUIRED - 1));
        if (tag.contains("seth_sacrifice_target")) tag.remove("seth_sacrifice_target");
        return tag.getString("seth_sacrifice_target");
    }

    private static void scheduleNextPreAttempt(Player p, long now) {
        int delay = INTERVAL_MIN_TICKS + p.getRandom().nextInt(INTERVAL_MAX_TICKS - INTERVAL_MIN_TICKS + 1);
        p.getPersistentData().putLong("seth_pre_next", now + delay);
    }

    private static void scheduleNextHunt(Player p, long now) {
        int delay = INTERVAL_MIN_TICKS + p.getRandom().nextInt(INTERVAL_MAX_TICKS - INTERVAL_MIN_TICKS + 1);
        p.getPersistentData().putLong("seth_hunt_next", now + delay);
    }

    private static void initIfNeeded(Player p, ItemStack blade, long now) {
        if (p.getPersistentData().getLong("seth_pre_next") == 0) scheduleNextPreAttempt(p, now);
        if (p.getPersistentData().getLong("seth_hunt_next") == 0) scheduleNextHunt(p, now);
        if (!isUnlocked(blade)) getRequiredSacrificeType(blade);
    }

    // ====== СОБЫТИЯ ======

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity();
        ItemStack stack = entity.getItem();

        if (!stack.is(ModItems.SETH_BLADE.get())) return;

        Player player = event.getPlayer();

        ItemStack copy = stack.copy();
        entity.discard();
        event.setCanceled(true);

        boolean added = player.getInventory().add(copy);
        if (!added) {
            int slot = player.getInventory().selected;
            ItemStack prev = player.getInventory().getItem(slot);
            player.getInventory().setItem(slot, copy);

            if (!prev.isEmpty() && !prev.is(ModItems.SETH_BLADE.get())) player.drop(prev, true);
        }

        debugLog(player, "Prevented toss(Q) -> returned blade to inventory.");
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack stack = drop.getItem();

            if (stack.is(ModItems.SETH_BLADE.get())) {
                RETURN_ON_RESPAWN.put(player.getUUID(), stack.copy());
                it.remove();
                drop.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        Player newPlayer = event.getEntity();
        ItemStack blade = RETURN_ON_RESPAWN.remove(newPlayer.getUUID());

        if (blade != null && !blade.isEmpty()) {
            newPlayer.getInventory().add(blade);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack blade = findBlade(player);
        if (blade.isEmpty()) return;

        player.getPersistentData().putDouble("seth_bonus_damage", 0D);
        player.getPersistentData().putDouble("seth_bonus_health", 0D);

        clearPreState(player);
        clearHuntState(player);

        long now = player.level().getGameTime();
        scheduleNextPreAttempt(player, now);
        scheduleNextHunt(player, now);

        debugLog(player, "Owner died -> power reset, states cleared, timers rescheduled.");
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        if (!player.getPersistentData().getBoolean("seth_hunt_active")) return;

        LivingEntity src = (event.getSource().getEntity() instanceof LivingEntity le) ? le : null;
        if (src == null) return;

        String targetStr = player.getPersistentData().getString("seth_hunt_target");
        if (targetStr.isEmpty()) return;

        if (src.getUUID().toString().equals(targetStr) && event.getAmount() > 0) {
            long now = player.level().getGameTime();
            player.getPersistentData().putLong("seth_control_pause_until", now + CONTROL_PAUSE_TICKS);
            debugLog(player, "Took counter-damage from target -> control paused for 2s.");
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (killer.level().isClientSide()) return;

        ItemStack blade = killer.getMainHandItem();
        if (!blade.is(ModItems.SETH_BLADE.get())) return;

        LivingEntity dead = event.getEntity();

        if (!isUnlocked(blade)) {
            String preTarget = killer.getPersistentData().getString("seth_pre_target");
            boolean matchesTarget = !preTarget.isEmpty() && dead.getUUID().toString().equals(preTarget);

            if (matchesTarget) acceptSacrifice(killer, blade);
            return;
        }

        growPower(killer, blade);

        var tag = blade.getOrCreateTag();
        tag.putInt("seth_total_kills", tag.getInt("seth_total_kills") + 1);

        if (killer.getPersistentData().getBoolean("seth_hunt_active")) {
            String t = killer.getPersistentData().getString("seth_hunt_target");
            if (!t.isEmpty() && dead.getUUID().toString().equals(t)) {
                endHunt(killer, killer.level().getGameTime(), true);
            }
        }
    }

    // ====== ОСНОВНОЙ ТИК ======
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;

        ItemStack blade = findBlade(player);
        if (blade.isEmpty()) return;

        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        initIfNeeded(player, blade, now);

        updatePowerModifiersOptimized(player, data);

        if (!isUnlocked(blade)) {
            tickPreUnlock(player, blade, now, data);
        } else {
            tickUnlockedHunt(player, blade, now, data);
        }

        boolean holding = isHoldingBlade(player);
        boolean wasHolding = data.getBoolean(TAG_WAS_HOLDING_BLADE);

        var btag = blade.getOrCreateTag();

        if (holding && !wasHolding && !btag.getBoolean(TAG_BLADE_AWAKEN_SOUNDS_DONE)) {
            playAwakeningWhispers(player);
            btag.putBoolean(TAG_BLADE_AWAKEN_SOUNDS_DONE, true);
        }

        if (holding && !wasHolding && btag.getLong(TAG_BLADE_SETEPAI_AT) == 0 && !btag.getBoolean(TAG_BLADE_SETEPAI_DONE)) {
            btag.putLong(TAG_BLADE_SETEPAI_AT, now + SETEPAI_DELAY_TICKS);
        }

        long at = btag.getLong(TAG_BLADE_SETEPAI_AT);
        if (at > 0 && !btag.getBoolean(TAG_BLADE_SETEPAI_DONE) && now >= at) {
            if (player instanceof ServerPlayer sp) {
                sp.playNotifySound(ModSounds.SETH_SETEPAI.get(), SoundSource.PLAYERS, 15.0F, 0.9F);
            }
            btag.putBoolean(TAG_BLADE_SETEPAI_DONE, true);
            btag.putLong(TAG_BLADE_SETEPAI_AT, 0);
        }

        data.putBoolean(TAG_WAS_HOLDING_BLADE, holding);
    }

    // ===================== ДО РАЗБЛОКИРОВКИ =====================

    private static void tickPreUnlock(Player player, ItemStack blade, long now, CompoundTag data) {
        long next = data.getLong("seth_pre_next");

        if (!data.getBoolean("seth_pre_active")) {
            debugActionbar(player, "Sealed. Next demand in " + Math.max(0, (next - now) / 20) + "s");

            if (now < next) return;

            LivingEntity target = pickTargetInFov(player, 50.0, e -> true);

            if (target == null) {
                scheduleNextPreAttempt(player, now);
                debugLog(player, "Pre-unlock demand: no valid target in FOV -> retry later.");
                return;
            }

            data.putBoolean("seth_pre_active", true);
            data.putInt(TAG_PRE_PULL_LAST_STEP, 0);
            data.putString("seth_pre_target", target.getUUID().toString());
            data.putLong("seth_pre_last_anger", now);

            int idx = getSacrificeIndex(blade);
            debugLog(player, "Pre-unlock demand started. (" + idx + "/5) Target=" + target.getName().getString());
            player.displayClientMessage(Component.literal("§4Seth demands a kill: §f" + target.getName().getString() + " §7(" + idx + "/5)"), true);

            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 15, 0, false, false, true));
            return;
        }

        LivingEntity target = getCachedTarget(player, "seth_pre_target", 128.0, now);
        if (target == null || !target.isAlive()) {
            debugLog(player, "Pre-unlock demand target missing -> canceled, rescheduled.");
            clearPreState(player);
            scheduleNextPreAttempt(player, now);
            return;
        }

        if (player.tickCount % 20 == 0) {
            if (!target.hasEffect(MobEffects.GLOWING)) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
            }
        }

        double dist = player.distanceTo(target);
        debugActionbar(player, "Sealed. Demand active. TargetDist=" + (int) dist + " blocks");

        if (dist > PRE_PULL_START_DISTANCE && dist <= PRE_ESCAPE_DISTANCE) {
            int step = (int) Math.floor(dist / PRE_TUG_STEP);
            int last = data.getInt(TAG_PRE_PULL_LAST_STEP);

            if (step > last) {
                pullToTarget(player, target, PRE_TUG_STRENGTH);
                data.putInt(TAG_PRE_PULL_LAST_STEP, step);
            }

            if (step < last) {
                data.putInt(TAG_PRE_PULL_LAST_STEP, step);
            }
        }

        if (dist > PRE_ESCAPE_DISTANCE) {
            player.displayClientMessage(Component.literal("§7You escaped Seth's demand... for now."), true);
            debugLog(player, "Pre-unlock: player escaped (dist " + dist + ") -> canceled, rescheduled.");
            clearPreState(player);
            scheduleNextPreAttempt(player, now);
            return;
        }

        boolean obsessionActive = false;

        if (dist <= PRE_ACTIVE_DISTANCE) {
            obsessionActive = true;

            if (player.tickCount % 40 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, false, true));
            }

            long lastAnger = data.getLong("seth_pre_last_anger");
            if (now - lastAnger > PRE_ANGER_MIN_TICKS) {
                if (player.getRandom().nextFloat() < 0.25f) {
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 12, 4, false, false, true));
                    debugLog(player, "Pre-unlock: Seth anger -> levitation applied.");
                }
                data.putLong("seth_pre_last_anger", now);
            }
        }

        applyObsessionBuffsOptimized(player, obsessionActive, data);
    }

    private static void acceptSacrifice(Player player, ItemStack blade) {
        var tag = blade.getOrCreateTag();
        int idx = tag.getInt("seth_sacrifice_index") + 1;
        tag.putInt("seth_sacrifice_index", idx);

        clearPreState(player);

        debugLog(player, "Sacrifice accepted. NewIndex=" + idx + "/5");

        if (idx >= SACRIFICES_REQUIRED) {
            tag.putBoolean("seth_unlocked", true);
            tag.remove("seth_sacrifice_target");

            player.displayClientMessage(Component.literal("§cSeth accepts your blood. The blade awakens."), false);

            long now = player.level().getGameTime();
            scheduleNextHunt(player, now);
            debugLog(player, "Unlocked! Next hunt scheduled.");
        } else {
            if (tag.contains("seth_sacrifice_target")) tag.remove("seth_sacrifice_target");
            player.displayClientMessage(
                    Component.literal("§4Sacrifice accepted. Progress: §f" + idx + "/" + SACRIFICES_REQUIRED),
                    true
            );

            scheduleNextPreAttempt(player, player.level().getGameTime());
        }

        if (blade.getItem() instanceof SethBladeItem relic) relic.spreadExperience(player, blade, 1);
    }

    private static void clearPreState(Player p) {
        CompoundTag data = p.getPersistentData();
        data.putBoolean("seth_pre_active", false);
        data.remove("seth_pre_target");
        data.remove("seth_pre_last_anger");
        data.remove(TAG_PRE_PULL_LAST_STEP);
        data.remove(TAG_CACHED_TARGET_ID);
        data.remove(TAG_CACHED_TARGET_TICK);

        applyObsessionBuffsOptimized(p, false, data);
    }

    // ===================== ПОСЛЕ РАЗБЛОКИРОВКИ =====================

    private static void tickUnlockedHunt(Player player, ItemStack blade, long now, CompoundTag data) {
        if (!data.getBoolean("seth_hunt_active")) {
            long next = data.getLong("seth_hunt_next");
            debugActionbar(player, "Awakened. Next hunt in " + Math.max(0, (next - now) / 20) + "s");

            if (now < next) return;

            LivingEntity target = pickTargetInFov(player, 50.0, e -> true);

            if (target == null) {
                scheduleNextHunt(player, now);
                debugLog(player, "Hunt: no target in FOV -> retry later.");
                return;
            }

            startHunt(player, target, now);
            return;
        }

        LivingEntity target = getCachedTarget(player, "seth_hunt_target", 128.0, now);
        if (target == null || !target.isAlive()) {
            debugLog(player, "Hunt target missing -> ending hunt.");
            endHunt(player, now, true);
            return;
        }

        if (player.tickCount % 20 == 0) {
            if (!target.hasEffect(MobEffects.GLOWING)) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
            }
        }

        long start = data.getLong("seth_hunt_start");
        long elapsed = now - start;

        if (elapsed >= HUNT_TOTAL_TICKS) {
            debugLog(player, "Hunt timed out (5min) -> ending hunt.");
            endHunt(player, now, true);
            return;
        }

        int stage = elapsed < STAGE1_END ? 1 : (elapsed < STAGE2_END ? 2 : 3);
        int lastStage = data.getInt("seth_hunt_stage");
        if (lastStage != stage) {
            data.putInt("seth_hunt_stage", stage);
            debugLog(player, "Hunt stage changed -> " + stage);
        }

        long pauseUntil = data.getLong("seth_control_pause_until");
        double dist = player.distanceTo(target);

        debugActionbar(player, "Hunt: stage=" + stage + " dist=" + (int) dist + " pause=" + Math.max(0, (pauseUntil - now) / 20) + "s");

        if (stage == 1) {
            if (!data.getBoolean(TAG_STAGE1_TP_DONE)) {
                teleportBehindTarget(player, target);
                data.putBoolean(TAG_STAGE1_TP_DONE, true);
            }

            if (dist > PRE_PULL_START_DISTANCE) startPullLock(player, now);

            if (isPullLocked(player, now)) {
                strongPullLockTick(player, target);
                applyObsessionBuffsOptimized(player, true, data);
            } else {
                applyObsessionBuffsOptimized(player, false, data);
            }

            if (dist > STAGE1_MAX_DISTANCE) {
                startPullLock(player, now);
            }

            return;
        }

        if (stage == 2) {
            ensureBladeLockedInMainHand(player);

            if (handleStuckAndMaybeTeleport(player, target)) return;

            dist = player.distanceTo(target);

            if (dist > TELEPORT_IF_FAR) {
                teleportBehindTarget(player, target);
                return;
            }

            leashToTarget(player, target, STAGE2_MAX_DISTANCE);

            if (dist > STAGE23_MIN_DISTANCE) {
                pullToTarget(player, target, 0.10);
            }

            return;
        }

        ensureBladeLockedInMainHand(player);

        if (handleStuckAndMaybeTeleport(player, target)) return;

        dist = player.distanceTo(target);

        if (dist > TELEPORT_IF_FAR) {
            teleportBehindTarget(player, target);
            return;
        }

        if (now < pauseUntil) {
            leashToTarget(player, target, STAGE2_MAX_DISTANCE);
            return;
        }

        hardLockCameraToTarget(player, target);

        if (dist > STAGE23_MIN_DISTANCE) {
            pullToTarget(player, target, 0.22);
        }
    }

    private static void startPullLock(Player player, long now) {
        player.getPersistentData().putLong(TAG_PULL_UNTIL, now + PULL_LOCK_TICKS);
    }

    private static boolean isPullLocked(Player player, long now) {
        return now < player.getPersistentData().getLong(TAG_PULL_UNTIL);
    }

    private static void forceLookAt(Player player, LivingEntity target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 d = to.subtract(from);

        double dx = d.x, dy = d.y, dz = d.z;
        double h = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, h)));

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    private static void strongPullLockTick(Player player, LivingEntity target) {
        forceLookAt(player, target);

        player.setSprinting(false);

        if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 10, false, false, true));
        }
        if (!player.hasEffect(MobEffects.JUMP)) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 10, 200, false, false, true));
        }

        Vec3 dir = target.position().subtract(player.position()).normalize();
        player.setDeltaMovement(dir.scale(AWAKEN_PULL_STRENGTH));
        player.hurtMarked = true;
    }

    private static void startHunt(Player player, LivingEntity target, long now) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean("seth_hunt_active", true);
        data.putLong("seth_hunt_start", now);
        data.putString("seth_hunt_target", target.getUUID().toString());
        data.putInt("seth_hunt_stage", 1);
        data.remove(TAG_STAGE1_TP_DONE);
        data.remove(TAG_LOCKED_SLOT);
        data.putLong("seth_control_pause_until", 0);

        debugLog(player, "Hunt started. Target=" + target.getName().getString());
        player.displayClientMessage(Component.literal("§4Seth demands blood: §f" + target.getName().getString()), true);

        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, false, true));
    }

    private static void endHunt(Player player, long now, boolean reschedule) {
        clearHuntState(player);

        player.displayClientMessage(Component.literal("§7Seth releases you... for now."), true);
        debugLog(player, "Hunt ended. Reschedule=" + reschedule);

        if (reschedule) scheduleNextHunt(player, now);
    }

    private static void clearHuntState(Player p) {
        CompoundTag data = p.getPersistentData();
        data.putBoolean("seth_hunt_active", false);
        data.remove("seth_hunt_target");
        data.remove("seth_hunt_start");
        data.remove("seth_hunt_stage");
        data.remove(TAG_STAGE1_TP_DONE);
        data.remove(TAG_LOCKED_SLOT);
        data.remove(TAG_PULL_UNTIL);
        data.remove(TAG_STUCK_TICKS);
        data.remove(TAG_LAST_X);
        data.remove(TAG_LAST_Z);
        data.remove("seth_control_pause_until");
        data.remove(TAG_CACHED_TARGET_ID);
        data.remove(TAG_CACHED_TARGET_TICK);

        applyObsessionBuffsOptimized(p, false, data);
    }

    private static void pullToTarget(Player player, LivingEntity target, double strength) {
        Vec3 dir = target.position().subtract(player.position()).normalize();
        Vec3 pull = dir.scale(strength);
        player.setDeltaMovement(player.getDeltaMovement().add(pull));
        player.hurtMarked = true;
    }

    private static void leashToTarget(Player player, LivingEntity target, double maxDist) {
        double dist = player.distanceTo(target);
        if (dist > maxDist) {
            Vec3 dir = target.position().subtract(player.position()).normalize();
            Vec3 newPos = target.position().subtract(dir.scale(maxDist));
            player.teleportTo(newPos.x, newPos.y, newPos.z);
        }
    }

    // ====== МОДИФИКАТОРЫ СИЛЫ ======

    private static void growPower(Player player, ItemStack blade) {
        double current = player.getPersistentData().getDouble("seth_bonus_damage");
        double newVal = Math.min(10.0, current + 0.1);
        player.getPersistentData().putDouble("seth_bonus_damage", newVal);

        double hp = player.getPersistentData().getDouble("seth_bonus_health");
        double newHp = Math.min(20.0, hp + 0.2);
        player.getPersistentData().putDouble("seth_bonus_health", newHp);

        if (blade.getItem() instanceof SethBladeItem relic) relic.spreadExperience(player, blade, 1);

        debugLog(player, "Power grew -> damage=" + newVal + ", health=" + newHp);
    }

    private static void updatePowerModifiersOptimized(Player player, CompoundTag data) {
        double dmg = data.getDouble("seth_bonus_damage");
        double hp = data.getDouble("seth_bonus_health");

        AttributeInstance attrDmg = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrDmg != null) {
            AttributeModifier existing = attrDmg.getModifier(SETH_DAMAGE_UUID);
            if (existing == null || Math.abs(existing.getAmount() - dmg) > 0.01) {
                attrDmg.removeModifier(SETH_DAMAGE_UUID);
                if (dmg > 0) {
                    attrDmg.addPermanentModifier(
                            new AttributeModifier(SETH_DAMAGE_UUID, "seth_bonus_damage",
                                    dmg, AttributeModifier.Operation.ADDITION));
                }
            }
        }

        AttributeInstance attrHp = player.getAttribute(Attributes.MAX_HEALTH);
        if (attrHp != null) {
            AttributeModifier existing = attrHp.getModifier(SETH_HEALTH_UUID);
            if (existing == null || Math.abs(existing.getAmount() - hp) > 0.01) {
                attrHp.removeModifier(SETH_HEALTH_UUID);
                if (hp > 0) {
                    attrHp.addPermanentModifier(
                            new AttributeModifier(SETH_HEALTH_UUID, "seth_bonus_health",
                                    hp, AttributeModifier.Operation.ADDITION));
                }
            }
        }
    }

    private static void applyObsessionBuffsOptimized(Player player, boolean enabled, CompoundTag data) {
        double dmgMult = enabled ? 0.20 : 0.0;
        double asMult = enabled ? 0.30 : 0.0;

        AttributeInstance attrDmg = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrDmg != null) {
            AttributeModifier existing = attrDmg.getModifier(SETH_OBSESSION_DMG_MULT_UUID);
            if (existing == null || Math.abs(existing.getAmount() - dmgMult) > 0.01) {
                attrDmg.removeModifier(SETH_OBSESSION_DMG_MULT_UUID);
                if (dmgMult > 0) {
                    attrDmg.addPermanentModifier(
                            new AttributeModifier(SETH_OBSESSION_DMG_MULT_UUID, "seth_obsession_dmg",
                                    dmgMult, AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
        }

        AttributeInstance attrAS = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attrAS != null) {
            AttributeModifier existing = attrAS.getModifier(SETH_OBSESSION_AS_MULT_UUID);
            if (existing == null || Math.abs(existing.getAmount() - asMult) > 0.01) {
                attrAS.removeModifier(SETH_OBSESSION_AS_MULT_UUID);
                if (asMult > 0) {
                    attrAS.addPermanentModifier(
                            new AttributeModifier(SETH_OBSESSION_AS_MULT_UUID, "seth_obsession_as",
                                    asMult, AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
        }
    }
}
