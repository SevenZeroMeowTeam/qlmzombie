package com.qlm.zombie.player

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.monster.Zombie
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object InfectionHandler {

    private const val BASE_INFECTION_CHANCE = 0.15f
    private const val MAX_INFECTION_STACKS = 10
    private const val INFECTION_DURATION_TICKS = 20 * 60 * 3
    private const val INFECTION_DECAY_TICKS = 20 * 60 * 5

    private val infectionStacks = mutableMapOf<String, Int>()
    private val lastInfectionTime = mutableMapOf<String, Long>()

    @JvmStatic
    @SubscribeEvent
    fun onLivingHurt(event: LivingHurtEvent) {
        val entity = event.entity
        val source = event.source

        if (entity.level().isClientSide) return
        if (entity !is Player) return

        val attacker = source.entity
        if (attacker !is Zombie) return

        if (!QLMConfig.enableThirst) return

        val baseChance = BASE_INFECTION_CHANCE
        val currentStacks = infectionStacks[entity.stringUUID] ?: 0
        val chance = baseChance + (currentStacks * 0.03f)

        if (entity.level().random.nextFloat() < chance) {
            applyInfection(entity, currentStacks, chance)
        }
    }

    private fun applyInfection(player: Player, currentStacks: Int, chance: Float) {
        val newStacks = (currentStacks + 1).coerceAtMost(MAX_INFECTION_STACKS)
        val uuid = player.stringUUID
        infectionStacks[uuid] = newStacks
        lastInfectionTime[uuid] = player.level().gameTime

        val amplifier = (newStacks - 1).coerceAtLeast(0)
        player.addEffect(
            MobEffectInstance(
                MobEffects.POISON,
                INFECTION_DURATION_TICKS,
                amplifier,
                false,
                true,
                true
            )
        )

        if (newStacks >= 5) {
            player.addEffect(
                MobEffectInstance(
                    MobEffects.WEAKNESS,
                    INFECTION_DURATION_TICKS,
                    amplifier / 2,
                    false,
                    true,
                    true
                )
            )
        }

        if (newStacks >= MAX_INFECTION_STACKS) {
            player.addEffect(
                MobEffectInstance(
                    MobEffects.CONFUSION,
                    INFECTION_DURATION_TICKS / 2,
                    0,
                    false,
                    true,
                    true
                )
            )
        }

        QLMZombieMod.LOGGER.info(
            "[Infection] 玩家 {} 感染! 层数: {}, 总概率: {:.1f}%",
            player.name.string,
            newStacks,
            (chance * 100)
        )
    }

    @JvmStatic
    @SubscribeEvent
    fun onLevelTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = event.player
        if (player.level().isClientSide) return

        val uuid = player.stringUUID
        val currentStacks = infectionStacks[uuid] ?: return

        val gameTime = player.level().gameTime
        val lastTime = lastInfectionTime[uuid] ?: 0L

        if (gameTime - lastTime > INFECTION_DECAY_TICKS) {
            infectionStacks[uuid] = (currentStacks - 1).coerceAtLeast(0)
            lastInfectionTime[uuid] = gameTime

            if (infectionStacks[uuid] == 0) {
                infectionStacks.remove(uuid)
                lastInfectionTime.remove(uuid)
                player.removeEffect(MobEffects.POISON)
                player.removeEffect(MobEffects.WEAKNESS)
                player.removeEffect(MobEffects.CONFUSION)
                QLMZombieMod.LOGGER.debug("[Infection] 玩家 {} 感染已清除", player.name.string)
            }
        }
    }

    fun getInfectionStacks(player: Player): Int {
        return infectionStacks[player.stringUUID] ?: 0
    }

    fun cureInfection(player: Player) {
        val uuid = player.stringUUID
        infectionStacks.remove(uuid)
        lastInfectionTime.remove(uuid)
        player.removeEffect(MobEffects.POISON)
        player.removeEffect(MobEffects.WEAKNESS)
        player.removeEffect(MobEffects.CONFUSION)
    }

    fun hasInfection(player: Player): Boolean {
        return (infectionStacks[player.stringUUID] ?: 0) > 0
    }
}