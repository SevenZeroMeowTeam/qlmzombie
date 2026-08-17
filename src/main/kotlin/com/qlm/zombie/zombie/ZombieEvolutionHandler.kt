package com.qlm.zombie.zombie

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.dayphase.DayPhaseManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Zombie
import net.minecraftforge.event.entity.living.MobSpawnEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.api.distmarker.Dist

@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = [Dist.DEDICATED_SERVER]
)
object ZombieEvolutionHandler {

    private val ZOMBIE_HEALTH_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_health_bonus")
    private val ZOMBIE_DAMAGE_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_damage_bonus")
    private val ZOMBIE_SPEED_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_speed_bonus")
    private val ZOMBIE_ARMOR_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "zombie_armor_bonus")

    /** 僵尸攻击力随天数增强起始天数（第 25 天后开始增长） */
    private const val ZOMBIE_ATTACK_SCALE_START_DAY = 25

    /** 每过一天攻击力增量（+1.5%/天，无上限） */
    private const val ZOMBIE_ATTACK_PER_DAY = 0.015

    @JvmStatic
    @SubscribeEvent
    fun onZombieSpawn(event: MobSpawnEvent.FinalizeSpawn) {
        val zombie = event.entity as? Zombie ?: return
        val level = zombie.level()
        if (level.isClientSide) return

        // 僵尸固定 +2 护甲（原为 EntityAttributeCreationEvent 注册基础属性，
        // 但原版僵尸已注册默认属性会抛 Duplicate DefaultAttributes 崩溃，
        // 改为生成时附加永久修饰符，效果等价）
        val armorAttribute = zombie.getAttribute(Attributes.ARMOR)
        if (armorAttribute != null) {
            val armorModifier = AttributeModifier(
                ZOMBIE_ARMOR_MODIFIER_ID.toString(),
                2.0,
                AttributeModifier.Operation.ADDITION
            )
            armorAttribute.addPermanentModifier(armorModifier)
        }

        val phase = DayPhaseManager.getCurrentPhase()
        val multiplier = phase.difficultyMultiplier

        // 僵尸攻击力随天数线性增强：第 25 天后每过一天 +1.5%（无上限）
        // 第25天 1.0x → 第50天 1.375x → 第76天 1.765x → 第100天 2.125x → 第200天 3.625x
        val day = DayPhaseManager.getCurrentDay().toInt()
        val dayAttackMult = 1.0 +
            maxOf(0.0, (day - ZOMBIE_ATTACK_SCALE_START_DAY).toDouble()) * ZOMBIE_ATTACK_PER_DAY

        if (multiplier <= 1.0 && dayAttackMult <= 1.0) return

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

        // 攻击力：随天数增强（替代原阶段倍率，更平滑且持续增长到后期）
        val damageAttribute = zombie.getAttribute(Attributes.ATTACK_DAMAGE)
        if (damageAttribute != null && dayAttackMult > 1.0) {
            val damageBonus = damageAttribute.baseValue * (dayAttackMult - 1.0)
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
            "[僵尸进化] 僵尸生成于 {} 阶段, 难度乘数: {}x, 攻击倍率: {}x, 生命: {}, 伤害: {}",
            phase.name,
            multiplier,
            dayAttackMult,
            zombie.maxHealth,
            zombie.getAttributeValue(Attributes.ATTACK_DAMAGE)
        )
    }
}