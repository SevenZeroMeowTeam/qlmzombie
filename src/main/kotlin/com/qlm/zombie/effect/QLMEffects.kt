package com.qlm.zombie.effect

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object QLMEffects {
    private val EFFECTS = DeferredRegister.create(ForgeRegistries.Keys.MOB_EFFECTS, QLMZombieMod.MOD_ID)

    val CUSTOM_THIRST: RegistryObject<MobEffect> = EFFECTS.register("custom_thirst") {
        CustomThirstEffect()
    }

    fun register(eventBus: IEventBus) {
        EFFECTS.register(eventBus)
    }
}

class CustomThirstEffect : MobEffect(MobEffectCategory.HARMFUL, 0x8B4513) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
    }

    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean {
        return false
    }
}