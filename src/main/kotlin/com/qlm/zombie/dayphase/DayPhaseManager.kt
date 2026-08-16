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
    private var lockApplied: Boolean = false

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

        // 100 天+：锁定困难，强制难度为困难且无法更改
        if (currentPhase == DayPhase.LOCKED_HARD && !lockApplied) {
            lockApplied = true
            applyHardLock(serverLevel)
        }
    }

    private fun applyHardLock(level: ServerLevel) {
        try {
            // 持久化锁定状态
            val state = DifficultyLockState.get(level.server)
            state.setLocked(true)
            state.setLastAppliedDay(getCurrentDay())
            state.setLastPhase(DayPhase.LOCKED_HARD.name)
            // 强制困难难度
            if (level.difficulty != net.minecraft.world.Difficulty.HARD) {
                level.server.setDifficulty(net.minecraft.world.Difficulty.HARD, true)
            }
            for (player in level.players()) {
                player.sendSystemMessage(
                    Component.literal("§4[昼夜阶段] §c服务器已进入 §6锁定困难 §c阶段（第 100 天+），难度锁定为困难，无法更改！")
                )
            }
            QLMZombieMod.LOGGER.info("[昼夜阶段] 已锁定困难难度（第 {} 天后）", getCurrentDay())
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.warn("[昼夜阶段] 应用困难锁定失败: {}", e.message)
        }
    }

    /** 难度是否已锁定（100 天+） */
    @JvmStatic
    fun isDifficultyLocked(): Boolean = currentPhase == DayPhase.LOCKED_HARD

    /** 阻止在锁定阶段更改难度（供命令等调用，返回是否被阻止） */
    @JvmStatic
    fun blockDifficultyChange(): Boolean {
        if (isDifficultyLocked()) return true
        val server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() ?: return false
        val state = DifficultyLockState.get(server)
        return state.isLocked()
    }

    private fun onPhaseChange(oldPhase: DayPhase, newPhase: DayPhase, level: ServerLevel) {
        val msg = "§6[昼夜阶段] §e从 ${oldPhase.name} 切换到 ${newPhase.name} §7(难度乘数: ${newPhase.difficultyMultiplier}x)" +
            if (newPhase.isLocked()) " §c[难度已锁定，无法更改]" else ""
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
        lockApplied = false
    }
}