package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 破败办公楼生成器（21×15，5层，每层4米）：
 * - 玻璃幕墙外立面（GLASS_PANE + LIGHT_GRAY_CONCRETE 框架）
 * - 中央3×3电梯井（混凝土填充，不可通行），旁边3×3楼梯井（L形旋转楼梯）
 * - 每层4间办公室（LIGHT_GRAY_CONCRETE 隔墙 + SMOOTH_STONE 地面）
 * - 一楼大厅 1×2 铁门朝南开
 * - 每层2个普通箱子（科技/办公用品），顶层董事长办公室用 CDBlocks.SUPPLY_CRATE
 */
object OfficeBuildingGenerator : BuildingGenerator {

    private const val WIDTH = 21
    private const val DEPTH = 15
    private const val FLOORS = 5
    private const val FLOOR_HEIGHT = 4
    private const val STAIRCASE_SIZE = 3
    private const val ELEVATOR_SIZE = 3
    private const val MOD_ITEM_CHANCE = 0.5

    /** 每区块仅评估一次（无论是否生成），避免重复扫描时反复掷概率 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    /** 科技/办公用品战利品 */
    private val officeLoot by lazy {
        listOf(
            QLMItems.REINFORCED_PARTS.get(),
            Items.PAPER,
            Items.BOOK,
            Items.BOOKSHELF,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.GOLD_NUGGET,
            Items.EMERALD,
        )
    }

    override fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        val chunkKey = StructureGenSupport.chunkKey(chunkX, chunkZ)
        // 该区块已有其他废弃建筑，跳过防止重叠
        if (StructureGenSupport.generatedChunks.contains(chunkKey)) return false

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY < 40) return false

        // 区块就绪后，每区块仅评估一次（无论是否生成），保持概率语义
        if (!decidedChunks.add(chunkKey)) return false

        val biome = level.getBiome(
            BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)
        )
        if (biome.`is`(net.minecraft.tags.BiomeTags.IS_OCEAN)) return false

        // 平面检测：办公楼需要平坦地形
        if (!StructureGenSupport.isFlatTerrain(chunk, QLMConfig.FLAT_TOLERANCE_MEDIUM.get())) return false

        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, QLMConfig.OFFICE_SPACING.get())) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - DEPTH / 2
        )

        // 跨会话防重复：若建筑标志（双层 SMOOTH_STONE 地板）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            StructureGenSupport.generatedChunks.add(chunkKey)
            return false
        }

        if (level.random.nextDouble() >= QLMConfig.OFFICE_CHANCE.get()) return false

        return try {
            generateOffice(level, chunk, origin)
            StructureGenSupport.generatedChunks.add(chunkKey)
            StructureGenSupport.registerBuilding(
                chunkKey,
                BlockPos(origin.x + WIDTH / 2, origin.y, origin.z + DEPTH / 2)
            )
            QLMZombieMod.LOGGER.info(
                "[破败办公楼] 在区块 ({}, {}) 生成5层办公楼", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            decidedChunks.remove(chunkKey) // 生成异常时取消"已评估"，允许周期扫描重试
            QLMZombieMod.LOGGER.error("[破败办公楼] 生成失败: {}", e.message)
            false
        }
    }

    /**
     * 跨会话防重复检测：办公楼地面为 SMOOTH_STONE，检查中心位置一/二层地板。
     */
    private fun hasExistingStructure(
        level: net.minecraft.world.level.Level,
        origin: BlockPos
    ): Boolean {
        val centerX = origin.x + WIDTH / 2
        val centerZ = origin.z + DEPTH / 2
        val floor1 = level.getBlockState(BlockPos(centerX, origin.y, centerZ)).block
        val floor2 = level.getBlockState(BlockPos(centerX, origin.y + FLOOR_HEIGHT, centerZ)).block
        return floor1 == Blocks.SMOOTH_STONE && floor2 == Blocks.SMOOTH_STONE
    }

    private fun generateOffice(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk,
        origin: BlockPos.MutableBlockPos
    ) {
        val x0 = origin.x
        val z0 = origin.z
        val groundY = origin.y
        val random = level.random

        // 电梯井 3×3 位置：楼中心
        val elevatorMinX = WIDTH / 2 - ELEVATOR_SIZE / 2
        val elevatorMaxX = elevatorMinX + ELEVATOR_SIZE - 1
        val elevatorMinZ = DEPTH / 2 - ELEVATOR_SIZE / 2
        val elevatorMaxZ = elevatorMinZ + ELEVATOR_SIZE - 1

        // 楼梯井 3×3 位置：电梯井东侧旁边（电梯井X=9..11, 楼梯井X=12..14）
        val stairMinX = elevatorMaxX + 1
        val stairMaxX = stairMinX + STAIRCASE_SIZE - 1
        val stairMinZ = DEPTH / 2 - STAIRCASE_SIZE / 2
        val stairMaxZ = stairMinZ + STAIRCASE_SIZE - 1

        for (floor in 0 until FLOORS) {
            val floorY = groundY + floor * FLOOR_HEIGHT

            // 地板：全部铺满 SMOOTH_STONE
            for (dx in 0 until WIDTH) {
                for (dz in 0 until DEPTH) {
                    val floorPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    level.setBlock(floorPos, Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                }
            }

            // 电梯井填充：每层用混凝土填满（3×3，4格高）
            for (dx in elevatorMinX..elevatorMaxX) {
                for (dz in elevatorMinZ..elevatorMaxZ) {
                    for (dy in 1..FLOOR_HEIGHT) {
                        val elevPos = BlockPos.MutableBlockPos(x0 + dx, floorY + dy, z0 + dz)
                        level.setBlock(elevPos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
                    }
                }
            }

            // 外墙（玻璃幕墙 + LIGHT_GRAY_CONCRETE 框架）
            for (dx in 0 until WIDTH) {
                for (dz in 0 until DEPTH) {
                    val isPerimeter = dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1
                    if (!isPerimeter) continue

                    // 门位置：1楼南侧中央，1格宽×2格高
                    val isDoorColumn = floor == 0 &&
                        dx == WIDTH / 2 && dz == DEPTH - 1

                    for (dy in 1..FLOOR_HEIGHT) {
                        val wallPos = BlockPos.MutableBlockPos(x0 + dx, floorY + dy, z0 + dz)
                        // 跳过门位置（dy=1,2 由 placeDoor1x2 处理，dy=3 是门楣）
                        if (isDoorColumn && dy in 1..3) continue

                        val isFrame = (dx % 2 == 0) || (dz % 2 == 0)
                        val isTopFrame = dy == FLOOR_HEIGHT
                        when {
                            isTopFrame || isFrame ->
                                level.setBlock(wallPos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
                            else ->
                                level.setBlock(wallPos, Blocks.GLASS_PANE.defaultBlockState(), 3)
                        }
                    }
                }
            }

            // 一楼大厅门：1×2 铁门朝南
            if (floor == 0) {
                StructureGenSupport.placeDoor1x2(
                    level,
                    x0 + WIDTH / 2,
                    floorY + 1,
                    z0 + DEPTH - 1,
                    Direction.SOUTH,
                    Blocks.IRON_DOOR as DoorBlock,
                    Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
                )
            }

            // 楼梯井：L形旋转楼梯
            buildStaircase(level, x0, floorY, z0, stairMinX, stairMinZ, floor)

            // 内部隔墙 + 办公室家具
            buildFloorInterior(level, x0, floorY, z0, elevatorMinX, elevatorMaxX, elevatorMinZ, elevatorMaxZ,
                stairMinX, stairMaxX, stairMinZ, stairMaxZ, random, floor)

            // 每层2个普通箱子
            placeFloorChests(level, x0, floorY, z0, random, floor)
        }

        // 屋顶
        val roofY = groundY + FLOORS * FLOOR_HEIGHT
        for (dx in 0 until WIDTH) {
            for (dz in 0 until DEPTH) {
                val roofPos = BlockPos.MutableBlockPos(x0 + dx, roofY, z0 + dz)
                level.setBlock(roofPos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
            }
        }

        // 屋顶女儿墙
        for (dx in 0 until WIDTH) {
            val parapetPos = BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0)
            level.setBlock(parapetPos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
            val parapetPos2 = BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0 + DEPTH - 1)
            level.setBlock(parapetPos2, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
        }
        for (dz in 0 until DEPTH) {
            val parapetPos = BlockPos.MutableBlockPos(x0, roofY + 1, z0 + dz)
            level.setBlock(parapetPos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
            val parapetPos2 = BlockPos.MutableBlockPos(x0 + WIDTH - 1, roofY + 1, z0 + dz)
            level.setBlock(parapetPos2, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
        }

        QLMZombieMod.LOGGER.debug(
            "[破败办公楼] 完成5层办公楼生成, 位置: {}, {}, {}", x0, groundY, z0
        )
    }

    /**
     * 楼梯井：3×3 L形旋转楼梯（参考 HighriseBuildingGenerator 的逻辑）
     */
    private fun buildStaircase(
        level: net.minecraft.world.level.Level,
        x0: Int,
        floorY: Int,
        z0: Int,
        stairMinX: Int,
        stairMinZ: Int,
        floor: Int
    ) {
        val evenFlight = (floor % 2) == 0

        // 楼梯井中心立柱（防止跳下）
        level.setBlock(
            BlockPos.MutableBlockPos(x0 + stairMinX + 1, floorY, z0 + stairMinZ + 1),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3
        )
        level.setBlock(
            BlockPos.MutableBlockPos(x0 + stairMinX + 1, floorY + 1, z0 + stairMinZ + 1),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3
        )

        if (evenFlight) {
            // 飞行 A：东侧上行 (0,2)->(1,2)->(2,2)，再转南 (2,1) 到上一层
            placeStair(level, x0 + stairMinX + 0, floorY + 1, z0 + stairMinZ + 2, Direction.EAST)
            placeStair(level, x0 + stairMinX + 1, floorY + 2, z0 + stairMinZ + 2, Direction.EAST)
            placeStair(level, x0 + stairMinX + 2, floorY + 3, z0 + stairMinZ + 2, Direction.SOUTH)
            placeStair(level, x0 + stairMinX + 2, floorY + 4, z0 + stairMinZ + 1, Direction.SOUTH)
        } else {
            // 飞行 B：西侧上行 (2,0)->(1,0)->(0,0)，再转北 (0,1) 到上一层
            placeStair(level, x0 + stairMinX + 2, floorY + 1, z0 + stairMinZ + 0, Direction.WEST)
            placeStair(level, x0 + stairMinX + 1, floorY + 2, z0 + stairMinZ + 0, Direction.WEST)
            placeStair(level, x0 + stairMinX + 0, floorY + 3, z0 + stairMinZ + 0, Direction.NORTH)
            placeStair(level, x0 + stairMinX + 0, floorY + 4, z0 + stairMinZ + 1, Direction.NORTH)
        }
    }

    private fun placeStair(
        level: net.minecraft.world.level.Level,
        x: Int, y: Int, z: Int,
        facing: Direction
    ) {
        level.setBlock(
            BlockPos.MutableBlockPos(x, y, z),
            Blocks.STONE_STAIRS.defaultBlockState().setValue(
                net.minecraft.world.level.block.StairBlock.FACING, facing
            ),
            3
        )
    }

    /**
     * 内部隔墙：每层4间办公室（东西各2间），中间是电梯井+楼梯井。
     * 办公室内部放 1 个 BOOKSHELF + 1 个 STONE_STAIRS（椅子）
     */
    private fun buildFloorInterior(
        level: net.minecraft.world.level.Level,
        x0: Int, floorY: Int, z0: Int,
        elevMinX: Int, elevMaxX: Int, elevMinZ: Int, elevMaxZ: Int,
        stairMinX: Int, stairMaxX: Int, stairMinZ: Int, stairMaxZ: Int,
        random: net.minecraft.util.RandomSource,
        floor: Int
    ) {
        // 4个办公室区域：
        // 西北: x=1..elevMinX-1, z=1..elevMinZ-1
        // 东北: x=stairMaxX+1..WIDTH-2, z=1..elevMinZ-1
        // 西南: x=1..elevMinX-1, z=elevMaxZ+1..DEPTH-2
        // 东南: x=stairMaxX+1..WIDTH-2, z=elevMaxZ+1..DEPTH-2

        // 东西向中间墙（z = elevMinZ 行，从西墙到电梯井西侧；从电梯井东侧到楼梯井西侧；从楼梯井东侧到东墙）
        val midZ = elevMinZ - 1
        val midZ2 = elevMaxZ + 1

        // 北侧办公室与走廊隔墙 (z=midZ 行)
        for (dx in 1 until elevMinX) {
            placeInternalWall(level, x0 + dx, floorY, z0 + midZ)
        }
        for (dx in stairMaxX + 1 until WIDTH - 1) {
            placeInternalWall(level, x0 + dx, floorY, z0 + midZ)
        }
        // 南侧办公室与走廊隔墙 (z=midZ2 行)
        for (dx in 1 until elevMinX) {
            placeInternalWall(level, x0 + dx, floorY, z0 + midZ2)
        }
        for (dx in stairMaxX + 1 until WIDTH - 1) {
            placeInternalWall(level, x0 + dx, floorY, z0 + midZ2)
        }

        // 办公室之间的南北向隔墙（电梯井西侧 x=elevMinX-1；楼梯井东侧 x=stairMaxX+1 不需要，已经是走廊东）
        val westDivX = elevMinX - 1
        for (dz in 1..midZ) {
            placeInternalWall(level, x0 + westDivX, floorY, z0 + dz)
        }
        for (dz in midZ2 until DEPTH - 1) {
            placeInternalWall(level, x0 + westDivX, floorY, z0 + dz)
        }
        val eastDivX = stairMaxX + 1
        for (dz in 1..midZ) {
            placeInternalWall(level, x0 + eastDivX, floorY, z0 + dz)
        }
        for (dz in midZ2 until DEPTH - 1) {
            placeInternalWall(level, x0 + eastDivX, floorY, z0 + dz)
        }

        // 4间办公室内的家具：1个BOOKSHELF + 1个STONE_STAIRS（椅子）
        val offices = listOf(
            Triple(2, 2, 2),                                          // 西北
            Triple(stairMaxX + 2, 2, 2),                              // 东北
            Triple(2, 2, DEPTH - 3),                                  // 西南
            Triple(stairMaxX + 2, 2, DEPTH - 3),                      // 东南
        )

        for ((offX, _, offZ) in offices) {
            // BOOKSHELF
            val shelfPos = BlockPos.MutableBlockPos(x0 + offX, floorY + 1, z0 + offZ)
            level.setBlock(shelfPos, Blocks.BOOKSHELF.defaultBlockState(), 3)
            // STONE_STAIRS 椅子
            val chairPos = BlockPos.MutableBlockPos(x0 + offX + 1, floorY + 1, z0 + offZ)
            level.setBlock(
                chairPos,
                Blocks.STONE_STAIRS.defaultBlockState().setValue(
                    net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH
                ),
                3
            )
        }
    }

    private fun placeInternalWall(
        level: net.minecraft.world.level.Level,
        x: Int, floorY: Int, z: Int
    ) {
        for (dy in 1..2) {
            val pos = BlockPos.MutableBlockPos(x, floorY + dy, z)
            level.setBlock(pos, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
        }
    }

    /**
     * 每层2个普通箱子（科技/办公用品）。
     * 顶层董事长办公室位置用 1 个 CDBlocks.SUPPLY_CRATE 替代普通箱子。
     */
    private fun placeFloorChests(
        level: net.minecraft.world.level.Level,
        x0: Int, floorY: Int, z0: Int,
        random: net.minecraft.util.RandomSource,
        floor: Int
    ) {
        val chestPositions = mutableListOf<Triple<Int, Int, Int>>()

        if (floor == FLOORS - 1) {
            // 顶层：董事长办公室（西北办公室角落）放 SUPPLY_CRATE + 1 个普通箱子
            val ceoPos = BlockPos(x0 + 3, floorY + 1, z0 + 3)
            level.setBlock(ceoPos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
            StructureGenSupport.fillCDCrate(level, ceoPos, random, officeLoot, 0.3, 4, 8)

            chestPositions.add(Triple(14, floorY + 1, DEPTH - 4)) // 东南办公室普通箱子
        } else {
            // 非顶层：2个普通箱子
            chestPositions.add(Triple(3, floorY + 1, 3))           // 西北
            chestPositions.add(Triple(WIDTH - 4, floorY + 1, DEPTH - 4)) // 东南
        }

        for ((cx, cy, cz) in chestPositions) {
            val pos = BlockPos(x0 + cx, cy, z0 + cz)
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, pos, random, officeLoot, MOD_ITEM_CHANCE, 2, 5)
            StructureGenSupport.maybeInjectTaczWeapon(level, pos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
        }
    }
}
