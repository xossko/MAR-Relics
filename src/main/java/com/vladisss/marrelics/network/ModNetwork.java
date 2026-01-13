package com.vladisss.marrelics.network;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.network.packet.ManaShieldStateS2CPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MARRelicsMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    ); // типичный способ завести SimpleChannel [web:33]

    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(ManaShieldStateS2CPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ManaShieldStateS2CPacket::encode)
                .decoder(ManaShieldStateS2CPacket::decode)
                .consumerMainThread(ManaShieldStateS2CPacket::handle)
                .add(); // builder-стиль регистрации пакетов [web:33]
    }

    /** Отправить состояние пузыря всем, кто трекает игрока, и самому игроку. */
    public static void sendManaShieldState(ServerPlayer player, boolean enabled) {
        ManaShieldStateS2CPacket pkt = new ManaShieldStateS2CPacket(player.getUUID(), enabled);

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), pkt);
    }
}
