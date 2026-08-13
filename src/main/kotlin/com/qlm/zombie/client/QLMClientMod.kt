package com.qlm.zombie.client

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.player.ThirstBarOverlay
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = [Dist.CLIENT]
)
object QLMClientMod {

    @JvmField
    var initialized = false

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            ThirstBarOverlay.register()
            QLMZombieMod.LOGGER.info("[QLM Zombie] 口渴值覆盖层已注册")
        }
    }

    fun init() {
        ThirstBarOverlay.register()
        QLMZombieMod.LOGGER.info("[QLM Zombie] 口渴值覆盖层已注册")
    }
}

@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = [Dist.CLIENT]
)
object QLMClientForgeEvents {

    @SubscribeEvent
    fun onClientLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        if (!QLMClientMod.initialized) {
            QLMClientMod.initialized = true
            QLMZombieMod.LOGGER.info("[QLM Zombie] 客户端初始化完成 (Kotlin)")
        }
    }

    @SubscribeEvent
    fun onClientLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        QLMZombieMod.LOGGER.debug("[QLM Zombie] 玩家登出, 清理客户端数据")
    }
}
