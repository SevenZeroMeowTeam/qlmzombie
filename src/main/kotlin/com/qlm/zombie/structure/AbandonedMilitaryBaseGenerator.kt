package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 废弃军事基地生成器：
 *  - 混凝土围墙 + 铁丝网（铁栏杆）+ 四角岗楼
 *  - 营房（砖块/混凝土，含沙袋掩体）
 *  - 弹药库/军火库箱子（弹药/护甲/武器材料 + 其他模组物品）
 *  - TNT 板条箱（火药/沙/铁栏杆）
 */
object AbandonedMilitaryBaseGenerator : BuildingGenerator {

    private const val SPAWN_CHANCE = 0.08
    private const val MIN_SPACING = 6
    private const val BASE_WIDTH = 20
    private const val BASE_DEPTH = 16
    private const val WALL_HEIGHT = 4

    /** 每区块仅评估一次（无论是否生成），避免重复扫描时反复掷概率 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    private val militaryLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.IRON_BLOCK, Items.DIAMOND, Items.GOLD_INGOT,
            Items.BOW, Items.ARROW, Items.CROSSBOW, Items.IRON_SWORD,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.TNT, Items.GUNPOWDER, Items.LEATHER, Items.STRING,
            QLMItems.TACTICAL_AMMO.get(), QLMItems.MEDICAL_SUPPLY.get(),
            QLMItems.REINFORCED_PARTS.get(), QLMItems.SURVIVAL_KIT.get(),
            CDItems.RIFLE_AMMO.get(), CDItems.SNIPER_AMMO.get(), CDItems.SHOTGUN_SHELL.get(),
            CDItems.PISTOL_AMMO.get(), CDItems.BALLISTIC_HELMET.get(),
            CDItems.PLATE_CARRIER.get(), CDItems.TACTICAL_VEST.get(),
        )
    }

    override fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z
        val key = StructureGenSupport.chunkKey(chunkX, chunkZ)
        // 该区块已有其他废弃建筑，跳过防止重叠
        if (StructureGenSupport.generatedChunks.contains(key)) return false

        // 先确认区块地形已就绪（heightmap 有效），再标记"已评估"，避免过早标记导致永久跳过
        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 2) return false
        val biome = level.getBiome(BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8))
        if (biome.`is`(net.minecraft.tags.BiomeTags.IS_OCEAN)) return false

        // 区块就绪后，每区块仅评估一次（无论是否生成），保持概率语义
        if (!decidedChunks.add(key)) return false
        if (level.random.nextDouble() >= SPAWN_CHANCE) return false
        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, MIN_SPACING)) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - BASE_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - BASE_DEPTH / 2
        )

        // 跨会话防重复：大门铁块标志
        val marker = BlockPos(origin.x + BASE_WIDTH / 2, origin.y + 1, origin.z)
        if (level.getBlockState(marker).block == Blocks.IRON_BLOCK) {
            StructureGenSupport.generatedChunks.add(key)
            return false
        }

        return try {
            generateMilitaryBase(level, origin)
            StructureGenSupport.generatedChunks.add(key)
            StructureGenSupport.registerBuilding(key, net.minecraft.core.BlockPos(origin.x + BASE_WIDTH / 2, origin.y, origin.z + BASE_DEPTH / 2))
            QLMZombieMod.LOGGER.info("[军事基地] 在区块 ({}, {}) 生成废弃军事基地", chunkX, chunkZ)
            true
        } catch (e: Exception) {
            decidedChunks.remove(key) // 生成异常时取消"已评估"，允许周期扫描重试
            QLMZombieMod.LOGGER.error("[军事基地] 生成失败: {}", e.message)
            false
        }
    }

    private fun generateMilitaryBase(level: net.minecraft.world.level.Level, origin: BlockPos.MutableBlockPos) {
        val x0 = origin.x
        val z0 = origin.z
        val y0 = origin.y
        val random = level.random

        // 基地地面（砂土 + 碎石）
        for (dx in 0 until BASE_WIDTH) {
            for (dz in 0 until BASE_DEPTH) {
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz)
                val block = if (random.nextDouble() < 0.25) Blocks.GRAVEL else Blocks.COARSE_DIRT
                level.setBlock(pos, block.defaultBlockState(), 3)
            }
        }

        // 围墙（混凝土 + 顶部铁丝网）
        for (dx in 0 until BASE_WIDTH) {
            for (dz in 0 until BASE_DEPTH) {
                val isWall = dx == 0 || dx == BASE_WIDTH - 1 || dz == 0 || dz == BASE_DEPTH - 1
                if (!isWall) continue
                // 大门（南侧中间）
                val isGate = dz == 0 && dx in (BASE_WIDTH / 2 - 1)..(BASE_WIDTH / 2 + 1)
                for (dy in 1..WALL_HEIGHT) {
                    if (isGate && dy <= 1) continue // 门洞
                    val pos = BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz)
                    level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
                }
                // 铁丝网（墙顶）
                if (!isGate) {
                    val wirePos = BlockPos.MutableBlockPos(x0 + dx, y0 + WALL_HEIGHT + 1, z0 + dz)
                    level.setBlock(wirePos, Blocks.IRON_BARS.defaultBlockState(), 3)
                }
            }
        }
        // 大门铁块
        level.setBlock(BlockPos.MutableBlockPos(x0 + BASE_WIDTH / 2, y0 + 1, z0), Blocks.IRON_BLOCK.defaultBlockState(), 3)

        // 四角岗楼
        val corners = listOf(Pair(1, 1), Pair(BASE_WIDTH - 2, 1), Pair(1, BASE_DEPTH - 2), Pair(BASE_WIDTH - 2, BASE_DEPTH - 2))
        for ((cx, cz) in corners) {
            for (dy in 1..WALL_HEIGHT) {
                for (dx in 0..1) {
                    for (dz in 0..1) {
                        level.setBlock(BlockPos.MutableBlockPos(x0 + cx + dx, y0 + dy, z0 + cz + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3)
                    }
                }
            }
            // 岗楼顶 + 围栏
            level.setBlock(BlockPos.MutableBlockPos(x0 + cx, y0 + WALL_HEIGHT + 1, z0 + cz), Blocks.IRON_BARS.defaultBlockState(), 3)
            level.setBlock(BlockPos.MutableBlockPos(x0 + cx + 1, y0 + WALL_HEIGHT + 1, z0 + cz + 1), Blocks.IRON_BARS.defaultBlockState(), 3)
        }

        // 营房（中心偏后，混凝土）
        val barrackX0 = BASE_WIDTH / 2 - 4
        val barrackZ0 = BASE_DEPTH / 2 - 1
        for (dx in 0 until 8) {
            for (dz in 0 until 5) {
                // 地板
                level.setBlock(BlockPos.MutableBlockPos(x0 + barrackX0 + dx, y0 + 1, z0 + barrackZ0 + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                // 墙
                val isWall = dx == 0 || dx == 7 || dz == 0 || dz == 4
                if (isWall) {
                    for (dy in 2..4) {
                        val pos = BlockPos.MutableBlockPos(x0 + barrackX0 + dx, y0 + dy, z0 + barrackZ0 + dz)
                        val isDoor = dy == 2 && dz == 4 && dx == 3
                        if (isDoor) level.setBlock(pos, Blocks.IRON_DOOR.defaultBlockState(), 3)
                        else level.setBlock(pos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
                    }
                }
            }
        }
        // 营房顶
        for (dx in 0 until 8) {
            for (dz in 0 until 5) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + barrackX0 + dx, y0 + 5, z0 + barrackZ0 + dz), Blocks.GRAY_CONCRETE.defaultBlockState(), 3)
            }
        }

        // 沙袋掩体（军营入口两侧）
        for (sx in listOf(BASE_WIDTH / 2 - 4, BASE_WIDTH / 2 + 3)) {
            for (dz in 1..3) {
                for (dy in 1..2) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + sx, y0 + dy, z0 + dz), Blocks.SANDSTONE.defaultBlockState(), 3)
                }
            }
        }

        // 军火箱子 4-6 个（营房内外 + 墙角）
        val chestSpots = listOf(
            Triple(barrackX0 + 2, 2, barrackZ0 + 1),
            Triple(barrackX0 + 5, 2, barrackZ0 + 3),
            Triple(barrackX0 + 6, 2, barrackZ0 + 1),
            Triple(barrackX0 + 1, 2, barrackZ0 + 3),
            Triple(BASE_WIDTH / 2 - 2, 2, BASE_DEPTH / 2 + 3),
            Triple(BASE_WIDTH / 2 + 1, 2, BASE_DEPTH / 2 + 3),
        )
        val chestCount = 4 + random.nextInt(3)
        val picked = chestSpots.shuffled().take(chestCount)
        for ((cx, cy, cz) in picked) {
            val chestPos = BlockPos.MutableBlockPos(x0 + cx, y0 + cy - 1, z0 + cz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, chestPos.immutable(), random, militaryLoot, 0.6, 3, 6)
        }

        // TNT 板条箱（基地角落 1-2 个）
        val crateSpots = listOf(Pair(2, BASE_DEPTH - 3), Pair(BASE_WIDTH - 3, BASE_DEPTH - 3))
        for ((cx, cz) in crateSpots) {
            if (random.nextDouble() < 0.7) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + cx, y0 + 1, z0 + cz), Blocks.TNT.defaultBlockState(), 3)
                level.setBlock(BlockPos.MutableBlockPos(x0 + cx, y0 + 2, z0 + cz), Blocks.TNT.defaultBlockState(), 3)
                level.setBlock(BlockPos.MutableBlockPos(x0 + cx, y0 + 1, z0 + cz + 1), Blocks.SAND.defaultBlockState(), 3)
            }
        }
    }
}
