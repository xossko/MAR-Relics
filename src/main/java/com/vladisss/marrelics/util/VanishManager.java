package com.vladisss.marrelics.util;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VanishManager {
    private static final Set<UUID> VANISHED_PLAYERS = new HashSet<>();

    public static void setVanished(Player player, boolean vanished) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerId = player.getUUID();

        if (vanished) {
            if (VANISHED_PLAYERS.add(playerId)) {
                hidePlayerFromOthers(serverPlayer);
                hideArmorForSelf(serverPlayer); // НОВОЕ
            }
        } else {
            if (VANISHED_PLAYERS.remove(playerId)) {
                showPlayerToOthers(serverPlayer);
                showArmorForSelf(serverPlayer); // НОВОЕ
            }
        }
    }

    public static boolean isVanished(Player player) {
        return VANISHED_PLAYERS.contains(player.getUUID());
    }

    private static void hidePlayerFromOthers(ServerPlayer vanishedPlayer) {
        List<ServerPlayer> players = vanishedPlayer.getServer().getPlayerList().getPlayers();

        for (ServerPlayer otherPlayer : players) {
            if (otherPlayer.getUUID().equals(vanishedPlayer.getUUID())) {
                continue; // Не скрываем от самого себя через пакеты
            }

            // Удаляем сущность из видимости
            ClientboundRemoveEntitiesPacket removePacket =
                    new ClientboundRemoveEntitiesPacket(vanishedPlayer.getId());
            otherPlayer.connection.send(removePacket);
        }
    }

    private static void showPlayerToOthers(ServerPlayer player) {
        List<ServerPlayer> players = player.getServer().getPlayerList().getPlayers();

        for (ServerPlayer otherPlayer : players) {
            if (otherPlayer.getUUID().equals(player.getUUID())) {
                continue;
            }

            // Повторно добавляем игрока через трекинг
            otherPlayer.connection.teleport(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        // Обновляем табличку
        player.getServer().getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        player
                )
        );
    }

    // НОВОЕ: Скрыть броню для самого игрока
    private static void hideArmorForSelf(ServerPlayer player) {
        // Максимальный уровень невидимости - скрывает ВСЁ (броню, предметы, частицы)
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                Integer.MAX_VALUE, // Бесконечная длительность
                255,               // Максимальный уровень
                false,             // Не ambient
                false,             // Не показывать частицы
                false              // Не показывать иконку
        ));

        player.setInvisible(true);
        player.getPersistentData().putBoolean("marrelics_vanish_self", true);
    }

    // НОВОЕ: Показать броню для самого игрока
    private static void showArmorForSelf(ServerPlayer player) {
        if (player.getPersistentData().getBoolean("marrelics_vanish_self")) {
            player.removeEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            player.getPersistentData().remove("marrelics_vanish_self");
        }
    }

    public static void onPlayerJoin(ServerPlayer joiningPlayer) {
        List<ServerPlayer> players = joiningPlayer.getServer().getPlayerList().getPlayers();

        for (ServerPlayer player : players) {
            if (VANISHED_PLAYERS.contains(player.getUUID()) &&
                    !player.getUUID().equals(joiningPlayer.getUUID())) {

                // Скрываем ванишнутых игроков от нового игрока
                ClientboundRemoveEntitiesPacket removePacket =
                        new ClientboundRemoveEntitiesPacket(player.getId());
                joiningPlayer.connection.send(removePacket);
            }
        }
    }
}
