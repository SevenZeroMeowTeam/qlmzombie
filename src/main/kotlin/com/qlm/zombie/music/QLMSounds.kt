package com.qlm.zombie.music

import com.qlm.zombie.QLMZombieMod
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

object QLMSounds {
    private val SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, QLMZombieMod.MOD_ID)

    fun register(eventBus: IEventBus) {
        SOUND_EVENTS.register(eventBus)
    }
}