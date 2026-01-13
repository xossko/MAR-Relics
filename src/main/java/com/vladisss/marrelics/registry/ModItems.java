package com.vladisss.marrelics.registry;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import com.vladisss.marrelics.items.WarHelmetItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import com.vladisss.marrelics.items.BloodGrimoireItem;
import net.minecraftforge.registries.RegistryObject;
import com.vladisss.marrelics.items.SethBladeItem;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MARRelicsMod.MOD_ID);

    public static final RegistryObject<Item> BLOOD_SHIELD_RING =
            ITEMS.register("blood_shield_ring", ShieldRingItem::new);

    public static final RegistryObject<Item> APOSTLE_OF_DECAY =
            ITEMS.register("apostle_of_decay", ApostleOfDecayItem::new);

    public static final RegistryObject<Item> HEART_OF_TARRASQUE =
            ITEMS.register("heart_of_tarrasque", HeartOfTarrasqueItem::new);

    public static final RegistryObject<Item> SALTED_SHIV = ITEMS.register("salted_shiv",
            SaltedShiv::new);
    // Добавьте эту строку в ваш класс ModItems
    public static final RegistryObject<Item> BLOOD_RAPIER = ITEMS.register("blood_rapier",
            BloodRapierItem::new);

    public static final RegistryObject<Item> MOONLIGHT =
            ITEMS.register("moonlight", MoonLightItem::new);

    public static final RegistryObject<Item> WAR_HELMET =
            ITEMS.register("war_helmet", WarHelmetItem::new);
    public static final RegistryObject<Item> DEMONIC_CROWN = ITEMS.register("demonic_crown", DemonicCrownItem::new);

    public static final RegistryObject<Item> BLOODGRIMOIRE =
            ITEMS.register("bloodgrimoire", BloodGrimoireItem::new);
    public static final RegistryObject<Item> SETH_BLADE =
            ITEMS.register("seth_blade", SethBladeItem::new);
    public static final RegistryObject<Item> MANA_FLUX_VEIL =
            ITEMS.register("mana_flux_veil", ManaFluxVeilItem::new);
}
