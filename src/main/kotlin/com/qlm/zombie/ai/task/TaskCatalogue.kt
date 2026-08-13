package com.qlm.zombie.ai.task

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

object TaskCatalogue {

    private val factories = mutableMapOf<String, (LivingEntity, List<String>) -> Task>()

    init {
        register("mine", "挖矿", "采矿") { entity, args ->
            val radius = args.getOrNull(0)?.toIntOrNull() ?: 5
            val filter = args.getOrNull(1) ?: ""
            MineTask(entity, radius, filter)
        }

        register("chop", "砍树", "伐木") { entity, args ->
            val radius = args.getOrNull(0)?.toIntOrNull() ?: 5
            MineTask(entity, radius, "tree")
        }

        register("collect", "收集", "采集") { entity, args ->
            val radius = args.getOrNull(0)?.toIntOrNull() ?: 5
            val filter = args.getOrNull(1) ?: ""
            MineTask(entity, radius, filter)
        }

        register("attack", "攻击", "战斗") { entity, args ->
            val targetName = args.getOrNull(0) ?: ""
            MineTask(entity, 5, "enemy")
        }

        register("build", "建造", "建房") { entity, args ->
            val size = args.getOrNull(0)?.toIntOrNull() ?: 5
            BuildTask(entity, size)
        }

        register("craft", "制作", "合成") { entity, args ->
            val recipe = args.getOrNull(0) ?: ""
            MineTask(entity, 3, "crafting")
        }

        register("follow", "跟随", "跟着") { entity, args ->
            val playerName = args.getOrNull(0) ?: ""
            val distance = args.getOrNull(1)?.toFloatOrNull() ?: 3f
            FollowTask(entity, playerName, distance)
        }

        register("stop", "停下", "停止") { entity, args ->
            MineTask(entity, 0, "__stop__")
        }
    }

    private fun register(vararg commands: String, factory: (LivingEntity, List<String>) -> Task) {
        for (cmd in commands) {
            factories[cmd.lowercase()] = factory
        }
    }

    fun getTask(command: String, entity: LivingEntity, args: List<String> = emptyList()): Task? {
        val key = command.lowercase().trim()
        val factory = factories[key]
        if (factory == null) {
            QLMZombieMod.LOGGER.warn("[TaskCatalogue] 未知命令: $command")
            return null
        }
        return try {
            factory(entity, args)
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[TaskCatalogue] 创建任务失败: ${e.message}")
            null
        }
    }

    fun getAvailableCommands(): List<String> {
        return factories.keys.toList().sorted()
    }

    fun getCommandHelp(): Map<String, String> {
        return mapOf(
            "挖矿 <半径> [方块名]" to "在指定半径内挖掘方块",
            "砍树 <半径>" to "在指定半径内砍伐树木",
            "收集 <半径> [物品名]" to "在指定半径内收集掉落物",
            "攻击 [目标名]" to "攻击附近的敌对生物",
            "建造 <大小>" to "建造指定大小的小屋",
            "制作 [配方]" to "在工作台制作物品",
            "跟随 [玩家名] [距离]" to "跟随指定玩家",
            "停下" to "停止当前任务"
        )
    }
}