package com.qlm.zombie.player

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.ai.companion.CompanionManager
import com.qlm.zombie.ai.task.TaskCatalogue
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object AIPlayerChatHandler {

    private const val COMMAND_PREFIX = "ai "

    @JvmStatic
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        CompanionManager.getInstance().tickAll()
    }

    @JvmStatic
    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {
        val player = event.player
        val message = event.message.string

        if (!message.startsWith(COMMAND_PREFIX)) return

        event.isCanceled = true
        handleChat(player, message.removePrefix(COMMAND_PREFIX).trim())
    }

    private fun handleChat(player: ServerPlayer, content: String) {
        val parts = content.split(" ")

        if (parts.isEmpty()) {
            sendHelp(player)
            return
        }

        val command = parts[0].lowercase()
        val args = parts.drop(1)

        when (command) {
            "spawn", "生成", "召唤" -> handleSpawn(player, args)
            "tame", "驯服" -> handleTame(player, args)
            "kill", "杀死", "移除" -> handleKill(player, args)
            "tp", "传送" -> handleTp(player, args)
            "help", "帮助" -> sendHelp(player)
            "status", "状态" -> handleStatus(player, args)
            "list", "列表" -> handleList(player)
            "stop", "停下" -> handleStop(player, args)
            else -> handleTaskCommand(player, command, args)
        }
    }

    private fun handleSpawn(player: ServerPlayer, args: List<String>) {
        val name = args.getOrNull(0) ?: "AI伴侣"
        val count = args.getOrNull(1)?.toIntOrNull() ?: 1

        for (i in 1..count) {
            val companionName = if (count > 1) "$name-$i" else name
            CompanionManager.getInstance().spawnCompanion(
                player.level(),
                player,
                companionName,
                player.position()
            )
        }

        player.sendSystemMessage(Component.literal("§a已生成 $count 个伴侣: $name"))
    }

    private fun handleTame(player: ServerPlayer, args: List<String>) {
        val name = args.getOrNull(0)
        if (name == null) {
            player.sendSystemMessage(Component.literal("§c用法: /ai tame <伴侣名> [增加度]"))
            return
        }

        val info = CompanionManager.getInstance().getByName(name)
        if (info == null) {
            player.sendSystemMessage(Component.literal("§c未找到伴侣: $name"))
            return
        }

        val amount = args.getOrNull(1)?.toFloatOrNull() ?: 10f
        CompanionManager.getInstance().tame(info.uuid, amount)
        player.sendSystemMessage(
            Component.literal("§6${info.name} 亲和度: ${"%.1f".format(info.affinity)}% ${if (info.isTamed) "§a[已驯服]" else "§c[未驯服]"}")
        )
    }

    private fun handleKill(player: ServerPlayer, args: List<String>) {
        val name = args.getOrNull(0)
        if (name == null) {
            CompanionManager.getInstance().getCompanionsOf(player).forEach {
                CompanionManager.getInstance().despawnCompanion(it.uuid)
            }
            player.sendSystemMessage(Component.literal("§a已移除所有伴侣"))
            return
        }

        val info = CompanionManager.getInstance().getByName(name)
        if (info == null) {
            player.sendSystemMessage(Component.literal("§c未找到伴侣: $name"))
            return
        }

        CompanionManager.getInstance().despawnCompanion(info.uuid)
        player.sendSystemMessage(Component.literal("§a伴侣已移除: $name"))
    }

    private fun handleTp(player: ServerPlayer, args: List<String>) {
        val name = args.getOrNull(0)
        if (name == null) {
            player.sendSystemMessage(Component.literal("§c用法: /ai tp <伴侣名>"))
            return
        }

        val info = CompanionManager.getInstance().getByName(name)
        if (info == null || info.entity == null) {
            player.sendSystemMessage(Component.literal("§c未找到伴侣: $name"))
            return
        }

        val entity = info.entity!!
        entity.teleportTo(player.x, player.y, player.z)
        player.sendSystemMessage(Component.literal("§a已传送 $name 到你的位置"))
    }

    private fun handleStatus(player: ServerPlayer, args: List<String>) {
        val name = args.getOrNull(0)
        if (name == null) {
            handleList(player)
            return
        }

        val info = CompanionManager.getInstance().getByName(name)
        if (info == null) {
            player.sendSystemMessage(Component.literal("§c未找到伴侣: $name"))
            return
        }

        val status = CompanionManager.getInstance().getStatus(info.uuid) ?: return
        val taskStatus = status["task"] as? Map<*, *>
        player.sendSystemMessage(Component.literal("§6=== ${info.name} 状态 ==="))
        player.sendSystemMessage(Component.literal("§7亲和度: ${"%.1f".format(info.affinity)}%"))
        player.sendSystemMessage(Component.literal("§7驯服: ${if (info.isTamed) "§a是" else "§c否"}"))
        player.sendSystemMessage(Component.literal("§7在线: ${if (status["alive"] as? Boolean == true) "§a是" else "§c否"}"))
        if (taskStatus != null) {
            player.sendSystemMessage(Component.literal("§7任务: ${taskStatus["currentTask"]}"))
            val progress = taskStatus["progress"]
            val progressStr = if (progress is Float) "%.1f".format(progress) else (progress?.toString() ?: "0")
            player.sendSystemMessage(Component.literal("§7进度: ${progressStr}%"))
        }
    }

    private fun handleList(player: ServerPlayer) {
        val companions = CompanionManager.getInstance().getCompanionsOf(player)
        if (companions.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7你还没有AI伴侣, 使用 /ai spawn 来生成"))
            return
        }

        player.sendSystemMessage(Component.literal("§6=== 你的AI伴侣 ==="))
        for (info in companions) {
            val entity = info.entity
            val pos = if (entity != null) {
                "§7(${entity.x.toInt()}, ${entity.y.toInt()}, ${entity.z.toInt()})"
            } else {
                "§c[离线]"
            }
            player.sendSystemMessage(
                Component.literal("§b${info.name} §7- 亲和度: ${"%.1f".format(info.affinity)}% $pos")
            )
        }
    }

    private fun handleStop(player: ServerPlayer, args: List<String>) {
        val name = args.getOrNull(0)
        if (name == null) {
            val companions = CompanionManager.getInstance().getCompanionsOf(player)
            companions.forEach { it.taskRunner.clearQueue() }
            player.sendSystemMessage(Component.literal("§a所有伴侣任务已停止"))
            return
        }

        val info = CompanionManager.getInstance().getByName(name)
        if (info == null) {
            player.sendSystemMessage(Component.literal("§c未找到伴侣: $name"))
            return
        }

        info.taskRunner.clearQueue()
        player.sendSystemMessage(Component.literal("§a已停止 ${info.name} 的任务"))
    }

    private fun handleTaskCommand(player: ServerPlayer, command: String, args: List<String>) {
        val companions = CompanionManager.getInstance().getCompanionsOf(player)
        if (companions.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c你还没有AI伴侣"))
            return
        }

        val targetName = args.getOrNull(0)
        val actualArgs = if (targetName != null && CompanionManager.getInstance().getByName(targetName) != null) {
            args.drop(1)
        } else {
            args
        }

        val targetCompanions = if (targetName != null && CompanionManager.getInstance().getByName(targetName) != null) {
            listOf(CompanionManager.getInstance().getByName(targetName)!!)
        } else {
            companions
        }

        for (info in targetCompanions) {
            val entity = info.entity ?: continue
            val task = TaskCatalogue.getTask(command, entity, actualArgs)
            if (task != null) {
                info.taskRunner.addTask(task, 0)
                player.sendSystemMessage(Component.literal("§a已为 ${info.name} 添加任务: $command"))
            } else {
                player.sendSystemMessage(Component.literal("§c无法创建任务: $command"))
            }
        }
    }

    private fun sendHelp(player: ServerPlayer) {
        player.sendSystemMessage(Component.literal("§6=== AI伴侣系统帮助 ==="))
        player.sendSystemMessage(Component.literal("§b/ai spawn [名字] [数量] §7- 生成AI伴侣"))
        player.sendSystemMessage(Component.literal("§b/ai tame <名字> [增加度] §7- 驯服伴侣"))
        player.sendSystemMessage(Component.literal("§b/ai kill [名字] §7- 移除伴侣"))
        player.sendSystemMessage(Component.literal("§b/ai tp <名字> §7- 传送伴侣到身边"))
        player.sendSystemMessage(Component.literal("§b/ai status [名字] §7- 查看状态"))
        player.sendSystemMessage(Component.literal("§b/ai list §7- 列出所有伴侣"))
        player.sendSystemMessage(Component.literal("§b/ai stop [名字] §7- 停止任务"))
        player.sendSystemMessage(Component.literal("§6--- 任务命令 ---"))

        for ((cmd, desc) in TaskCatalogue.getCommandHelp()) {
            player.sendSystemMessage(Component.literal("§b  $cmd §7- $desc"))
        }
    }
}