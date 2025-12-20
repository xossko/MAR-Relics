package com.vladisss.marrelics.registry;

import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.items.ShieldRingItem;
import com.vladisss.marrelics.items.ApostleOfDecayItem;
import com.vladisss.marrelics.items.HeartOfTarrasqueItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MARRelicsMod.MOD_ID);

    public static final RegistryObject<Item> BLOOD_SHIELD_RING =
            ITEMS.register("blood_shield_ring", ShieldRingItem::new);

    public static final RegistryObject<Item> APOSTLE_OF_DECAY =
            ITEMS.register("apostle_of_decay", ApostleOfDecayItem::new);

    public static final RegistryObject<Item> HEART_OF_TARRASQUE =
            ITEMS.register("heart_of_tarrasque", HeartOfTarrasqueItem::new);
}
