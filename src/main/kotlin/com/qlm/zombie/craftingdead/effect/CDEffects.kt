package com.qlm.zombie.craftingdead.effect

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object CDEffects {
    private val EFFECTS = DeferredRegister.create(ForgeRegistries.Keys.MOB_EFFECTS, QLMZombieMod.MOD_ID)

    @JvmField
    val BLEEDING: RegistryObject<MobEffect> = EFFECTS.register("bleeding") {
        BleedingEffect()
    }

    @JvmField
    val FRACTURE: RegistryObject<MobEffect> = EFFECTS.register("fracture") {
        FractureEffect()
    }

    @JvmField
    val ADRENALINE: RegistryObject<MobEffect> = EFFECTS.register("adrenaline") {
        AdrenalineEffect()
    }

    @JvmField
    val PAINKILLER: RegistryObject<MobEffect> = EFFECTS.register("painkiller") {
        PainkillerEffect()
    }

    @JvmField
    val SEVERE_INFECTION: RegistryObject<MobEffect> = EFFECTS.register("severe_infection") {
        SevereInfectionEffect()
    }

    // Java-compatible aliases for legacy naming
    @JvmField
    val BROKEN_BONE: RegistryObject<MobEffect> = FRACTURE

    @JvmField
    val PAIN_SUPPRESSION: RegistryObject<MobEffect> = PAINKILLER

    @JvmField
    val ADRENALINE_RUSH: RegistryObject<MobEffect> = ADRENALINE

    fun register(eventBus: IEventBus) {
        EFFECTS.register(eventBus)
    }
}

class BleedingEffect : MobEffect(MobEffectCategory.HARMFUL, 0xAA0000) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
        entity.hurt(entity.damageSources().magic(), 1.0f + amplifier * 0.5f)
    }

    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean {
        return duration % 40 == 0
    }
}

class FractureEffect : MobEffect(MobEffectCategory.HARMFUL, 0x8B7355) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
    }

    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean {
        return false
    }

    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            FRACTURE_SLOWDOWN_MODIFIER.toString(),
            -0.15,
            AttributeModifier.Operation.MULTIPLY_BASE
        )
    }

    companion object {
        private val FRACTURE_SLOWDOWN_MODIFIER = java.util.UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567890")
    }
}

class AdrenalineEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0xFF0000) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
    }

    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean {
        return false
    }

    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ADRENALINE_SPEED_MODIFIER.toString(),
            0.2,
            AttributeModifier.Operation.MULTIPLY_BASE
        )
        addAttributeModifier(
            Attributes.ATTACK_DAMAGE,
            ADRENALINE_DAMAGE_MODIFIER.toString(),
            0.3,
            AttributeModifier.Operation.MULTIPLY_BASE
        )
    }

    companion object {
        private val ADRENALINE_SPEED_MODIFIER = java.util.UUID.fromString("B2C3D4E5-F6A7-8901-BCDE-F12345678901")
        private val ADRENALINE_DAMAGE_MODIFIER = java.util.UUID.fromString("C3D4E5F6-A7B8-9012-CDEF-123456789012")
    }
}

class PainkillerEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0x0000FF) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
    }

    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean {
        return false
    }

    companion object {
    }
}

class SevereInfectionEffect : MobEffect(MobEffectCategory.HARMFUL, 0x006400) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
        entity.hurt(entity.damageSources().magic(), 1.0f + amplifier * 0.3f)
    }

    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean {
        return duration % 25 == 0
    }
}
