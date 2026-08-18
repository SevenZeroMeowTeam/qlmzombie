package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 废弃学校生成器：
 *  - 砖墙教学楼（16×12×5），走廊贯通
 *  - 5 间教室：每间含床 + 蜘蛛网 + 箱子（箱子含本模组与其他模组物品，各箱物品不同）
 *  - 破窗户、坍塌屋顶、散落书本（书/书架）
 */
object AbandonedSchoolGenerator : BuildingGenerator {

    private const val SCHOOL_WIDTH = 16
    private const val SCHOOL_DEPTH = 12
    private const val SCHOOL_HEIGHT = 5

    /** 每区块仅评估一次（无论是否生成），避免重复扫描时反复掷概率 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    // 各房间不同战利品主题（5 间房，每个箱子物品不同）
    private val roomLoot by lazy {
        listOf(
            // 图书室：书/纸张/墨水
            listOf(
                Items.BOOK, Items.WRITABLE_BOOK, Items.PAPER, Items.INK_SAC, Items.BOOKSHELF,
                QLMItems.SURVIVAL_KIT.get(), QLMItems.BIOHAZARD_SAMPLE.get(),
            ),
            // 化学教室：药水/火药/材料
            listOf(
                Items.GLASS_BOTTLE, Items.GUNPOWDER, Items.REDSTONE, Items.GLOWSTONE_DUST,
                Items.SUGAR, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE,
                CDItems.PAINKILLERS.get(), QLMItems.ANTIDOTE.get(),
            ),
            // 食堂：食物
            listOf(
                Items.BREAD, Items.COOKED_BEEF, Items.APPLE, Items.GOLDEN_APPLE, Items.CARROT,
                Items.POTATO, Items.BAKED_POTATO, Items.MELON_SLICE,
                QLMItems.EMERGENCY_RATION.get(), QLMItems.PURIFIED_WATER_BOTTLE.get(),
            ),
            // 机房：红石/铁/金（电子元件）
            listOf(
                Items.REDSTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.REPEATER, Items.COMPARATOR, Items.COMPASS, Items.CLOCK,
                CDItems.RIFLE_AMMO.get(), CDItems.PISTOL_AMMO.get(),
            ),
            // 医务室：医疗
            listOf(
                Items.PAPER, Items.LEATHER, Items.STRING, Items.BONE,
                QLMItems.MEDICAL_SUPPLY.get(), QLMItems.ANTIDOTE.get(),
                CDItems.BANDAGE.get(), CDItems.FIRST_AID_KIT.get(), CDItems.SPLINT.get(),
                CDItems.ADRENALINE_SYRINGE.get(),
            ),
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
        if (level.random.nextDouble() >= QLMConfig.SCHOOL_CHANCE.get()) return false
        if (!StructureGenSupport.isFlatTerrain(chunk, QLMConfig.FLAT_TOLERANCE_MEDIUM.get())) return false
        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, QLMConfig.SCHOOL_SPACING.get())) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - SCHOOL_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - SCHOOL_DEPTH / 2
        )

        // 跨会话防重复：走廊地板石砖标志
        val marker = BlockPos(origin.x + SCHOOL_WIDTH / 2, origin.y, origin.z + SCHOOL_DEPTH / 2)
        if (level.getBlockState(marker).block == Blocks.STONE_BRICKS) {
            StructureGenSupport.generatedChunks.add(key)
            return false
        }

        return try {
            generateSchool(level, origin)
            StructureGenSupport.generatedChunks.add(key)
            StructureGenSupport.registerBuilding(key, net.minecraft.core.BlockPos(origin.x + SCHOOL_WIDTH / 2, origin.y, origin.z + SCHOOL_DEPTH / 2))
            QLMZombieMod.LOGGER.info("[学校] 在区块 ({}, {}) 生成废弃学校", chunkX, chunkZ)
            true
        } catch (e: Exception) {
            decidedChunks.remove(key) // 生成异常时取消"已评估"，允许周期扫描重试
            QLMZombieMod.LOGGER.error("[学校] 生成失败: {}", e.message)
            false
        }
    }

    private fun generateSchool(level: net.minecraft.world.level.Level, origin: BlockPos.MutableBlockPos) {
        val x0 = origin.x
        val z0 = origin.z
        val y0 = origin.y
        val random = level.random

        // 地面（石砖 + 破损木板）
        for (dx in 0 until SCHOOL_WIDTH) {
            for (dz in 0 until SCHOOL_DEPTH) {
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz)
                val block = if (random.nextDouble() < 0.15) Blocks.OAK_PLANKS else Blocks.STONE_BRICKS
                level.setBlock(pos, block.defaultBlockState(), 3)
            }
        }

        // 外墙（砖块 + 玻璃窗）
        for (dx in 0 until SCHOOL_WIDTH) {
            for (dz in 0 until SCHOOL_DEPTH) {
                val isWall = dx == 0 || dx == SCHOOL_WIDTH - 1 || dz == 0 || dz == SCHOOL_DEPTH - 1
                if (!isWall) continue
                for (dy in 1..SCHOOL_HEIGHT) {
                    val pos = BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz)
                    val isWindow = dy == 2 && ((dz == 0 || dz == SCHOOL_DEPTH - 1) || (dx == 0 || dx == SCHOOL_WIDTH - 1))
                    val isBrokenWindow = isWindow && random.nextDouble() < 0.45
                    when {
                        isBrokenWindow -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
                        isWindow -> level.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState(), 3)
                        else -> level.setBlock(pos, Blocks.BRICKS.defaultBlockState(), 3)
                    }
                }
            }
        }

        // 正门：1 格宽 × 2 格高橡木门 + 砖块门楣（朝南外开）
        StructureGenSupport.placeDoor1x2(
            level,
            x0 + SCHOOL_WIDTH / 2,
            y0 + 1,
            z0 + SCHOOL_DEPTH - 1,
            Direction.SOUTH,
            Blocks.OAK_DOOR as DoorBlock,
            Blocks.BRICKS.defaultBlockState()
        )

        // 走廊内部墙（把学校分成左右各 5 个房间区域，中间走廊 z=5..6）
        val corridorZ = SCHOOL_DEPTH / 2
        for (dx in 0 until SCHOOL_WIDTH) {
            // 走廊两侧墙（在走廊 z=corridorZ 与 z=corridorZ-1 之间，以及 z=corridorZ+1）
            for (wz in listOf(corridorZ - 1, corridorZ + 1)) {
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0 + 1, z0 + wz)
                if (random.nextDouble() < 0.1) continue // 走廊墙破损留缺口
                level.setBlock(pos, Blocks.BRICKS.defaultBlockState(), 3)
                val pos2 = BlockPos.MutableBlockPos(x0 + dx, y0 + 2, z0 + wz)
                level.setBlock(pos2, Blocks.BRICKS.defaultBlockState(), 3)
            }
        }
        // 走廊留门洞（每 4 格）
        for (dx in 2 until SCHOOL_WIDTH step 4) {
            for (wz in listOf(corridorZ - 1, corridorZ + 1)) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + 1, z0 + wz), Blocks.AIR.defaultBlockState(), 3)
            }
        }

        // 5 间房：每间 = 床 + 蜘蛛网 + 箱子（不同主题战利品）
        val roomDefs = listOf(
            // (房间中心dx, 房间中心dz, 主题索引)
            Triple(2, 2, 0), Triple(7, 2, 1), Triple(13, 2, 2),
            Triple(3, 9, 3), Triple(8, 9, 4),
        )
        for ((roomIndex, roomDef) in roomDefs.withIndex()) {
            val rx = roomDef.first
            val rz = roomDef.second
            val theme = roomDef.third

            // 床（朝走廊方向）
            val bedPos = BlockPos.MutableBlockPos(x0 + rx, y0 + 1, z0 + rz)
            level.setBlock(bedPos, Blocks.RED_BED.defaultBlockState(), 3)
            level.setBlock(bedPos.relative(net.minecraft.core.Direction.SOUTH), Blocks.RED_BED.defaultBlockState().setValue(
                net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3)

            // 蜘蛛网（房间角落 2-3 个）
            val webCount = 2 + random.nextInt(2)
            for (w in 0 until webCount) {
                val wx = rx + random.nextInt(3) - 1
                val wz = rz + random.nextInt(3) - 1
                val webPos = BlockPos.MutableBlockPos(x0 + wx, y0 + random.nextInt(3) + 1, z0 + wz)
                if (level.getBlockState(webPos).isAir) {
                    level.setBlock(webPos, Blocks.COBWEB.defaultBlockState(), 3)
                }
            }

            // 书架/课桌（房间装饰）
            if (random.nextDouble() < 0.5) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + rx + 1, y0 + 1, z0 + rz), Blocks.BOOKSHELF.defaultBlockState(), 3)
            } else {
                level.setBlock(BlockPos.MutableBlockPos(x0 + rx + 1, y0 + 1, z0 + rz), Blocks.OAK_PLANKS.defaultBlockState(), 3)
            }

            // 箱子（每间房都有，物品主题不同，混入其他模组物品）
            val chestPos = BlockPos.MutableBlockPos(x0 + rx - 1, y0 + 1, z0 + rz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, chestPos.immutable(), random, roomLoot[theme], 0.5, 3, 6)
            StructureGenSupport.maybeInjectTaczWeapon(level, chestPos.immutable(), random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
            QLMZombieMod.LOGGER.debug("[学校] 房间 {} 箱子已生成于 {}", roomIndex, chestPos)
        }

        // 校长室（走廊尽头）CD 补给箱（高级战利品）
        val officeChest = BlockPos.MutableBlockPos(x0 + SCHOOL_WIDTH - 2, y0 + 1, z0 + corridorZ)
        level.setBlock(officeChest, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
        StructureGenSupport.fillCDCrate(level, officeChest.immutable(), random, roomLoot[4], 0.7, 3, 7)
        StructureGenSupport.maybeInjectTaczWeapon(level, officeChest.immutable(), random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())

        // 屋顶（部分坍塌）
        for (dx in 0 until SCHOOL_WIDTH) {
            for (dz in 0 until SCHOOL_DEPTH) {
                if (random.nextDouble() < 0.2) continue // 坍塌缺口
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0 + SCHOOL_HEIGHT + 1, z0 + dz)
                level.setBlock(pos, Blocks.BRICKS.defaultBlockState(), 3)
            }
        }
    }
}
