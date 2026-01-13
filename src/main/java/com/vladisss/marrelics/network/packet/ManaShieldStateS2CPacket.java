package com.vladisss.marrelics.network.packet;

import com.vladisss.marrelics.client.ManaShieldClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ManaShieldStateS2CPacket(UUID playerId, boolean enabled) {

    public static void encode(ManaShieldStateS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId());
        buf.writeBoolean(msg.enabled());
    }

    public static ManaShieldStateS2CPacket decode(FriendlyByteBuf buf) {
        return new ManaShieldStateS2CPacket(buf.readUUID(), buf.readBoolean());
    }

    // ВАЖНО: второй аргумент Supplier<Context>, а не Context
    public static void handle(ManaShieldStateS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ManaShieldClientState.set(msg.playerId(), msg.enabled());
        });
        ctx.get().setPacketHandled(true);
    }
}
