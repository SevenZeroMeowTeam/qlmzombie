package com.qlm.zombie.dayphase

import com.qlm.zombie.QLMZombieMod
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.api.distmarker.Dist

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.DEDICATED_SERVER])
object DayPhaseManager {
    private var currentPhase: DayPhase = DayPhase.PEACE
    private var lastDay: Int = -1

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (event.level.isClientSide) return
        if (event.level.dimension() != Level.OVERWORLD) return

        val serverLevel = event.level as? ServerLevel ?: return
        val day = (serverLevel.dayTime / 24000L).toInt()

        if (day != lastDay) {
            lastDay = day
            val newPhase = DayPhase.fromDay(day)
            if (newPhase != currentPhase) {
                val oldPhase = currentPhase
                currentPhase = newPhase
                onPhaseChange(oldPhase, newPhase, serverLevel)
            }
        }
    }

    private fun onPhaseChange(oldPhase: DayPhase, newPhase: DayPhase, level: ServerLevel) {
        val msg = "§6[昼夜阶段] §e从 ${oldPhase.name} 切换到 ${newPhase.name} §7(难度乘数: ${newPhase.difficultyMultiplier}x)"
        for (player in level.players()) {
            player.sendSystemMessage(Component.literal(msg))
        }
        QLMZombieMod.LOGGER.info(
            "[昼夜阶段] 阶段变更: {} -> {}, 难度乘数: {}x",
            oldPhase.name,
            newPhase.name,
            newPhase.difficultyMultiplier
        )
    }

    @JvmStatic
    fun getCurrentPhase(): DayPhase = currentPhase

    @JvmStatic
    fun getCurrentDay(): Long {
        val server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() ?: return 0L
        val overworld = server.getLevel(Level.OVERWORLD) ?: return 0L
        return overworld.dayTime / 24000L
    }

    fun reset() {
        currentPhase = DayPhase.PEACE
        lastDay = -1
    }
}