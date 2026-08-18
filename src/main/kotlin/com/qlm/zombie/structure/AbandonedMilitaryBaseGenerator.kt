package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 废弃军事基地生成器（128×128，跨8×8区块）：
 * - 混凝土围墙 + 铁丝网 + 四角岗楼(12格高)
 * - 营房区 / 弹药库(AMMO_CRATE) / 军火库(SUPPLY_CRATE) / 医务所 / 训练场 / 停机坪 / 指挥塔
 * - 13个CD箱子 + 8个普通箱子，5%保底tacz武器
 * - 1格宽×2格高南门（方便其他模组防御物品留通道）
 */
object AbandonedMilitaryBaseGenerator : BuildingGenerator {

    private const val BASE_SIZE = 128        // 128×128 方块
    private const val BASE_SIZE_CHUNKS = 8    // 8×8 区块
    private const val WALL_HEIGHT = 6         // 围墙6格高
    private const val TOWER_HEIGHT = 12       // 角岗楼12格高
    private const val COMMAND_TOWER_FLOORS = 5 // 指挥塔5层

    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    /** 弹药库战利品（CD弹药为主） */
    private val ammoLoot by lazy {
        listOf(
            CDItems.RIFLE_AMMO.get(), CDItems.PISTOL_AMMO.get(),
            CDItems.SHOTGUN_SHELL.get(), CDItems.SNIPER_AMMO.get(),
            QLMItems.TACTICAL_AMMO.get(),
            Items.ARROW, Items.TNT, Items.GUNPOWDER,
        )
    }

    /** 军火库战利品（CD武器+配件为主） */
    private val weaponLoot by lazy {
        listOf(
            CDItems.AK47.get(), CDItems.M4A1.get(), CDItems.MP5.get(),
            CDItems.M1014.get(), CDItems.DESERT_EAGLE.get(), CDItems.GLOCK17.get(),
            CDItems.BARRETT_M82.get(), CDItems.AWM.get(),
            CDItems.RED_DOT_SIGHT.get(), CDItems.ACOG_SIGHT.get(),
            CDItems.SUPPRESSOR.get(), CDItems.EXTENDED_MAG.get(),
            CDItems.DRUM_MAG.get(), CDItems.BALLISTIC_HELMET.get(),
            CDItems.PLATE_CARRIER.get(), CDItems.TACTICAL_VEST.get(),
        )
    }

    /** 医务所战利品（CD医疗为主） */
    private val medicalLoot by lazy {
        listOf(
            CDItems.BANDAGE.get(), CDItems.FIRST_AID_KIT.get(),
            CDItems.ADRENALINE_SYRINGE.get(), CDItems.PAINKILLERS.get(),
            CDItems.TOURNIQUET.get(), CDItems.SALINE_BAG.get(),
            CDItems.SPLINT.get(), CDItems.SURGICAL_SCISSORS.get(),
            QLMItems.MEDICAL_SUPPLY.get(), QLMItems.ANTIDOTE.get(),
        )
    }

    /** 营房杂物战利品 */
    private val barrackLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.IRON_BLOCK, Items.GOLD_INGOT,
            Items.BREAD, Items.COOKED_BEEF, Items.APPLE,
            Items.LEATHER, Items.STRING, Items.IRON_SWORD,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE,
            QLMItems.SURVIVAL_KIT.get(), QLMItems.REINFORCED_PARTS.get(),
            QLMItems.EMERGENCY_RATION.get(), QLMItems.ZOMBIE_CORE.get(),
        )
    }

    override fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        // 仅在8×8网格锚点触发（1/64区块）
        if (chunkX % BASE_SIZE_CHUNKS != 0 || chunkZ % BASE_SIZE_CHUNKS != 0) return false

        val key = StructureGenSupport.chunkKey(chunkX, chunkZ)
        if (StructureGenSupport.generatedChunks.contains(key)) return false

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 2) return false

        val biome = level.getBiome(BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8))
        if (biome.`is`(net.minecraft.tags.BiomeTags.IS_OCEAN)) return false

        // 跨区块间距检测
        if (!StructureGenSupport.isFarEnoughArea(chunkX, chunkZ, BASE_SIZE_CHUNKS, QLMConfig.MILITARY_BASE_SPACING.get())) return false

        // 标记8×8全部区块为已评估（防止其他生成器在此区域生成）
        for (dx in 0 until BASE_SIZE_CHUNKS) {
            for (dz in 0 until BASE_SIZE_CHUNKS) {
                decidedChunks.add(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
            }
        }

        // 跨区块平面检测（周边区块需全部加载）
        if (!StructureGenSupport.isFlatTerrainArea(level, chunkX, chunkZ, BASE_SIZE_CHUNKS, QLMConfig.FLAT_TOLERANCE_LARGE.get())) {
            // 平面检测失败，取消标记，等待下次扫描
            for (dx in 0 until BASE_SIZE_CHUNKS) {
                for (dz in 0 until BASE_SIZE_CHUNKS) {
                    decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
                }
            }
            return false
        }

        // 确定性随机：用chunkX/chunkZ哈希生成种子，避免重启后随机重摇
        val hashSeed = (chunkX.toLong() * 73856093L) xor (chunkZ.toLong() * 19349663L)
        val detRandom = net.minecraft.util.RandomSource.create(hashSeed)
        if (detRandom.nextDouble() >= QLMConfig.MILITARY_BASE_CHANCE.get()) return false

        // 强制加载8×8全部区块
        val serverLevel = level as? ServerLevel ?: return false
        for (dx in 0 until BASE_SIZE_CHUNKS) {
            for (dz in 0 until BASE_SIZE_CHUNKS) {
                serverLevel.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true)
            }
        }

        val originX = chunkX * 16
        val originZ = chunkZ * 16
        val originY = surfaceY

        return try {
            generateMilitaryBase128(level, originX, originY, originZ, detRandom)
            // 标记8×8全部64个区块为已生成
            for (dx in 0 until BASE_SIZE_CHUNKS) {
                for (dz in 0 until BASE_SIZE_CHUNKS) {
                    val ck = StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz)
                    StructureGenSupport.generatedChunks.add(ck)
                }
            }
            StructureGenSupport.registerBuilding(
                key,
                BlockPos(originX + BASE_SIZE / 2, originY, originZ + BASE_SIZE / 2)
            )
            QLMZombieMod.LOGGER.info(
                "[军事基地] 在区块 ({}, {}) 生成128×128军事基地", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            for (dx in 0 until BASE_SIZE_CHUNKS) {
                for (dz in 0 until BASE_SIZE_CHUNKS) {
                    decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ + dz))
                }
            }
            QLMZombieMod.LOGGER.error("[军事基地] 生成失败: {}", e.message)
            false
        }
    }

    private fun generateMilitaryBase128(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        // 1. 基地地面（砂土+碎石）
        for (dx in 0 until BASE_SIZE) {
            for (dz in 0 until BASE_SIZE) {
                val pos = BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz)
                val block = if (random.nextDouble() < 0.2) Blocks.GRAVEL else Blocks.COARSE_DIRT
                level.setBlock(pos, block.defaultBlockState(), 3)
            }
        }

        // 2. 围墙（混凝土+顶部铁丝网）
        buildPerimeterWall(level, x0, y0, z0, random)

        // 3. 四角岗楼（12格高）
        buildCornerTowers(level, x0, y0, z0)

        // 4. 南门（1格宽×2格高铁门）
        val gateX = x0 + BASE_SIZE / 2
        StructureGenSupport.placeDoor1x2(
            level, gateX, y0 + 1, z0,
            Direction.NORTH,
            Blocks.IRON_DOOR as DoorBlock,
            Blocks.STONE_BRICKS.defaultBlockState()
        )

        // 5. 建筑群
        // 弹药库（西北角附近，放置6个AMMO_CRATE）
        buildAmmoDepot(level, x0 + 16, y0, z0 + 16, random)

        // 军火库（东北角附近，放置4个SUPPLY_CRATE）
        buildArmory(level, x0 + BASE_SIZE - 28, y0, z0 + 16, random)

        // 医务所（中心偏北，放置3个SUPPLY_CRATE作为医疗箱）
        buildMedicalStation(level, x0 + BASE_SIZE / 2 - 6, y0, z0 + 20, random)

        // 营房区A（西南角附近）
        buildBarracks(level, x0 + 16, y0, z0 + BASE_SIZE - 28, random, "A")

        // 营房区B（东南角附近）
        buildBarracks(level, x0 + BASE_SIZE - 28, y0, z0 + BASE_SIZE - 28, random, "B")

        // 训练场（中心，沙袋+TNT）
        buildTrainingGround(level, x0 + BASE_SIZE / 2 - 10, y0, z0 + BASE_SIZE / 2 - 10, random)

        // 停机坪（中心偏南）
        buildHelipad(level, x0 + BASE_SIZE / 2 - 10, y0, z0 + BASE_SIZE - 32, random)

        // 指挥塔（中心偏北，5层高）
        buildCommandTower(level, x0 + BASE_SIZE / 2 - 4, y0, z0 + 40, random)
    }

    /** 围墙：128×128周长，6格高混凝土+顶部铁丝网 */
    private fun buildPerimeterWall(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        for (dx in 0 until BASE_SIZE) {
            for (dz in 0 until BASE_SIZE) {
                val isWall = dx == 0 || dx == BASE_SIZE - 1 || dz == 0 || dz == BASE_SIZE - 1
                if (!isWall) continue
                // 南门位置跳过（1格宽门洞）
                val isGate = dz == 0 && dx == BASE_SIZE / 2
                for (dy in 1..WALL_HEIGHT) {
                    if (isGate && dy in 1..2) continue // 门洞（1×2）
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
    }

    /** 四角岗楼：4个，每个4×4，12格高 */
    private fun buildCornerTowers(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int
    ) {
        val corners = listOf(
            Triple(2, 2, "NW"), Triple(BASE_SIZE - 6, 2, "NE"),
            Triple(2, BASE_SIZE - 6, "SW"), Triple(BASE_SIZE - 6, BASE_SIZE - 6, "SE")
        )
        for ((cx, cz, _) in corners) {
            for (dy in 1..TOWER_HEIGHT) {
                for (dx in 0..3) {
                    for (dz in 0..3) {
                        val isEdge = dx == 0 || dx == 3 || dz == 0 || dz == 3
                        if (isEdge || dy == TOWER_HEIGHT) {
                            val pos = BlockPos.MutableBlockPos(x0 + cx + dx, y0 + dy, z0 + cz + dz)
                            level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
                        }
                    }
                }
            }
            // 岗楼顶围栏
            for (dx in 0..3) {
                for (dz in 0..3) {
                    val isEdge = dx == 0 || dx == 3 || dz == 0 || dz == 3
                    if (isEdge) {
                        level.setBlock(
                            BlockPos.MutableBlockPos(x0 + cx + dx, y0 + TOWER_HEIGHT + 1, z0 + cz + dz),
                            Blocks.IRON_BARS.defaultBlockState(), 3
                        )
                    }
                }
            }
        }
    }

    /** 弹药库：12×12，放置6个AMMO_CRATE（CD弹药箱） */
    private fun buildAmmoDepot(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        val size = 12
        // 地板
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.SMOOTH_STONE.defaultBlockState(), 3)
            }
        }
        // 墙壁
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                val isWall = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1
                if (!isWall) continue
                for (dy in 1..4) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz),
                        Blocks.STONE_BRICKS.defaultBlockState(), 3)
                }
            }
        }
        // 门（1格宽×2格高）
        StructureGenSupport.placeDoor1x2(
            level, x0 + size / 2, y0 + 1, z0,
            Direction.NORTH, Blocks.IRON_DOOR as DoorBlock,
            Blocks.STONE_BRICKS.defaultBlockState()
        )
        // 6个AMMO_CRATE
        val cratePositions = listOf(
            Triple(2, 1, 2), Triple(5, 1, 2), Triple(8, 1, 2),
            Triple(2, 1, 8), Triple(5, 1, 8), Triple(8, 1, 8),
        )
        for ((cx, cy, cz) in cratePositions) {
            val pos = BlockPos(x0 + cx, y0 + cy, z0 + cz)
            level.setBlock(pos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
            StructureGenSupport.fillCDCrate(level, pos, random, ammoLoot, 0.3, 6, 12)
        }
    }

    /** 军火库：12×12，放置4个SUPPLY_CRATE（CD武器箱） */
    private fun buildArmory(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        val size = 12
        // 地板
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.SMOOTH_STONE.defaultBlockState(), 3)
            }
        }
        // 墙壁
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                val isWall = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1
                if (!isWall) continue
                for (dy in 1..4) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz),
                        Blocks.STONE_BRICKS.defaultBlockState(), 3)
                }
            }
        }
        // 门
        StructureGenSupport.placeDoor1x2(
            level, x0 + size / 2, y0 + 1, z0,
            Direction.NORTH, Blocks.IRON_DOOR as DoorBlock,
            Blocks.STONE_BRICKS.defaultBlockState()
        )
        // 4个SUPPLY_CRATE
        val cratePositions = listOf(
            Triple(3, 1, 3), Triple(8, 1, 3),
            Triple(3, 1, 8), Triple(8, 1, 8),
        )
        for ((cx, cy, cz) in cratePositions) {
            val pos = BlockPos(x0 + cx, y0 + cy, z0 + cz)
            level.setBlock(pos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
            StructureGenSupport.fillCDCrate(level, pos, random, weaponLoot, 0.2, 2, 5)
        }
    }

    /** 医务所：12×12，放置3个SUPPLY_CRATE作为医疗箱 */
    private fun buildMedicalStation(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        val size = 12
        // 地板（白色羊毛，医院风格）
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.WHITE_WOOL.defaultBlockState(), 3)
            }
        }
        // 墙壁
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                val isWall = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1
                if (!isWall) continue
                for (dy in 1..4) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz),
                        Blocks.WHITE_CONCRETE.defaultBlockState(), 3)
                }
            }
        }
        // 门
        StructureGenSupport.placeDoor1x2(
            level, x0 + size / 2, y0 + 1, z0,
            Direction.NORTH, Blocks.IRON_DOOR as DoorBlock,
            Blocks.WHITE_CONCRETE.defaultBlockState()
        )
        // 3个SUPPLY_CRATE（医疗箱）
        val cratePositions = listOf(Triple(3, 1, 3), Triple(6, 1, 6), Triple(9, 1, 9))
        for ((cx, cy, cz) in cratePositions) {
            val pos = BlockPos(x0 + cx, y0 + cy, z0 + cz)
            level.setBlock(pos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
            StructureGenSupport.fillCDCrate(level, pos, random, medicalLoot, 0.2, 3, 6)
        }
    }

    /** 营房：16×10，内含2个普通箱子 */
    private fun buildBarracks(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource,
        label: String
    ) {
        val width = 16
        val depth = 10
        // 地板
        for (dx in 0 until width) {
            for (dz in 0 until depth) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.SMOOTH_STONE.defaultBlockState(), 3)
            }
        }
        // 墙壁
        for (dx in 0 until width) {
            for (dz in 0 until depth) {
                val isWall = dx == 0 || dx == width - 1 || dz == 0 || dz == depth - 1
                if (!isWall) continue
                for (dy in 1..4) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + dz),
                        Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3)
                }
            }
        }
        // 屋顶
        for (dx in 0 until width) {
            for (dz in 0 until depth) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + 5, z0 + dz),
                    Blocks.GRAY_CONCRETE.defaultBlockState(), 3)
            }
        }
        // 门（1格宽×2格高）
        StructureGenSupport.placeDoor1x2(
            level, x0 + width / 2, y0 + 1, z0,
            Direction.NORTH, Blocks.IRON_DOOR as DoorBlock,
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
        )
        // 2个普通箱子
        val chestPositions = listOf(Triple(3, 1, 3), Triple(width - 4, 1, depth - 4))
        for ((cx, cy, cz) in chestPositions) {
            val pos = BlockPos(x0 + cx, y0 + cy, z0 + cz)
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, pos, random, barrackLoot, 0.4, 3, 6)
            StructureGenSupport.maybeInjectTaczWeapon(level, pos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
        }
        // 沙袋掩体（营房入口两侧）
        for (sx in listOf(width / 2 - 3, width / 2 + 2)) {
            for (dz in 1..2) {
                for (dy in 1..2) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + sx, y0 + dy, z0 + dz),
                        Blocks.SANDSTONE.defaultBlockState(), 3)
                }
            }
        }
    }

    /** 训练场：20×20，沙袋+TNT */
    private fun buildTrainingGround(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        val size = 20
        // 地板（沙子）
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.SAND.defaultBlockState(), 3)
            }
        }
        // 沙袋掩体圈
        for (dx in 4..6) {
            for (dy in 1..2) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + 4), Blocks.SANDSTONE.defaultBlockState(), 3)
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0 + dy, z0 + size - 5), Blocks.SANDSTONE.defaultBlockState(), 3)
            }
        }
        // TNT板条箱（角落1-2个）
        if (random.nextDouble() < 0.8) {
            val tntX = x0 + size - 3
            val tntZ = z0 + size - 3
            level.setBlock(BlockPos.MutableBlockPos(tntX, y0 + 1, tntZ), Blocks.TNT.defaultBlockState(), 3)
            level.setBlock(BlockPos.MutableBlockPos(tntX, y0 + 2, tntZ), Blocks.TNT.defaultBlockState(), 3)
        }
        // 2个普通箱子
        for ((cx, cz) in listOf(Pair(2, 2), Pair(size - 3, 2))) {
            val pos = BlockPos(x0 + cx, y0 + 1, z0 + cz)
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, pos, random, barrackLoot, 0.5, 2, 4)
            StructureGenSupport.maybeInjectTaczWeapon(level, pos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
        }
    }

    /** 停机坪：20×20，混凝土 */
    private fun buildHelipad(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        val size = 20
        // 地板（混凝土+中心H标志用黄色混凝土）
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                val isCenter = dx in 8..11 && dz in 8..11
                val block = if (isCenter) Blocks.YELLOW_CONCRETE else Blocks.LIGHT_GRAY_CONCRETE
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz), block.defaultBlockState(), 3)
            }
        }
        // 2个普通箱子（停机坪角落）
        for ((cx, cz) in listOf(Pair(1, 1), Pair(size - 2, size - 2))) {
            val pos = BlockPos(x0 + cx, y0 + 1, z0 + cz)
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3)
            StructureGenSupport.fillChest(level, pos, random, barrackLoot, 0.5, 2, 4)
            StructureGenSupport.maybeInjectTaczWeapon(level, pos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
        }
    }

    /** 指挥塔：8×8，5层高 */
    private fun buildCommandTower(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        val size = 8
        val floors = COMMAND_TOWER_FLOORS
        val floorHeight = 4

        for (floor in 0 until floors) {
            val floorY = y0 + floor * floorHeight
            // 地板
            for (dx in 0 until size) {
                for (dz in 0 until size) {
                    level.setBlock(BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz),
                        Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                }
            }
            // 墙壁
            for (dx in 0 until size) {
                for (dz in 0 until size) {
                    val isWall = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1
                    if (!isWall) continue
                    for (dy in 1..floorHeight) {
                        val isWindow = dy == 2 && random.nextDouble() < 0.5
                        val block = if (isWindow) Blocks.GLASS else Blocks.STONE_BRICKS
                        level.setBlock(BlockPos.MutableBlockPos(x0 + dx, floorY + dy, z0 + dz),
                            block.defaultBlockState(), 3)
                    }
                }
            }
            // 门（1楼）
            if (floor == 0) {
                StructureGenSupport.placeDoor1x2(
                    level, x0 + size / 2, floorY + 1, z0,
                    Direction.NORTH, Blocks.IRON_DOOR as DoorBlock,
                    Blocks.STONE_BRICKS.defaultBlockState()
                )
            }
            // 楼梯（简单螺旋：每层放4级台阶）
            val stairBase = floorY + 1
            val stairs = listOf(
                Triple(size - 2, stairBase, size - 2),
                Triple(size - 3, stairBase + 1, size - 2),
                Triple(size - 3, stairBase + 2, size - 3),
                Triple(size - 2, stairBase + 3, size - 3),
            )
            for ((sx, sy, sz) in stairs) {
                level.setBlock(
                    BlockPos.MutableBlockPos(x0 + sx, sy, z0 + sz),
                    Blocks.STONE_STAIRS.defaultBlockState().setValue(
                        net.minecraft.world.level.block.StairBlock.FACING,
                        net.minecraft.core.Direction.EAST
                    ), 3
                )
            }
            // 顶层放1个SUPPLY_CRATE（指挥官宝箱）
            if (floor == floors - 1) {
                val pos = BlockPos(x0 + size / 2, floorY + 1, z0 + size / 2)
                level.setBlock(pos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
                StructureGenSupport.fillCDCrate(level, pos, random, weaponLoot + medicalLoot, 0.3, 4, 8)
            }
        }
        // 屋顶
        val roofY = y0 + floors * floorHeight
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                level.setBlock(BlockPos.MutableBlockPos(x0 + dx, roofY, z0 + dz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3)
            }
        }
        // 屋顶围栏
        for (dx in 0 until size) {
            level.setBlock(BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0), Blocks.IRON_BARS.defaultBlockState(), 3)
            level.setBlock(BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0 + size - 1), Blocks.IRON_BARS.defaultBlockState(), 3)
        }
        for (dz in 0 until size) {
            level.setBlock(BlockPos.MutableBlockPos(x0, roofY + 1, z0 + dz), Blocks.IRON_BARS.defaultBlockState(), 3)
            level.setBlock(BlockPos.MutableBlockPos(x0 + size - 1, roofY + 1, z0 + dz), Blocks.IRON_BARS.defaultBlockState(), 3)
        }
    }
}
