package com.vladisss.marrelics.event;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MARRelicsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ManaShieldSyncEvents {

    private static final String TAG = "manafluxshieldenabled"; // твой реальный ключ [file:27]

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;

        boolean enabled = target.getPersistentData().getBoolean(TAG);
        ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> tracker),
                new com.vladisss.marrelics.network.packet.ManaShieldStateS2CPacket(target.getUUID(), enabled)
        );
    }
}
