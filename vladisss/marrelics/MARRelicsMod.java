package com.vladisss.marrelics;

import com.vladisss.marrelics.config.ShieldRingConfig;
import com.vladisss.marrelics.registry.ModItems;
import com.vladisss.marrelics.event.ShieldRingEventHandler;
import com.vladisss.marrelics.event.DevourEventHandler;
import com.vladisss.marrelics.event.ApostleOfDecayEventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MARRelicsMod.MOD_ID)
public class MARRelicsMod {
    public static final String MOD_ID = "marrelics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MARRelicsMod.class);

    public MARRelicsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрация предметов
        ModItems.ITEMS.register(modEventBus);

        // Регистрация конфигураций
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShieldRingConfig.SPEC);
        // УДАЛИ: ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ApostleOfDecayConfig.SPEC, "marrelics-apostle.toml");

        // Event handlers регистрируются автоматически через @Mod.EventBusSubscriber

        LOGGER.info("MAR Relics (Magic And RPG Relics) initialized!");
    }
}
