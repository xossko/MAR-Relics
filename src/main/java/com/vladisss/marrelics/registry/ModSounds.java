package com.vladisss.marrelics.registry;

import com.vladisss.marrelics.MARRelicsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;



public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MARRelicsMod.MOD_ID);

    public static final RegistryObject<SoundEvent> SETH_SETEPAI = SOUNDS.register("seth.setepai",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MARRelicsMod.MOD_ID, "seth.setepai")));
}
