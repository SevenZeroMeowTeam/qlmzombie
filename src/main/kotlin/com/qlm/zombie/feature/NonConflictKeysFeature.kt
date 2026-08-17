package com.qlm.zombie.feature

import com.qlm.zombie.QLMZombieMod
import net.minecraft.client.KeyMapping
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(
    modid = QLMZombieMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = [Dist.CLIENT]
)
object NonConflictKeysFeature {

    private val registeredKeys = mutableSetOf<String>()

    // 延迟初始化：避免类加载时创建 KeyMapping（客户端类），
    // 服务端即使意外加载此类也不会触发 NoClassDefFoundError
    private val keyMappings: List<KeyMapping> by lazy {
        listOf(
            KeyMapping(
                "key.qlmzombie.open_qlm_menu",
                GLFW.GLFW_KEY_R,
                "category.qlmzombie.main"
            ),
            KeyMapping(
                "key.qlmzombie.quick_use_antidote",
                GLFW.GLFW_KEY_V,
                "category.qlmzombie.main"
            ),
            KeyMapping(
                "key.qlmzombie.call_companion",
                GLFW.GLFW_KEY_B,
                "category.qlmzombie.main"
            ),
            KeyMapping(
                "key.qlmzombie.toggle_thirst",
                GLFW.GLFW_KEY_N,
                "category.qlmzombie.main"
            )
        )
    }

    @JvmStatic
    @SubscribeEvent
    fun onKeyRegister(event: RegisterKeyMappingsEvent) {
        for (keyMapping in keyMappings) {
            if (!registeredKeys.contains(keyMapping.name)) {
                event.register(keyMapping)
                registeredKeys.add(keyMapping.name)
                QLMZombieMod.LOGGER.debug(
                    "[NonConflict] 注册按键绑定: {}",
                    keyMapping.name
                )
            }
        }
    }
}