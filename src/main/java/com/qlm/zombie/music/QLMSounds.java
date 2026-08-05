package com.qlm.zombie.music;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class QLMSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, QLMZombieMod.MOD_ID);

    public static final RegistryObject<SoundEvent> EPIC_MAIN_THEME =
            registerSound("music.epic_main_theme");

    public static final RegistryObject<SoundEvent> BLOOD_MOON_RISING =
            registerSound("music.blood_moon_rising");

    public static final RegistryObject<SoundEvent> BLOOD_MOON_BATTLE =
            registerSound("music.blood_moon_battle");

    public static final RegistryObject<SoundEvent> ADVENTURE_OVERTURE =
            registerSound("music.adventure_overture");

    public static final RegistryObject<SoundEvent> BOSS_PHASE_1 =
            registerSound("music.boss_phase_1");

    public static final RegistryObject<SoundEvent> BOSS_PHASE_2 =
            registerSound("music.boss_phase_2");

    public static final RegistryObject<SoundEvent> BOSS_PHASE_3 =
            registerSound("music.boss_phase_3");

    public static final RegistryObject<SoundEvent> HORDE_AMBIENT =
            registerSound("music.horde_ambient");

    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(QLMZombieMod.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}