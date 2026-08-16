package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * 废墟生成器：
 *  - 散落的断壁残垣（石砖/圆石），破损的柱子
 *  - 地面碎石（圆石/砂砾/苔石），蜘蛛网
 *  - 1-3 个箱子（废墟杂物/医疗/食物战利品 + 其他模组物品）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object RuinsGenerator {

    private const val SPAWN_CHANCE = 0.28
    private const val MIN_SPACING = 3
    private const val RUIN_RADIUS = 5
    private const val LOGIN_SCAN_RADIUS = 3

    private val themedLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.COAL,
            Items.BREAD, Items.ROTTEN_FLESH, Items.BONE, Items.STRING,
            Items.LEATHER, Items.IRON_NUGGET, Items.GLASS_BOTTLE,
            QLMItems.ZOMBIE_CORE.get(), QLMItems.INFECTED_ESSENCE.get(),
            QLMItems.EMERGENCY_RATION.get(), QLMItems.PURIFIED_WATER_BOTTLE.get(),
            CDItems.BANDAGE.get(), CDItems.PAINKILLERS.get(),
        )
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        if (event.level.isClientSide) return
        val level = event.level as? net.minecraft.world.level.Level ?: return
        val chunk = event.chunk as? net.minecraft.world.level.chunk.LevelChunk ?: return
        tryGenerate(level, chunk)
    }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity ?: return
        val level = player.level()
        if (level.isClientSide) return
        val serverLevel = level as? net.minecraft.server.level.ServerLevel ?: return
        val server = serverLevel.server
        server.tell(net.minecraft.server.TickTask(server.tickCount + 40, Runnable {
            try {
                val cx = player.blockPosition().x shr 4
                val cz = player.blockPosition().z shr 4
                for (dx in -LOGIN_SCAN_RADIUS..LOGIN_SCAN_RADIUS) {
                    for (dz in -LOGIN_SCAN_RADIUS..LOGIN_SCAN_RADIUS) {
                        val chunk = serverLevel.chunkSource.getChunkNow(cx + dx, cz + dz)
                        if (chunk != null) tryGenerate(serverLevel, chunk)
                    }
                }
            } catch (e: Exception) {
                QLMZombieMod.LOGGER.error("[废墟] 延迟扫描异常: {}", e.message)
            }
        }))
    }

    private fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z
        val key = StructureGenSupport.chunkKey(chunkX, chunkZ)
        if (StructureGenSupport.generatedChunks.contains(key)) return false
        if (level.random.nextDouble() >= SPAWN_CHANCE) return false
        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, MIN_SPACING)) return false

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 2) return false
        val biome = level.getBiome(BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8))
        if (biome.`is`(net.minecraft.tags.BiomeTags.IS_OCEAN)) return false

        val origin = BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)

        // 跨会话防重复：中心废墟标志（苔石）
        val marker = BlockPos(origin.x, origin.y, origin.z)
        if (level.getBlockState(marker).block == Blocks.MOSSY_COBBLESTONE) {
            StructureGenSupport.generatedChunks.add(key)
            return false
        }

        return try {
            generateRuins(level, origin)
            StructureGenSupport.generatedChunks.add(key)
            QLMZombieMod.LOGGER.info("[废墟] 在区块 ({}, {}) 生成废墟", chunkX, chunkZ)
            true
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[废墟] 生成失败: {}", e.message)
            false
        }
    }

    private fun generateRuins(level: net.minecraft.world.level.Level, origin: BlockPos.MutableBlockPos) {
        val x0 = origin.x
        val z0 = origin.z
        val y0 = origin.y
        val random = level.random

        // 碎石地面（圆石/砂砾/苔石/石砖）
        for (dx in -RUIN_RADIUS..RUIN_RADIUS) {
            for (dz in -RUIN_RADIUS..RUIN_RADIUS) {
                val dist = Math.max(Math.abs(dx), Math.abs(dz))
                if (dist > RUIN_RADIUS) continue
                if (random.nextDouble() < 0.65) {
                    val pos = BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz)
                    val block = when (random.nextInt(5)) {
                        0 -> Blocks.GRAVEL
                        1 -> Blocks.MOSSY_COBBLESTONE
                        2 -> Blocks.COBBLESTONE
                        3 -> Blocks.STONE_BRICKS
                        else -> Blocks.CRACKED_STONE_BRICKS
                    }
                    level.setBlock(pos, block.defaultBlockState(), 3)
                }
            }
        }

        // 断壁残垣（石砖/苔石墙，部分倒塌）
        val wallSegments = listOf(
            Triple(-4, 2, 2), Triple(-2, 3, -1), Triple(0, 2, 3), Triple(2, 3, -2),
            Triple(4, 1, 1), Triple(-3, 2, -3), Triple(3, 2, 3), Triple(1, 1, -4),
        )
        for ((wx, wallH, wz) in wallSegments) {
            if (random.nextDouble() < 0.15) continue // 部分彻底倒塌
            val base = BlockPos.MutableBlockPos(x0 + wx, y0, z0 + wz)
            // 地基
            for (dy in 0 until wallH) {
                val pos = BlockPos.MutableBlockPos(base.x, y0 + dy, base.z)
                val block = if (random.nextDouble() < 0.4) Blocks.MOSSY_STONE_BRICKS else Blocks.STONE_BRICKS
                level.setBlock(pos, block.defaultBlockState(), 3)
                if (random.nextDouble() < 0.15) {
                    level.setBlock(pos.above(), Blocks.COBWEB.defaultBlockState(), 3)
                }
            }
        }

        // 破损柱子
        val pillars = listOf(Pair(-5, -5), Pair(5, -5), Pair(-5, 5), Pair(5, 5), Pair(0, 0))
        for ((px, pz) in pillars) {
            if (random.nextDouble() < 0.3) continue
            val colH = 2 + random.nextInt(2)
            for (dy in 0 until colH) {
                val pos = BlockPos.MutableBlockPos(x0 + px, y0 + dy, z0 + pz)
                level.setBlock(pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3)
            }
            if (random.nextDouble() < 0.3) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + px, y0 + colH, z0 + pz), Blocks.COBWEB.defaultBlockState(), 3)
            }
        }

        // 宝箱 1-3 个（废墟角落 + 中心）
        val chestSpots = listOf(
            Pair(-3, -3), Pair(3, -3), Pair(-3, 3), Pair(3, 3), Pair(0, 2)
        )
        val chestCount = 1 + random.nextInt(3)
        val picked = chestSpots.shuffled().take(chestCount)
        for ((cx, cz) in picked) {
            val chestPos = BlockPos.MutableBlockPos(x0 + cx, y0 + 1, z0 + cz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, chestPos.immutable(), random, themedLoot, 0.5, 2, 5)
        }
    }
}
