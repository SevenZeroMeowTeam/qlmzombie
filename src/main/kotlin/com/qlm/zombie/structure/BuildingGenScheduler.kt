package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * 废弃建筑统一生成调度器（服务端 / 单人集成服务器）。
 *
 * 三层触发机制确保客户端（含单人游戏）能可靠看到废弃建筑：
 *  1. ChunkEvent.Load 快速路径 —— 区块加载/生成时立即尝试（主力路径）
 *  2. 玩家登录延迟扫描 —— 玩家登录 2 秒后扫描出生点周围 9x9 已加载区块补生成
 *  3. 周期性扫描兜底 —— 每 5 秒扫描所有在线玩家周围 7x7 已加载区块，
 *     即使 ChunkEvent.Load 因时序问题漏掉，也能在下一轮扫描中补上
 *
 * 各生成器内部保证"每区块仅评估一次"（decidedChunks），
 * 因此多次扫描不会重复掷概率、不会导致建筑密度失控。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object BuildingGenScheduler {

    /** 玩家登录扫描半径（半径 4 = 9x9 = 81 区块） */
    private const val LOGIN_SCAN_RADIUS = 4

    /** 周期扫描半径（半径 3 = 7x7 = 49 区块） */
    private const val PERIODIC_SCAN_RADIUS = 3

    /** 周期扫描间隔（tick，100 = 5 秒） */
    private const val PERIODIC_INTERVAL_TICKS = 100

    /** 登录延迟（tick，40 = 2 秒，等待出生点区块异步加载完成） */
    private const val LOGIN_DELAY_TICKS = 40

    /** 周期扫描节流：每次扫描最多处理的区块数，防止大服务器卡顿 */
    private const val PERIODIC_MAX_CHUNKS = 64

    /** 周期扫描游标（旋转扫描，避免同一 tick 内重复扫同一区块） */
    private var periodicCursor = 0

    private var tickCounter = 0

    /** 所有废弃建筑生成器（顺序即优先级：先到先得，越靠前越容易占据区块） */
    private val generators: List<BuildingGenerator> = listOf(
        RandomBuildingGenerator,
        AbandonedShopGenerator,
        HighriseBuildingGenerator,
        OceanRuinGenerator,
        RuinsGenerator,
        AbandonedGasStationGenerator,
        AbandonedSchoolGenerator,
        AbandonedMilitaryBaseGenerator,
    )

    // ==================== 快速路径：区块加载时立即尝试 ====================
    @JvmStatic
    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        if (event.level.isClientSide) return
        val level = event.level as? Level ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        tryGenerateAll(level, chunk)
    }

    // ==================== 玩家登录：延迟扫描出生点周围 ====================
    @JvmStatic
    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity ?: return
        val level = player.level()
        if (level.isClientSide) return
        val serverLevel = level as? ServerLevel ?: return

        QLMZombieMod.LOGGER.info(
            "[建筑生成] 玩家 {} 登录, 延迟2秒后扫描周围区块补生成",
            player.name.string
        )
        val server = serverLevel.server
        server.tell(net.minecraft.server.TickTask(server.tickCount + LOGIN_DELAY_TICKS, Runnable {
            try {
                val (scanned, generated) = scanAroundPlayer(serverLevel, player, LOGIN_SCAN_RADIUS)
                QLMZombieMod.LOGGER.info(
                    "[建筑生成] 玩家 {} 登录扫描完成: 扫描{}区块, 新生成{}建筑",
                    player.name.string, scanned, generated
                )
            } catch (e: Exception) {
                QLMZombieMod.LOGGER.error("[建筑生成] 登录延迟扫描异常: {}", e.message)
            }
        }))
    }

    // ==================== 周期性扫描：兜底确保生成 ====================
    @JvmStatic
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        tickCounter++
        if (tickCounter % PERIODIC_INTERVAL_TICKS != 0) return

        val server = event.server ?: return
        if (server.playerList.players.isEmpty()) return

        var totalGenerated = 0
        var totalScanned = 0
        for (player in server.playerList.players) {
            val level = player.level()
            if (level !is ServerLevel) continue
            try {
                val (scanned, generated) = scanAroundPlayer(level, player, PERIODIC_SCAN_RADIUS)
                totalScanned += scanned
                totalGenerated += generated
            } catch (e: Exception) {
                QLMZombieMod.LOGGER.error("[建筑生成] 周期扫描异常: {}", e.message)
            }
        }
        if (totalGenerated > 0) {
            QLMZombieMod.LOGGER.info(
                "[建筑生成] 周期扫描完成: 扫描{}区块, 新生成{}建筑",
                totalScanned, totalGenerated
            )
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 扫描玩家周围 radius 半径内所有已加载区块并尝试生成。
     * 返回 (扫描区块数, 新生成建筑数)。
     */
    private fun scanAroundPlayer(
        serverLevel: ServerLevel,
        player: Player,
        radius: Int
    ): Pair<Int, Int> {
        val centerChunkX = player.blockPosition().x shr 4
        val centerChunkZ = player.blockPosition().z shr 4
        var scanned = 0
        var generated = 0

        // 旋转起点，使周期扫描在不同 tick 覆盖不同区块，避免每次从头开始
        val start = (periodicCursor % ((2 * radius + 1) * (2 * radius + 1)))
        val cells = (2 * radius + 1) * (2 * radius + 1)

        for (i in 0 until cells) {
            val idx = (start + i) % cells
            val dx = idx / (2 * radius + 1) - radius
            val dz = idx % (2 * radius + 1) - radius

            val chunk = serverLevel.chunkSource.getChunkNow(centerChunkX + dx, centerChunkZ + dz)
                ?: continue
            scanned++
            if (tryGenerateAll(serverLevel, chunk)) generated++
            if (scanned >= PERIODIC_MAX_CHUNKS) break
        }
        periodicCursor = (periodicCursor + 1) % 100000
        return scanned to generated
    }

    /** 依次尝试所有生成器，返回是否有任一生成器成功生成 */
    private fun tryGenerateAll(level: Level, chunk: LevelChunk): Boolean {
        var any = false
        for (gen in generators) {
            try {
                if (gen.tryGenerate(level, chunk)) any = true
            } catch (e: Exception) {
                // 单个生成器异常不影响其余生成器
                QLMZombieMod.LOGGER.warn(
                    "[建筑生成] 生成器 {} 异常: {}", gen.javaClass.simpleName, e.message
                )
            }
        }
        return any
    }
}
