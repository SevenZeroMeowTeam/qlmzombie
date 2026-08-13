package com.qlm.zombie.feature

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.UUID

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ThirstFeature {
    const val MAX_THIRST = 100
    private const val THIRST_DECREASE_INTERVAL = 200
    private const val THIRST_DECREASE_AMOUNT = 1
    private const val LOW_THIRST_THRESHOLD = 20

    private val thirstLevels = mutableMapOf<UUID, Int>()
    private val lastUpdateTicks = mutableMapOf<UUID, Long>()
    private val weakApplied = mutableSetOf<UUID>()

    @JvmStatic
    fun restoreThirst(player: LivingEntity, amount: Int) {
        val uuid = player.uuid
        val current = thirstLevels.getOrDefault(uuid, MAX_THIRST)
        thirstLevels[uuid] = (current + amount).coerceAtMost(MAX_THIRST)
    }

    @JvmStatic
    fun getThirst(player: LivingEntity): Int {
        return thirstLevels.getOrDefault(player.uuid, MAX_THIRST)
    }

    @JvmStatic
    fun setThirst(player: LivingEntity, value: Int) {
        thirstLevels[player.uuid] = value.coerceIn(0, MAX_THIRST)
    }

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (event.level.isClientSide) return
        if (!QLMConfig.enableThirst) return

        val level = event.level
        val gameTime = level.gameTime

        for (player in level.players()) {
            val uuid = player.uuid
            val lastTick = lastUpdateTicks.getOrDefault(uuid, 0L)

            if (gameTime - lastTick >= THIRST_DECREASE_INTERVAL) {
                lastUpdateTicks[uuid] = gameTime
                val current = thirstLevels.getOrDefault(uuid, MAX_THIRST)
                thirstLevels[uuid] = (current - THIRST_DECREASE_AMOUNT).coerceAtLeast(0)

                val thirst = thirstLevels[uuid]!!
                if (thirst <= LOW_THIRST_THRESHOLD) {
                    if (weakApplied.add(uuid)) {
                        player.addEffect(
                            MobEffectInstance(
                                MobEffects.WEAKNESS,
                                THIRST_DECREASE_INTERVAL + 100,
                                0,
                                false,
                                false,
                                true
                            )
                        )
                    }
                } else {
                    weakApplied.remove(uuid)
                }

                if (thirst <= 0) {
                    player.hurt(player.damageSources().starve(), 1.0f)
                }
            }
        }
    }

    fun resetPlayer(player: Player) {
        thirstLevels[player.uuid] = MAX_THIRST
        lastUpdateTicks[player.uuid] = 0L
        weakApplied.remove(player.uuid)
    }

    fun removePlayer(uuid: UUID) {
        thirstLevels.remove(uuid)
        lastUpdateTicks.remove(uuid)
        weakApplied.remove(uuid)
    }
}