package com.qlm.zombie.zombie

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.dayphase.DayPhaseManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Zombie
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.event.entity.living.MobSpawnEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.api.distmarker.Dist

@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = [Dist.DEDICATED_SERVER]
)
object ZombieAttributeHandler {

    @SubscribeEvent
    fun onEntityAttributeCreation(event: EntityAttributeCreationEvent) {
        event.put(
            net.minecraft.world.entity.EntityType.ZOMBIE,
            Zombie.createAttributes()
                .add(Attributes.ARMOR, 2.0)
                .build()
        )
    }
}

@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = [Dist.DEDICATED_SERVER]
)
object ZombieEvolutionHandler {

    private val ZOMBIE_HEALTH_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_health_bonus")
    private val ZOMBIE_DAMAGE_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_damage_bonus")
    private val ZOMBIE_SPEED_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_speed_bonus")

    @SubscribeEvent
    fun onZombieSpawn(event: MobSpawnEvent.FinalizeSpawn) {
        val zombie = event.entity as? Zombie ?: return
        val level = zombie.level()
        if (level.isClientSide) return

        val phase = DayPhaseManager.getCurrentPhase()
        val multiplier = phase.difficultyMultiplier

        if (multiplier <= 1.0) return

        val healthAttribute = zombie.getAttribute(Attributes.MAX_HEALTH)
        if (healthAttribute != null) {
            val healthBonus = healthAttribute.baseValue * (multiplier - 1.0)
            val healthModifier = AttributeModifier(
                ZOMBIE_HEALTH_MODIFIER_ID.toString(),
                healthBonus,
                AttributeModifier.Operation.ADDITION
            )
            healthAttribute.addPermanentModifier(healthModifier)
            zombie.health = zombie.maxHealth
        }

        val damageAttribute = zombie.getAttribute(Attributes.ATTACK_DAMAGE)
        if (damageAttribute != null) {
            val damageBonus = damageAttribute.baseValue * (multiplier - 1.0)
            val damageModifier = AttributeModifier(
                ZOMBIE_DAMAGE_MODIFIER_ID.toString(),
                damageBonus,
                AttributeModifier.Operation.ADDITION
            )
            damageAttribute.addPermanentModifier(damageModifier)
        }

        if (multiplier >= 2.0) {
            val speedAttribute = zombie.getAttribute(Attributes.MOVEMENT_SPEED)
            if (speedAttribute != null) {
                val speedBonus = 0.02 * (multiplier - 1.0)
                val speedModifier = AttributeModifier(
                    ZOMBIE_SPEED_MODIFIER_ID.toString(),
                    speedBonus,
                    AttributeModifier.Operation.ADDITION
                )
                speedAttribute.addPermanentModifier(speedModifier)
            }
        }

        QLMZombieMod.LOGGER.debug(
            "[僵尸进化] 僵尸生成于 {} 阶段, 难度乘数: {}x, 生命: {}, 伤害: {}",
            phase.name,
            multiplier,
            zombie.maxHealth,
            zombie.getAttributeValue(Attributes.ATTACK_DAMAGE)
        )
    }
}