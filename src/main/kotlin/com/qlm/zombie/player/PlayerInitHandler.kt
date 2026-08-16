package com.qlm.zombie.player

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.thirst.foundation.common.capability.ModCapabilities
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object PlayerInitHandler {

    /**
     * 玩家重生时补充部分口渴值（防御"因脱水死亡后无限死亡循环"）。
     * 口渴能力的持久化 / 玩家克隆同步已由 Thirst-Mod 能力系统（PlayerThirstManager）接管。
     */
    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        if (event.isEndConquered) return
        val player = event.entity ?: return
        player.getCapability(ModCapabilities.PLAYER_THIRST).ifPresent { cap ->
            cap.setThirst((20 * 0.7f).toInt())
            QLMZombieMod.LOGGER.debug(
                "[PlayerInit] 玩家 {} 重生, 口渴值: {}",
                player.name.string,
                cap.getThirst()
            )
        }
    }
}