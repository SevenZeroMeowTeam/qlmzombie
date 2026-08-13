package com.qlm.zombie.player

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.feature.ThirstFeature
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object PlayerInitHandler {

    private val CAPABILITY_THIRST = ResourceLocation(QLMZombieMod.MOD_ID, "thirst")
    private val CAPABILITY_QUALITY = ResourceLocation(QLMZombieMod.MOD_ID, "quality")

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        val original = event.original
        val newPlayer = event.entity

        ThirstFeature.setThirst(newPlayer, ThirstFeature.getThirst(original))

        QLMZombieMod.LOGGER.debug(
            "[PlayerInit] 玩家克隆: {} -> {}, 口渴值: {}",
            original.name.string,
            newPlayer.name.string,
            ThirstFeature.getThirst(newPlayer)
        )
    }

    @SubscribeEvent
    fun onPlayerStartTracking(event: PlayerEvent.StartTracking) {
        val player = event.entity as? Player ?: return
        val target = event.target

        if (target is Player) {
            val currentThirst = ThirstFeature.getThirst(player)
            if (currentThirst <= 0) {
                ThirstFeature.setThirst(player, ThirstFeature.MAX_THIRST / 2)
            }
            QLMZombieMod.LOGGER.debug(
                "[PlayerInit] 玩家 {} 开始追踪 {}, 口渴值: {}",
                player.name.string,
                target.name.string,
                ThirstFeature.getThirst(player)
            )
        }
    }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity ?: return
        ThirstFeature.setThirst(player, ThirstFeature.MAX_THIRST)
        QLMZombieMod.LOGGER.info(
            "[PlayerInit] 玩家 {} 登录, 口渴值已重置为 {}",
            player.name.string,
            ThirstFeature.MAX_THIRST
        )
    }

    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity ?: return
        ThirstFeature.removePlayer(player.uuid)
        QLMZombieMod.LOGGER.debug(
            "[PlayerInit] 玩家 {} 登出, 已清理数据",
            player.name.string
        )
    }

    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity ?: return
        if (!event.isEndConquered) {
            ThirstFeature.setThirst(player, (ThirstFeature.MAX_THIRST * 0.7f).toInt())
            QLMZombieMod.LOGGER.debug(
                "[PlayerInit] 玩家 {} 重生, 口渴值: {}",
                player.name.string,
                ThirstFeature.getThirst(player)
            )
        }
    }
}