package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 破败商业广场生成器（32×32，跨2×2区块）：
 * - 地面全部铺 COBBLESTONE
 * - 中央圆形喷泉（半径5，STONE_BRICKS 圆环 + 中心 WATER）
 * - 四角4栋2层小楼（每栋8×8，FLOOR_HEIGHT=3）：服装/餐饮/电子/百货
 * - 喷泉下方"地下宝箱"：喷泉中心 y0 层放 CDBlocks.SUPPLY_CRATE 武器主题
 */
object CommercialPlazaGenerator : BuildingGenerator {

    private const val WIDTH = 32
    private const val DEPTH = 32
    private const val SIZE_CHUNKS = 2
    private const val BUILDING_SIZE = 8
    private const val FLOOR_HEIGHT = 3
    private const val MOD_ITEM_CHANCE = 0.4
    private const val FOUNTAIN_RADIUS = 5

    /** 每区块仅评估一次 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    /** 西北角：服装主题 */
    private val clothingLoot by lazy {
        listOf(
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
            Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL,
            Items.LEATHER_LEGGINGS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET,
            Items.LEATHER_BOOTS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS,
        )
    }

    /** 东北角：餐饮主题 */
    private val foodLoot by lazy {
        listOf(
            Items.BREAD, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
            Items.MUTTON, Items.COOKED_MUTTON, Items.APPLE, Items.GOLDEN_APPLE,
            Items.BAKED_POTATO, Items.COOKIE, Items.PUMPKIN_PIE, Items.RABBIT_STEW,
            Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.COCOA_BEANS,
            QLMItems.EMERGENCY_RATION.get(), QLMItems.MEDICAL_SUPPLY.get(),
        )
    }

    /** 西南角：电子主题 */
    private val electronicsLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE,
            Items.REDSTONE_BLOCK, Items.EMERALD, Items.DIAMOND,
            Items.HOPPER, Items.DISPENSER, Items.DROPPER,
            Items.OBSERVER, Items.COMPARATOR, Items.REPEATER,
            Items.PISTON, Items.STICKY_PISTON, Items.TRIPWIRE_HOOK,
            QLMItems.REINFORCED_PARTS.get(), QLMItems.TACTICAL_AMMO.get(),
        )
    }

    /** 东南角：百货（混合） */
    private val mixedLoot by lazy {
        listOf(
            Items.BREAD, Items.COOKED_BEEF, Items.PORKCHOP, Items.MUTTON,
            Items.APPLE, Items.GOLDEN_APPLE,
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.RED_WOOL,
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.REDSTONE_BLOCK,
            Items.EMERALD, Items.DIAMOND,
            Items.PAPER, Items.BOOK, Items.BOOKSHELF,
            Items.LEATHER, Items.STRING, Items.TORCH,
            Items.CHEST, Items.GLASS_BOTTLE, Items.FEATHER,
            QLMItems.EMERGENCY_RATION.get(), QLMItems.MEDICAL_SUPPLY.get(),
            QLMItems.REINFORCED_PARTS.get(), QLMItems.SURVIVAL_KIT.get(),
            QLMItems.ANTIDOTE.get(), QLMItems.ZOMBIE_CORE.get(),
            QLMItems.TACTICAL_AMMO.get(),
        )
    }

    /** 武器主题（地下宝箱用） */
    private val weaponLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_SWORD,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
            Items.DIAMOND_SWORD, Items.BOW, Items.ARROW,
            Items.CROSSBOW, Items.SHIELD,
            QLMItems.TACTICAL_AMMO.get(), QLMItems.REINFORCED_PARTS.get(),
            QLMItems.BIOHAZARD_SAMPLE.get(),
        )
    }

    /** 4栋楼信息：(相对偏移x, 相对偏移z, 门朝向, 战利品, 外墙材质, 标签) */
    private data class BuildingInfo(
        val offX: Int, val offZ: Int,
        val doorFacing: Direction,
        val loot: List<Item>,
        val wall: net.minecraft.world.level.block.Block,
        val label: String,
    )

    private val buildingInfos by lazy {
        listOf(
            // 西北：门朝广场中心 SOUTH（(x0+2, z0+2)，门在building_z+0，朝SOUTH→即向广场方向）
            BuildingInfo(2, 2, Direction.SOUTH, clothingLoot, Blocks.BLUE_CONCRETE, "服装店"),
            // 东北：门朝广场中心 WEST（(x0+22, z0+2)，门在 x0+22+8-1=x0+29，朝WEST→广场）
            BuildingInfo(22, 2, Direction.WEST, foodLoot, Blocks.RED_CONCRETE, "餐饮店"),
            // 西南：门朝广场中心 NORTH（(x0+2, z0+22)，门在 z0+22+8-1=z0+29，朝NORTH→广场）
            BuildingInfo(2, 22, Direction.NORTH, electronicsLoot, Blocks.GRAY_CONCRETE, "电子店"),
            // 东南：门朝广场中心 EAST（(x0+22, z0+22)，门在 x0+22+0，朝EAST→广场）
            BuildingInfo(22, 22, Direction.EAST, mixedLoot, Blocks.YELLOW_CONCRETE, "百货店"),
        )
    }

    override fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        // 仅在 chunkX%2==0 && chunkZ%2==0 锚点触发（跨2×2区块）
        if (chunkX % SIZE_CHUNKS != 0 || chunkZ % SIZE_CHUNKS != 0) return false

        val chunkKey = StructureGenSupport.chunkKey(chunkX, chunkZ)
        if (StructureGenSupport.generatedChunks.contains(chunkKey)) return false

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 0) return false

        val biome = level.getBiome(
            BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)
        )
        if (biome.`is`(net.minecraft.tags.BiomeTags.IS_OCEAN)) return false

        // 标记2×2全部区块为已评估
        for (dx in 0 until SIZE_CHUNKS) {
            for (dz in 0 until SIZE_CHUNKS) {
                decidedChunks.add(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
            }
        }

        // 跨区块平面检测（2×2，FLAT_TOLERANCE_LARGE）
        // isFlatTerrainArea 用 sizeChunks × sizeChunks，centerChunkX 是"中心"，这里 2×2 的"中心"应该是 chunkX+1, chunkZ+1
        if (!StructureGenSupport.isFlatTerrainArea(level, chunkX + 1, chunkZ + 1, SIZE_CHUNKS * 2, QLMConfig.FLAT_TOLERANCE_LARGE.get())) {
            for (dx in 0 until SIZE_CHUNKS) {
                for (dz in 0 until SIZE_CHUNKS) {
                    decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
                }
            }
            return false
        }

        // 跨区块间距检测
        if (!StructureGenSupport.isFarEnoughArea(chunkX + 1, chunkZ + 1, SIZE_CHUNKS * 2, QLMConfig.COMMERCIAL_PLAZA_SPACING.get())) {
            for (dx in 0 until SIZE_CHUNKS) {
                for (dz in 0 until SIZE_CHUNKS) {
                    decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
                }
            }
            return false
        }

        // 概率判定
        if (level.random.nextDouble() >= QLMConfig.COMMERCIAL_PLAZA_CHANCE.get()) return false

        // 强制加载2×2区块
        val serverLevel = level as? ServerLevel ?: return false
        for (dx in 0 until SIZE_CHUNKS) {
            for (dz in 0 until SIZE_CHUNKS) {
                serverLevel.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true)
            }
        }

        val originX = chunkX * 16
        val originZ = chunkZ * 16
        val originY = surfaceY

        return try {
            generatePlaza(level, originX, originY, originZ, level.random)
            // 标记2×2=4个chunkKey到generatedChunks
            for (dx in 0 until SIZE_CHUNKS) {
                for (dz in 0 until SIZE_CHUNKS) {
                    val ck = StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz)
                    StructureGenSupport.generatedChunks.add(ck)
                }
            }
            StructureGenSupport.registerBuilding(
                chunkKey,
                BlockPos(originX + WIDTH / 2, originY, originZ + DEPTH / 2)
            )
            QLMZombieMod.LOGGER.info(
                "[破败商业广场] 在区块 ({}, {}) 生成32×32商业广场", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            for (dx in 0 until SIZE_CHUNKS) {
                for (dz in 0 until SIZE_CHUNKS) {
                    decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
                }
            }
            QLMZombieMod.LOGGER.error("[破败商业广场] 生成失败: {}", e.message)
            false
        }
    }

    private fun generatePlaza(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        // ===== 1. 地面全部铺 COBBLESTONE =====
        for (dx in 0 until WIDTH) {
            for (dz in 0 until DEPTH) {
                level.setBlock(
                    BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.COBBLESTONE.defaultBlockState(), 3
                )
            }
        }

        // ===== 2. 中央喷泉（中心 x0+16, z0+16，半径5） =====
        val fountCx = x0 + 16
        val fountCz = z0 + 16
        // y0+1 层放 STONE_BRICKS 圆环，dist<=1 放水（WATER）
        for (dx in -FOUNTAIN_RADIUS..FOUNTAIN_RADIUS) {
            for (dz in -FOUNTAIN_RADIUS..FOUNTAIN_RADIUS) {
                val dist = Math.sqrt((dx * dx + dz * dz).toDouble())
                if (dist <= FOUNTAIN_RADIUS) {
                    val fx = fountCx + dx
                    val fz = fountCz + dz
                    // STONE_BRICKS 圆环（所有半径内的圆范围都铺 y0+1 层的 STONE_BRICKS）
                    level.setBlock(
                        BlockPos.MutableBlockPos(fx, y0 + 1, fz),
                        Blocks.STONE_BRICKS.defaultBlockState(), 3
                    )
                    // 最内层 dist<=1：把 y0+1 的 STONE_BRICKS 替换为 WATER
                    if (dist <= 1.0) {
                        level.setBlock(
                            BlockPos.MutableBlockPos(fx, y0 + 1, fz),
                            Blocks.WATER.defaultBlockState(), 3
                        )
                    }
                }
            }
        }
        // 喷泉圆环边缘加高一格装饰（y0+2）
        for (dx in -FOUNTAIN_RADIUS..FOUNTAIN_RADIUS) {
            for (dz in -FOUNTAIN_RADIUS..FOUNTAIN_RADIUS) {
                val dist = Math.sqrt((dx * dx + dz * dz).toDouble())
                if (dist > (FOUNTAIN_RADIUS - 1.5) && dist <= FOUNTAIN_RADIUS) {
                    val fx = fountCx + dx
                    val fz = fountCz + dz
                    level.setBlock(
                        BlockPos.MutableBlockPos(fx, y0 + 2, fz),
                        Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(
                            net.minecraft.world.level.block.StairBlock.FACING,
                            getOutwardDirection(dx, dz)
                        ), 3
                    )
                }
            }
        }

        // ===== 3. 喷泉下方"地下宝箱"：喷泉中心 y0 层放 CDBlocks.SUPPLY_CRATE =====
        val undergroundPos = BlockPos(fountCx, y0, fountCz)
        // 替换 y0 层原本的 COBBLESTONE 为 SUPPLY_CRATE
        level.setBlock(undergroundPos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
        StructureGenSupport.fillCDCrate(level, undergroundPos, random, weaponLoot, 0.3, 6, 12)

        // ===== 4. 四角4栋2层小楼 =====
        for (info in buildingInfos) {
            buildTwoStoryBuilding(
                level,
                x0 + info.offX, y0, z0 + info.offZ,
                info.doorFacing, info.loot, info.wall, info.label, random
            )
        }
    }

    /**
     * 从 (dx, dz) 相对喷泉中心的偏移计算"朝外"的方向。
     */
    private fun getOutwardDirection(dx: Int, dz: Int): Direction {
        val absDx = Math.abs(dx)
        val absDz = Math.abs(dz)
        return when {
            absDx >= absDz && dx >= 0 -> Direction.EAST
            absDx >= absDz -> Direction.WEST
            dz >= 0 -> Direction.SOUTH
            else -> Direction.NORTH
        }
    }

    /**
     * 建一栋 8×8 的2层小楼，每层 FLOOR_HEIGHT=3。
     * 1层1个箱子 (x+2, y0+1, z+2)，2层1个箱子 (x+5, y0+4, z+5)。
     * 门朝广场中心开。
     */
    private fun buildTwoStoryBuilding(
        level: net.minecraft.world.level.Level,
        bx: Int, y0: Int, bz: Int,
        doorFacing: Direction,
        loot: List<Item>,
        wallBlock: net.minecraft.world.level.block.Block,
        label: String,
        random: net.minecraft.util.RandomSource
    ) {
        val W = BUILDING_SIZE
        val D = BUILDING_SIZE

        for (floor in 0 until 2) {
            val floorY = y0 + floor * FLOOR_HEIGHT

            // 地板（COBBLESTONE 已铺，楼内用 OAK_PLANKS 覆盖更美观）
            for (dx in 0 until W) {
                for (dz in 0 until D) {
                    level.setBlock(
                        BlockPos.MutableBlockPos(bx + dx, floorY, bz + dz),
                        Blocks.OAK_PLANKS.defaultBlockState(), 3
                    )
                }
            }

            // 墙壁：FLOOR_HEIGHT 格高（3格 = 2格空间 + 1格顶梁/楼板合并）
            for (dx in 0 until W) {
                for (dz in 0 until D) {
                    val isWall = dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1
                    if (!isWall) continue

                    // 门位置：仅1楼 (floor == 0)
                    // 门位置 (building_x+4, y0+1, building_z+0/7) — 取决于朝向
                    val isDoor = floor == 0 && when (doorFacing) {
                        Direction.SOUTH -> dx == 4 && dz == 0        // 西北楼：门在 dz=0，朝SOUTH
                        Direction.NORTH -> dx == 4 && dz == D - 1    // 西南楼：门在 dz=7，朝NORTH
                        Direction.WEST -> dx == W - 1 && dz == 4     // 东北楼：门在 dx=7，朝WEST
                        Direction.EAST -> dx == 0 && dz == 4         // 东南楼：门在 dx=0，朝EAST
                        else -> false
                    }

                    for (dy in 1..FLOOR_HEIGHT) {
                        // 门的位置：1楼，dy=1,2 留给 placeDoor1x2（2格门），dy=3 留给门楣
                        if (isDoor && dy in 1..3) continue
                        val pos = BlockPos.MutableBlockPos(bx + dx, floorY + dy, bz + dz)
                        level.setBlock(pos, wallBlock.defaultBlockState(), 3)
                    }
                }
            }

            // 1楼门：1×2 OAK_DOOR 朝广场中心
            if (floor == 0) {
                val (doorX, doorZ) = when (doorFacing) {
                    Direction.SOUTH -> Pair(bx + 4, bz + 0)
                    Direction.NORTH -> Pair(bx + 4, bz + D - 1)
                    Direction.WEST -> Pair(bx + W - 1, bz + 4)
                    Direction.EAST -> Pair(bx + 0, bz + 4)
                    else -> Pair(bx + 4, bz + 0)
                }
                StructureGenSupport.placeDoor1x2(
                    level, doorX, y0 + 1, doorZ,
                    doorFacing,
                    Blocks.OAK_DOOR as DoorBlock,
                    wallBlock.defaultBlockState()
                )
            }

            // 箱子：1层 (x+2, y0+1, z+2)，2层 (x+5, y0+4, z+5)
            val chestPos = if (floor == 0) {
                BlockPos(bx + 2, floorY + 1, bz + 2)
            } else {
                BlockPos(bx + 5, floorY + 1, bz + 5)
            }
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, chestPos, random, loot, MOD_ITEM_CHANCE, 3, 6)
            StructureGenSupport.maybeInjectTaczWeapon(level, chestPos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
        }

        // 屋顶（第2层地板上方：y0 + 2 * FLOOR_HEIGHT = y0+6）
        val roofY = y0 + 2 * FLOOR_HEIGHT
        for (dx in 0 until W) {
            for (dz in 0 until D) {
                level.setBlock(
                    BlockPos.MutableBlockPos(bx + dx, roofY, bz + dz),
                    Blocks.COBBLESTONE_SLAB.defaultBlockState(), 3
                )
            }
        }
    }
}
