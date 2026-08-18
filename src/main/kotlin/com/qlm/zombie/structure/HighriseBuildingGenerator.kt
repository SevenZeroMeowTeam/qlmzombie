package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BiomeTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

object HighriseBuildingGenerator : BuildingGenerator {

    private const val BUILDING_WIDTH = 13
    private const val BUILDING_DEPTH = 9
    private const val FLOORS = 9
    private const val FLOOR_HEIGHT = 4
    private const val STAIRCASE_SIZE = 3
    private const val MOD_ITEM_CHANCE = 0.5

    /** 每区块仅评估一次（无论是否生成），避免重复扫描时反复掷概率 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    /** 高楼样式（每栋高楼样式不同）：外墙材质 + 内部房间材质 */
    private data class BuildingStyle(val wall: net.minecraft.world.level.block.Block, val room: net.minecraft.world.level.block.Block)
    private val STYLES = listOf(
        BuildingStyle(Blocks.STONE_BRICKS, Blocks.OAK_PLANKS),          // 石砖办公楼
        BuildingStyle(Blocks.BRICKS, Blocks.SPRUCE_PLANKS),             // 红砖公寓
        BuildingStyle(Blocks.QUARTZ_BLOCK, Blocks.WHITE_WOOL),          // 石英写字楼
        BuildingStyle(Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE), // 现代混凝土楼
        BuildingStyle(Blocks.PRISMARINE, Blocks.DARK_OAK_PLANKS),       // 海晶石复古楼
    )

    // 延迟初始化：RegistryObject.get() 必须在注册表完成注册后调用，
    // 类静态初始化时（CONSTRUCT 阶段）调用会抛出 NPE。
    private val modLoot by lazy {
        listOf(
            QLMItems.ZOMBIE_CORE.get(),
            QLMItems.INFECTED_ESSENCE.get(),
            QLMItems.SURVIVAL_KIT.get(),
            QLMItems.ANTIDOTE.get(),
            QLMItems.MEDICAL_SUPPLY.get(),
            QLMItems.REINFORCED_PARTS.get(),
            QLMItems.BIOHAZARD_SAMPLE.get(),
            QLMItems.EMERGENCY_RATION.get(),
            QLMItems.TACTICAL_AMMO.get(),
            CDItems.BANDAGE.get(),
            CDItems.FIRST_AID_KIT.get(),
            CDItems.RIFLE_AMMO.get(),
            CDItems.PISTOL_AMMO.get(),
            CDItems.SHOTGUN_SHELL.get(),
            CDItems.SNIPER_AMMO.get(),
            CDItems.BALLISTIC_HELMET.get(),
            CDItems.PLATE_CARRIER.get(),
            CDItems.TACTICAL_VEST.get(),
        )
    }

    private val vanillaLoot = listOf(
        Items.IRON_INGOT,
        Items.GOLD_INGOT,
        Items.DIAMOND,
        Items.COAL,
        Items.IRON_BLOCK,
        Items.GOLD_BLOCK,
        Items.BREAD,
        Items.COOKED_BEEF,
        Items.APPLE,
        Items.BUCKET,
    )

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
        if (biome.`is`(BiomeTags.IS_OCEAN)) return false

        // 平面检测：高楼需要平坦地形
        if (!StructureGenSupport.isFlatTerrain(chunk, QLMConfig.FLAT_TOLERANCE_MEDIUM.get())) return false

        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, QLMConfig.HIGHRISE_SPACING.get())) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - BUILDING_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - BUILDING_DEPTH / 2
        )

        // 跨会话防重复：若建筑标志（双层 STONE 地板）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            StructureGenSupport.generatedChunks.add(chunkKey)
            return false
        }

        if (level.random.nextDouble() >= QLMConfig.HIGHRISE_CHANCE.get()) return false

        return try {
            generateHighrise(level, chunk, origin)
            StructureGenSupport.generatedChunks.add(chunkKey)
            StructureGenSupport.registerBuilding(chunkKey, net.minecraft.core.BlockPos(origin.x + BUILDING_WIDTH / 2, origin.y, origin.z + BUILDING_DEPTH / 2))
            QLMZombieMod.LOGGER.info(
                "[高层建筑] 在区块 ({}, {}) 生成9层高楼", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            decidedChunks.remove(chunkKey) // 生成异常时取消"已评估"，允许周期扫描重试
            QLMZombieMod.LOGGER.error("[高层建筑] 生成失败: {}", e.message)
            false
        }
    }

    /**
     * 检测目标位置是否已存在本生成器产出的建筑。
     * 高层建筑地板为 STONE，检查建筑中心一/二层地板位置是否均为 STONE。
     * 自然地形几乎不会在空中出现连续两层 STONE，可作为可靠标志。
     * 用于跨会话防重复：generatedChunks 是内存 Set，重启后清空。
     */
    private fun hasExistingStructure(
        level: net.minecraft.world.level.Level,
        origin: BlockPos
    ): Boolean {
        val centerX = origin.x + BUILDING_WIDTH / 2
        val centerZ = origin.z + BUILDING_DEPTH / 2
        val floor1 = level.getBlockState(BlockPos(centerX, origin.y, centerZ)).block
        val floor2 = level.getBlockState(BlockPos(centerX, origin.y + FLOOR_HEIGHT, centerZ)).block
        return floor1 == net.minecraft.world.level.block.Blocks.STONE &&
            floor2 == net.minecraft.world.level.block.Blocks.STONE
    }

    private fun generateHighrise(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk,
        origin: BlockPos.MutableBlockPos
    ) {
        val x0 = origin.x
        val z0 = origin.z
        val groundY = origin.y
        val random = level.random

        // 每栋高楼样式不同（随机选择一种风格）
        val style = STYLES[random.nextInt(STYLES.size)]

        val staircaseMinX = BUILDING_WIDTH / 2 - STAIRCASE_SIZE / 2
        val staircaseMaxX = staircaseMinX + STAIRCASE_SIZE - 1
        val staircaseMinZ = BUILDING_DEPTH / 2 - STAIRCASE_SIZE / 2
        val staircaseMaxZ = staircaseMinZ + STAIRCASE_SIZE - 1

        for (floor in 0 until FLOORS) {
            val floorY = groundY + floor * FLOOR_HEIGHT

            // 地板：全部铺满（包括楼梯井区域，作为每层地面和上一层landing）
            // 修复：原代码 if (isStaircaseArea) continue 导致楼梯井无地板，玩家踩空
            //
            // 楼梯防卡头：上一层台阶 S2/S3 正上方的楼板格开洞并铺薄地毯。
            // 玩家站在台阶上时身高（台阶顶+1.8格）会顶到楼板导致无法继续通行，
            // 必须打通；洞口用 0.5 格以下的薄地毯封住，玩家既不会卡头也不会踩空掉落。
            val floorHoles = mutableListOf<Pair<Int, Int>>()
            if (floor > 0) {
                val prevEvenFlight = ((floor - 1) % 2 == 0)
                if (prevEvenFlight) {
                    floorHoles.add(Pair(staircaseMinX + 1, staircaseMinZ + 2)) // S2(6,y+2,5) 正上方
                    floorHoles.add(Pair(staircaseMinX + 2, staircaseMinZ + 2)) // S3(7,y+3,5) 正上方
                } else {
                    floorHoles.add(Pair(staircaseMinX + 1, staircaseMinZ))     // S2(6,y+2,3) 正上方
                    floorHoles.add(Pair(staircaseMinX, staircaseMinZ))         // S3(5,y+3,3) 正上方
                }
            }
            for (dx in 0 until BUILDING_WIDTH) {
                for (dz in 0 until BUILDING_DEPTH) {
                    val floorPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    if (Pair(dx, dz) in floorHoles) {
                        level.setBlock(floorPos, Blocks.GRAY_CARPET.defaultBlockState(), 3)
                    } else {
                        level.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3)
                    }
                }
            }

            // 外墙（按样式）
            for (dx in 0 until BUILDING_WIDTH) {
                for (dz in 0 until BUILDING_DEPTH) {
                    val isPerimeter = dx == 0 || dx == BUILDING_WIDTH - 1 ||
                        dz == 0 || dz == BUILDING_DEPTH - 1
                    if (!isPerimeter) continue

                    // 门位置：1楼南侧中间，1格宽×2格高
                    val isDoorColumn = floor == 0 &&
                        dx == BUILDING_WIDTH / 2 && dz == BUILDING_DEPTH - 1

                    for (dy in 1..FLOOR_HEIGHT) {
                        val wallPos = BlockPos.MutableBlockPos(x0 + dx, floorY + dy, z0 + dz)
                        // 跳过门位置（dy=1,2 由 placeDoor1x2 处理，dy=3 是门楣）
                        if (isDoorColumn && dy in 1..3) continue
                        val isWindow = dy == 2 && random.nextDouble() < 0.6

                        when {
                            isWindow ->
                                level.setBlock(wallPos, Blocks.GLASS.defaultBlockState(), 3)
                            else ->
                                level.setBlock(wallPos, style.wall.defaultBlockState(), 3)
                        }
                    }
                }
            }

            // 1格宽×2格高门：方便其他模组防御物品在门口留通道进出
            if (floor == 0) {
                StructureGenSupport.placeDoor1x2(
                    level,
                    x0 + BUILDING_WIDTH / 2,
                    floorY + 1,
                    z0 + BUILDING_DEPTH - 1,
                    Direction.SOUTH,
                    Blocks.IRON_DOOR as DoorBlock,
                    style.wall.defaultBlockState()
                )
            }

            // 楼梯井：每层建造可通行楼梯（从本层通到上一层）
            buildStaircase(level, x0, floorY, z0, staircaseMinX, staircaseMinZ, style, random, floor)

            if (floor == 0) {
                buildFirstFloorRooms(level, x0, floorY, z0, staircaseMinX, staircaseMaxX,
                    staircaseMinZ, staircaseMaxZ, random, style)
            } else {
                buildUpperFloorRooms(level, x0, floorY, z0, staircaseMinX, staircaseMaxX,
                    staircaseMinZ, staircaseMaxZ, random, floor, style)
            }
        }

        val roofY = groundY + FLOORS * FLOOR_HEIGHT
        for (dx in 0 until BUILDING_WIDTH) {
            for (dz in 0 until BUILDING_DEPTH) {
                val roofPos = BlockPos.MutableBlockPos(x0 + dx, roofY, z0 + dz)
                level.setBlock(roofPos, Blocks.STONE.defaultBlockState(), 3)
            }
        }

        for (dx in 0 until BUILDING_WIDTH) {
            val parapetPos = BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0)
            level.setBlock(parapetPos, style.wall.defaultBlockState(), 3)
            val parapetPos2 = BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0 + BUILDING_DEPTH - 1)
            level.setBlock(parapetPos2, style.wall.defaultBlockState(), 3)
        }
        for (dz in 0 until BUILDING_DEPTH) {
            val parapetPos = BlockPos.MutableBlockPos(x0, roofY + 1, z0 + dz)
            level.setBlock(parapetPos, style.wall.defaultBlockState(), 3)
            val parapetPos2 = BlockPos.MutableBlockPos(x0 + BUILDING_WIDTH - 1, roofY + 1, z0 + dz)
            level.setBlock(parapetPos2, style.wall.defaultBlockState(), 3)
        }

        QLMZombieMod.LOGGER.debug(
            "[高层建筑] 完成9层高楼生成(样式: {}), 位置: {}, {}, {}", style.wall, x0, groundY, z0
        )
    }

    /**
     * 楼梯井：在 3×3 楼梯井内建造可通行的 L 形旋转楼梯（每层升高 FLOOR_HEIGHT=4）
     * 地板已在 generateHighrise 中为楼梯井留空。
     */
    private fun buildStaircase(
        level: net.minecraft.world.level.Level,
        x0: Int,
        floorY: Int,
        z0: Int,
        stairMinX: Int,
        stairMinZ: Int,
        style: BuildingStyle,
        random: net.minecraft.util.RandomSource,
        floor: Int
    ) {
        // 楼梯井局部坐标 (0..2, 0..2)，两种飞行交替，每飞行 4 级台阶
        // 飞行 A（偶数层）：(0,2)->(1,2)->(2,2)->(2,1)
        // 飞行 B（奇数层）：(2,0)->(1,0)->(0,0)->(0,1)
        // 修复：用楼层号 floor%2 而非绝对Y坐标 (floorY/FLOOR_HEIGHT)%2 判断奇偶
        val evenFlight = (floor % 2) == 0

        // 楼梯井中心立柱（防止跳下楼梯井）
        level.setBlock(
            BlockPos.MutableBlockPos(x0 + stairMinX + 1, floorY, z0 + stairMinZ + 1),
            style.wall.defaultBlockState(), 3
        )
        // 中心立柱向上延伸（每层一层）
        level.setBlock(
            BlockPos.MutableBlockPos(x0 + stairMinX + 1, floorY + 1, z0 + stairMinZ + 1),
            style.wall.defaultBlockState(), 3
        )

        if (evenFlight) {
            // 飞行 A：东侧上行 (0,2)->(1,2)->(2,2)，再转南 (2,1) 到上一层
            placeStair(level, x0 + stairMinX + 0, floorY + 1, z0 + stairMinZ + 2, net.minecraft.core.Direction.EAST)
            placeStair(level, x0 + stairMinX + 1, floorY + 2, z0 + stairMinZ + 2, net.minecraft.core.Direction.EAST)
            placeStair(level, x0 + stairMinX + 2, floorY + 3, z0 + stairMinZ + 2, net.minecraft.core.Direction.SOUTH)
            placeStair(level, x0 + stairMinX + 2, floorY + 4, z0 + stairMinZ + 1, net.minecraft.core.Direction.SOUTH)
        } else {
            // 飞行 B：西侧上行 (2,0)->(1,0)->(0,0)，再转北 (0,1) 到上一层
            placeStair(level, x0 + stairMinX + 2, floorY + 1, z0 + stairMinZ + 0, net.minecraft.core.Direction.WEST)
            placeStair(level, x0 + stairMinX + 1, floorY + 2, z0 + stairMinZ + 0, net.minecraft.core.Direction.WEST)
            placeStair(level, x0 + stairMinX + 0, floorY + 3, z0 + stairMinZ + 0, net.minecraft.core.Direction.NORTH)
            placeStair(level, x0 + stairMinX + 0, floorY + 4, z0 + stairMinZ + 1, net.minecraft.core.Direction.NORTH)
        }
    }

    private fun placeStair(
        level: net.minecraft.world.level.Level,
        x: Int,
        y: Int,
        z: Int,
        facing: net.minecraft.core.Direction
    ) {
        level.setBlock(
            BlockPos.MutableBlockPos(x, y, z),
            Blocks.STONE_STAIRS.defaultBlockState().setValue(
                net.minecraft.world.level.block.StairBlock.FACING, facing
            ),
            3
        )
    }

    private fun buildFirstFloorRooms(
        level: net.minecraft.world.level.Level,
        x0: Int,
        floorY: Int,
        z0: Int,
        stairMinX: Int, stairMaxX: Int,
        stairMinZ: Int, stairMaxZ: Int,
        random: net.minecraft.util.RandomSource,
        style: BuildingStyle
    ) {
        val halfWidth = BUILDING_WIDTH / 2
        val halfDepth = BUILDING_DEPTH / 2

        val internalWallPositions = listOf(
            Pair(halfWidth, 1) to Pair(halfWidth, stairMinZ - 1),
            Pair(halfWidth, stairMaxZ + 1) to Pair(halfWidth, BUILDING_DEPTH - 2),
            Pair(1, halfDepth) to Pair(stairMinX - 1, halfDepth),
            Pair(stairMaxX + 1, halfDepth) to Pair(BUILDING_WIDTH - 2, halfDepth),
        )

        for ((start, end) in internalWallPositions) {
            if (start.first == end.first) {
                val wx = start.first
                for (wz in start.second..end.second) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + wx, floorY + 1, z0 + wz)
                    level.setBlock(wallPos, style.room.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, style.room.defaultBlockState(), 3)
                }
            } else {
                val wz = start.second
                for (wx in start.first..end.first) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + wx, floorY + 1, z0 + wz)
                    level.setBlock(wallPos, style.room.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, style.room.defaultBlockState(), 3)
                }
            }
        }

        val roomCenters = listOf(
            Triple(1, floorY, 1),
            Triple(BUILDING_WIDTH - 2, floorY, 1),
            Triple(1, floorY, BUILDING_DEPTH - 2),
            Triple(BUILDING_WIDTH - 2, floorY, BUILDING_DEPTH - 2),
            Triple(stairMaxX + 2, floorY, stairMaxZ + 2),
        )

        for ((index, rc) in roomCenters.withIndex()) {
            val (rx, ry, rz) = rc
            val chestPos = BlockPos.MutableBlockPos(x0 + rx, ry, z0 + rz)
            if (index == roomCenters.size - 1) {
                // 楼梯旁房间：CD 补给箱（Crafting Dead 模组物资）
                level.setBlock(chestPos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
                StructureGenSupport.fillCDCrate(level, chestPos.immutable(), random, modLoot, 0.3, 4, 8)
                StructureGenSupport.maybeInjectTaczWeapon(level, chestPos.immutable(), random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
            } else {
                level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
                fillChestWithLoot(level, chestPos, random)
            }
        }
    }

    private fun buildUpperFloorRooms(
        level: net.minecraft.world.level.Level,
        x0: Int,
        floorY: Int,
        z0: Int,
        stairMinX: Int, stairMaxX: Int,
        stairMinZ: Int, stairMaxZ: Int,
        random: net.minecraft.util.RandomSource,
        floor: Int,
        style: BuildingStyle
    ) {
        val halfWidth = BUILDING_WIDTH / 2
        val halfDepth = BUILDING_DEPTH / 2

        val internalWallPositions = listOf(
            Pair(halfWidth, 1) to Pair(halfWidth, stairMinZ - 1),
            Pair(halfWidth, stairMaxZ + 1) to Pair(halfWidth, BUILDING_DEPTH - 2),
            Pair(1, halfDepth) to Pair(stairMinX - 1, halfDepth),
            Pair(stairMaxX + 1, halfDepth) to Pair(BUILDING_WIDTH - 2, halfDepth),
        )

        for ((start, end) in internalWallPositions) {
            if (start.first == end.first) {
                val wx = start.first
                for (wz in start.second..end.second) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + wx, floorY + 1, z0 + wz)
                    level.setBlock(wallPos, style.room.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, style.room.defaultBlockState(), 3)
                }
            } else {
                val wz = start.second
                for (wx in start.first..end.first) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + wx, floorY + 1, z0 + wz)
                    level.setBlock(wallPos, style.room.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, style.room.defaultBlockState(), 3)
                }
            }
        }

        val roomCenters = listOf(
            Triple(1, floorY, 1),
            Triple(BUILDING_WIDTH - 2, floorY, 1),
            Triple(1, floorY, BUILDING_DEPTH - 2),
            Triple(BUILDING_WIDTH - 2, floorY, BUILDING_DEPTH - 2),
            Triple(stairMaxX + 2, floorY, stairMaxZ + 2),
        )

        for ((index, rc) in roomCenters.withIndex()) {
            val (rx, ry, rz) = rc
            val chestPos = BlockPos.MutableBlockPos(x0 + rx, ry, z0 + rz)
            if (index == roomCenters.size - 1) {
                // 楼梯旁房间：CD 补给箱（Crafting Dead 模组物资）
                level.setBlock(chestPos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
                StructureGenSupport.fillCDCrate(level, chestPos.immutable(), random, modLoot, 0.3, 4, 8)
                StructureGenSupport.maybeInjectTaczWeapon(level, chestPos.immutable(), random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
            } else {
                level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
                fillChestWithLoot(level, chestPos, random)
            }
        }

        if (floor == FLOORS - 1) {
            for (dx in 0 until BUILDING_WIDTH) {
                for (dz in 0 until BUILDING_DEPTH) {
                    val isCorner = (dx == 0 || dx == BUILDING_WIDTH - 1) &&
                        (dz == 0 || dz == BUILDING_DEPTH - 1)
                    if (isCorner) {
                        val pillarPos = BlockPos.MutableBlockPos(x0 + dx, floorY + 3, z0 + dz)
                        level.setBlock(pillarPos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
                    }
                }
            }
        }
    }

    private fun fillChestWithLoot(
        level: net.minecraft.world.level.Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource
    ) {
        // 主题战利品（本模组 + 原版）+ 自动扫描的其他模组物品
        val combinedThemed = modLoot + vanillaLoot
        StructureGenSupport.fillChest(level, pos, random, combinedThemed, MOD_ITEM_CHANCE, 2, 5)
        // 5% 保底 TACZ 武器
        StructureGenSupport.maybeInjectTaczWeapon(level, pos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
    }
}