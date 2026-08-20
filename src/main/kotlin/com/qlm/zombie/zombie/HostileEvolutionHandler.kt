package com.qlm.zombie.zombie

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.dayphase.DayPhaseManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.Zombie
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.entity.living.MobSpawnEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * 敌对生物（非僵尸）随天数增强处理器。
 *
 * <p>所有 {@link Monster} 类型的敌对生物（骷髅/蜘蛛/女巫/苦力怕/末影人/尸壳/溺尸等，以及
 * 其他模组的敌对生物）在生成时，根据服务器当前天数对其属性施加<b>线性增长</b>的永久修饰符：
 * <ul>
 *   <li>生命上限：第 {@code hostileScalingStartDay} 天后，每过一天 +{@code hostileHealthPerDay}（默认 1%/天）</li>
 *   <li>攻击力：第 {@code hostileScalingStartDay} 天后，每过一天 +{@code hostileDamagePerDay}（默认 1.5%/天）</li>
 *   <li>护甲：第 {@code hostileScalingStartDay} 天后，每过一天 +{@code hostileArmorPerDay}（默认 0.05/天）</li>
 *   <li>击退抗性：第 {@code hostileScalingStartDay} 天后，每过一天 +{@code hostileKnockbackResistancePerDay}（默认 0.001/天，上限 0.5）</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li><b>排除 {@link Zombie}</b>：僵尸已有 {@link ZombieEvolutionHandler} 专门处理（含特殊僵尸与阶段/天数逻辑），
 *       避免重复加成。</li>
 *   <li>使用<b>永久修饰符</b>（addPermanentModifier）而非临时修饰符，与 {@link ZombieEvolutionHandler} 保持一致，
 *       且对自然生成/刷怪笼/命令生成的敌对生物均生效。</li>
 *   <li>无攻击力属性（如苦力怕 Creeper）或护甲属性的敌对生物会自动跳过对应属性，不会报错。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = [Dist.DEDICATED_SERVER]
)
object HostileEvolutionHandler {

    private val HEALTH_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "hostile_health_day_bonus")
    private val DAMAGE_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "hostile_damage_day_bonus")
    private val ARMOR_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "hostile_armor_day_bonus")
    private val KB_RESIST_MODIFIER_ID = ResourceLocation(QLMZombieMod.MOD_ID, "hostile_kb_resist_day_bonus")

    @JvmStatic
    @SubscribeEvent
    fun onMonsterSpawn(event: MobSpawnEvent.FinalizeSpawn) {
        val mob = event.entity as? Monster ?: return
        // 僵尸由 ZombieEvolutionHandler 专门处理，避免重复加成
        if (mob is Zombie) return
        val level = mob.level()
        if (level.isClientSide) return

        if (!QLMConfig.HOSTILE_DAY_SCALING_ENABLED.get()) return

        val day = DayPhaseManager.getCurrentDay().toInt()
        val startDay = QLMConfig.HOSTILE_SCALING_START_DAY.get()
        val dayProgress = maxOf(0.0, (day - startDay).toDouble())
        // 未达到起始天数则跳过
        if (dayProgress <= 0.0) return

        // ---- 生命上限：随天数线性增长 ----
        val healthPerDay = QLMConfig.HOSTILE_HEALTH_PER_DAY.get()
        if (healthPerDay > 0.0) {
            val healthAttribute = mob.getAttribute(Attributes.MAX_HEALTH)
            if (healthAttribute != null) {
                val healthBonus = healthAttribute.baseValue * dayProgress * healthPerDay
                healthAttribute.addPermanentModifier(
                    AttributeModifier(
                        HEALTH_MODIFIER_ID.toString(),
                        healthBonus,
                        AttributeModifier.Operation.ADDITION
                    )
                )
                mob.health = mob.maxHealth
            }
        }

        // ---- 攻击力：随天数线性增长（无 ATTACK_DAMAGE 属性的实体自动跳过） ----
        val damagePerDay = QLMConfig.HOSTILE_DAMAGE_PER_DAY.get()
        if (damagePerDay > 0.0) {
            val damageAttribute = mob.getAttribute(Attributes.ATTACK_DAMAGE)
            if (damageAttribute != null) {
                val damageBonus = damageAttribute.baseValue * dayProgress * damagePerDay
                damageAttribute.addPermanentModifier(
                    AttributeModifier(
                        DAMAGE_MODIFIER_ID.toString(),
                        damageBonus,
                        AttributeModifier.Operation.ADDITION
                    )
                )
            }
        }

        // ---- 护甲：随天数线性增长 ----
        val armorPerDay = QLMConfig.HOSTILE_ARMOR_PER_DAY.get()
        if (armorPerDay > 0.0) {
            val armorAttribute = mob.getAttribute(Attributes.ARMOR)
            if (armorAttribute != null) {
                val armorBonus = dayProgress * armorPerDay
                armorAttribute.addPermanentModifier(
                    AttributeModifier(
                        ARMOR_MODIFIER_ID.toString(),
                        armorBonus,
                        AttributeModifier.Operation.ADDITION
                    )
                )
            }
        }

        // ---- 击退抗性：随天数线性增长（上限 0.5） ----
        val kbPerDay = QLMConfig.HOSTILE_KNOCKBACK_RESISTANCE_PER_DAY.get()
        if (kbPerDay > 0.0) {
            val kbAttribute = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
            if (kbAttribute != null) {
                val kbBonus = minOf(0.5, dayProgress * kbPerDay)
                kbAttribute.addPermanentModifier(
                    AttributeModifier(
                        KB_RESIST_MODIFIER_ID.toString(),
                        kbBonus,
                        AttributeModifier.Operation.ADDITION
                    )
                )
            }
        }

        QLMZombieMod.LOGGER.debug(
            "[敌对进化] {} 生成于第 {} 天, 生命: {}, 攻击: {}, 护甲: {}",
            mob.type.descriptionId,
            day,
            mob.maxHealth,
            mob.getAttributeValue(Attributes.ATTACK_DAMAGE),
            mob.getAttributeValue(Attributes.ARMOR)
        )
    }
}
