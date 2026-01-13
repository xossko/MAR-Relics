package com.vladisss.marrelics;

import com.vladisss.marrelics.commands.SethBladeCommands;
import com.vladisss.marrelics.config.ShieldRingConfig;
import com.vladisss.marrelics.registry.ModItems;
import com.vladisss.marrelics.event.ShieldRingEventHandler;
import com.vladisss.marrelics.event.DevourEventHandler;
import com.vladisss.marrelics.event.ApostleOfDecayEventHandler;
import com.vladisss.marrelics.registry.ModSounds;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.vladisss.marrelics.event.BloodGrimoireEventHandler;
import org.slf4j.LoggerFactory;

@Mod(MARRelicsMod.MOD_ID)
public class MARRelicsMod {
    public static final String MOD_ID = "marrelics";
    public static final String MODID = MOD_ID; // Алиас для совместимости
    public static final Logger LOGGER = LoggerFactory.getLogger(MARRelicsMod.class);

    public MARRelicsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрация предметов
        ModItems.ITEMS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        // Регистрация конфигураций
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShieldRingConfig.SPEC);

        // Регистрация команд (используем FORGE event bus, не MOD event bus)
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);

        // Event handlers регистрируются автоматически через @Mod.EventBusSubscriber

        LOGGER.info("MAR Relics (Magic And RPG Relics) initialized with Mixins support!");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SethBladeCommands.register(event.getDispatcher());
    }
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.vladisss.marrelics.network.ModNetwork.register());
    }
}
