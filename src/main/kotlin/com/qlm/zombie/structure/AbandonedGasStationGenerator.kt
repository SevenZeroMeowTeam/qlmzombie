package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * 废弃加油站生成器：
 *  - 混凝土便利店（10×8×4），破损窗户/蜘蛛网
 *  - 加油棚（金属顶棚 + 立柱），加油机（铁块+铁栅栏）
 *  - 收银台 + 货架，2-4 个箱子（弹药/医疗/食物/杂物 + 其他模组物品）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object AbandonedGasStationGenerator {

    private const val SPAWN_CHANCE = 0.18
    private const val MIN_SPACING = 4
    private const val SHOP_WIDTH = 10
    private const val SHOP_DEPTH = 8
    private const val SHOP_HEIGHT = 4
    private const val LOGIN_SCAN_RADIUS = 3

    private val themedLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_BARS, Items.IRON_NUGGET,
            Items.BUCKET, Items.LAVA_BUCKET, Items.COAL, Items.CHARCOAL,
            Items.BREAD, Items.COOKED_PORKCHOP, Items.APPLE,
            QLMItems.EMERGENCY_RATION.get(), QLMItems.PURIFIED_WATER_BOTTLE.get(),
            QLMItems.TACTICAL_AMMO.get(), QLMItems.MEDICAL_SUPPLY.get(),
            CDItems.PISTOL_AMMO.get(), CDItems.BANDAGE.get(),
            CDItems.RIFLE_AMMO.get(), CDItems.FIRST_AID_KIT.get(),
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
                QLMZombieMod.LOGGER.error("[加油站] 延迟扫描异常: {}", e.message)
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

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - SHOP_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - SHOP_DEPTH / 2
        )

        // 跨会话防重复：收银台铁块标志
        val marker = BlockPos(origin.x + 2, origin.y + 1, origin.z + 2)
        if (level.getBlockState(marker).block == Blocks.IRON_BLOCK) {
            StructureGenSupport.generatedChunks.add(key)
            return false
        }

        return try {
            generateGasStation(level, origin)
            StructureGenSupport.generatedChunks.add(key)
            QLMZombieMod.LOGGER.info("[加油站] 在区块 ({}, {}) 生成废弃加油站", chunkX, chunkZ)
            true
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[加油站] 生成失败: {}", e.message)
            false
        }
    }

    private fun generateGasStation(level: net.minecraft.world.level.Level, origin: BlockPos.MutableBlockPos) {
        val x0 = origin.x
        val z0 = origin.z
        val y0 = origin.y
        val random = level.random

        // 地面硬化（平滑石 + 破损沥青）
        for (dx in 0 until SHOP_WIDTH) {
            for (dz in 0 until SHOP_DEPTH) {
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz)
                val block = if (random.nextDouble() < 0.2) Blocks.GRAVEL else Blocks.SMOOTH_STONE
                level.setBlock(pos, block.defaultBlockState(), 3)
            }
        }

        // 便利店墙体（灰色混凝土）
        for (dx in 0 until SHOP_WIDTH) {
            for (dz in 0 until SHOP_DEPTH) {
                val isWall = dx == 0 || dx == SHOP_WIDTH - 1 || dz == 0 || dz == SHOP_DEPTH - 1
                if (!isWall) continue
                for (dy in 1..SHOP_HEIGHT) {
                    val pos = BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz)
                    val isDoor = dy == 1 && dx == SHOP_WIDTH / 2 && dz == 0
                    val isWindow = dy == 2 && dz == 0 && dx in 2..(SHOP_WIDTH - 3) && dx != SHOP_WIDTH / 2
                    val isBrokenWindow = isWindow && random.nextDouble() < 0.4
                    when {
                        isDoor -> level.setBlock(pos, Blocks.IRON_DOOR.defaultBlockState(), 3)
                        isBrokenWindow -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
                        isWindow -> level.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState(), 3)
                        else -> level.setBlock(pos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
                    }
                }
            }
        }

        // 屋顶
        for (dx in 0 until SHOP_WIDTH) {
            for (dz in 0 until SHOP_DEPTH) {
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0 + SHOP_HEIGHT + 1, z0 + dz)
                level.setBlock(pos, Blocks.GRAY_CONCRETE.defaultBlockState(), 3)
            }
        }

        // 收银台（铁块 + 铁块顶）
        for (dx in 2..4) {
            for (dz in 2..3) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + 1, z0 + dz), Blocks.IRON_BLOCK.defaultBlockState(), 3)
            }
        }
        level.setBlock(BlockPos.MutableBlockPos(x0 + 3, y0 + 2, z0 + 3), Blocks.IRON_BLOCK.defaultBlockState(), 3)

        // 货架（橡木木板 + 台阶）
        for (shelf in 0 until 2) {
            val sx = if (shelf == 0) 1 else SHOP_WIDTH - 2
            for (dz in 2..5) {
                for (dy in 1..2) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + sx, y0 + dy, z0 + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3)
                }
            }
        }

        // 蜘蛛网（店内角落）
        val webSpots = listOf(
            Pair(1, 1), Pair(SHOP_WIDTH - 2, 1), Pair(1, SHOP_DEPTH - 2), Pair(SHOP_WIDTH - 2, SHOP_DEPTH - 2)
        )
        for ((wx, wz) in webSpots) {
            if (random.nextDouble() < 0.6) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + wx, y0 + 2, z0 + wz), Blocks.COBWEB.defaultBlockState(), 3)
            }
        }

        // 加油棚（前侧：顶棚 + 立柱 + 加油机）
        val canopyY = y0 + SHOP_HEIGHT + 1
        for (dx in 0 until SHOP_WIDTH) {
            level.setBlock(BlockPos.MutableBlockPos(x0 + dx, canopyY, z0 - 1), Blocks.GRAY_CONCRETE.defaultBlockState(), 3)
            level.setBlock(BlockPos.MutableBlockPos(x0 + dx, canopyY, z0 - 2), Blocks.GRAY_CONCRETE.defaultBlockState(), 3)
        }
        val pillarXs = listOf(1, SHOP_WIDTH - 2)
        for (px in pillarXs) {
            for (dy in 1..SHOP_HEIGHT) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + px, y0 + dy, z0 - 2), Blocks.IRON_BARS.defaultBlockState(), 3)
            }
        }
        // 加油机（铁块 + 铁栏杆 竖管）
        val pumpXs = listOf(2, SHOP_WIDTH - 3)
        for (px in pumpXs) {
            val pumpPos = BlockPos.MutableBlockPos(x0 + px, y0 + 1, z0 - 1)
            level.setBlock(pumpPos, Blocks.IRON_BLOCK.defaultBlockState(), 3)
            level.setBlock(pumpPos.above(), Blocks.IRON_BARS.defaultBlockState(), 3)
            level.setBlock(BlockPos.MutableBlockPos(x0 + px, y0 + 1, z0), Blocks.IRON_BARS.defaultBlockState(), 3)
        }

        // 宝箱 2-4 个（店内 + 收银台后）
        val chestSpots = listOf(
            Triple(1, 1, SHOP_DEPTH - 2),
            Triple(SHOP_WIDTH - 2, 1, SHOP_DEPTH - 2),
            Triple(5, 1, 2),
            Triple(SHOP_WIDTH - 3, 1, SHOP_DEPTH - 3),
        )
        val chestCount = 2 + random.nextInt(3)
        val picked = chestSpots.shuffled().take(chestCount)
        for ((cx, cy, cz) in picked) {
            val chestPos = BlockPos.MutableBlockPos(x0 + cx, y0 + cy, z0 + cz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, chestPos.immutable(), random, themedLoot, 0.55, 2, 5)
        }
    }
}
