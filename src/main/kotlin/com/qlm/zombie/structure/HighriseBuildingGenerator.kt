package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.tags.BiomeTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.api.distmarker.Dist
import java.util.concurrent.ConcurrentHashMap

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.DEDICATED_SERVER])
object HighriseBuildingGenerator {

    private const val SPAWN_CHANCE = 0.02
    private const val MIN_SPACING = 8
    private const val BUILDING_WIDTH = 13
    private const val BUILDING_DEPTH = 9
    private const val FLOORS = 9
    private const val FLOOR_HEIGHT = 4
    private const val STAIRCASE_SIZE = 3
    private const val MOD_ITEM_CHANCE = 0.15

    private val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

    private val modLoot = listOf(
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

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val levelAccessor = event.level
        if (levelAccessor.isClientSide) return

        val level = levelAccessor as? net.minecraft.world.level.Level ?: return
        val chunk = event.chunk as? net.minecraft.world.level.chunk.LevelChunk ?: return

        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        val chunkKey = chunkKey(chunkX, chunkZ)
        if (generatedChunks.contains(chunkKey)) return

        if (level.random.nextDouble() >= SPAWN_CHANCE) return

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY < 40) return

        val biome = level.getBiome(
            BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)
        )
        if (biome.`is`(BiomeTags.IS_OCEAN)) return

        if (!isFarEnoughFromOtherStructures(chunkX, chunkZ)) return

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - BUILDING_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - BUILDING_DEPTH / 2
        )

        try {
            generateHighrise(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.debug(
                "[高层建筑] 在区块 ({}, {}) 生成9层高楼", chunkX, chunkZ
            )
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[高层建筑] 生成失败: {}", e.message)
        }
    }

    private fun isFarEnoughFromOtherStructures(chunkX: Int, chunkZ: Int): Boolean {
        for (dx in -MIN_SPACING..MIN_SPACING) {
            for (dz in -MIN_SPACING..MIN_SPACING) {
                if (dx == 0 && dz == 0) continue
                if (generatedChunks.contains(chunkKey(chunkX + dx, chunkZ + dz))) {
                    return false
                }
            }
        }
        return true
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

        val staircaseMinX = BUILDING_WIDTH / 2 - STAIRCASE_SIZE / 2
        val staircaseMaxX = staircaseMinX + STAIRCASE_SIZE - 1
        val staircaseMinZ = BUILDING_DEPTH / 2 - STAIRCASE_SIZE / 2
        val staircaseMaxZ = staircaseMinZ + STAIRCASE_SIZE - 1

        val ladderX = staircaseMinX
        val ladderZ = staircaseMinZ
        val ladderWallX = ladderX - 1

        for (floor in 0 until FLOORS) {
            val floorY = groundY + floor * FLOOR_HEIGHT

            for (dx in 0 until BUILDING_WIDTH) {
                for (dz in 0 until BUILDING_DEPTH) {
                    val floorPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    val isStaircaseArea = dx in staircaseMinX..staircaseMaxX &&
                        dz in staircaseMinZ..staircaseMaxZ
                    val isLadderHole = dx == ladderX && dz == ladderZ

                    if (isStaircaseArea && floor > 0 && isLadderHole) {
                        continue
                    }

                    level.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3)
                }
            }

            for (dx in 0 until BUILDING_WIDTH) {
                for (dz in 0 until BUILDING_DEPTH) {
                    val isPerimeter = dx == 0 || dx == BUILDING_WIDTH - 1 ||
                        dz == 0 || dz == BUILDING_DEPTH - 1
                    if (!isPerimeter) continue

                    for (dy in 1..FLOOR_HEIGHT) {
                        val wallPos = BlockPos.MutableBlockPos(x0 + dx, floorY + dy, z0 + dz)
                        val isWindow = dy == 2 && random.nextDouble() < 0.6
                        val isDoor = floor == 0 && dy == 1 &&
                            dx == BUILDING_WIDTH / 2 && dz == BUILDING_DEPTH - 1

                        when {
                            isDoor ->
                                level.setBlock(wallPos, Blocks.IRON_DOOR.defaultBlockState(), 3)
                            isWindow ->
                                level.setBlock(wallPos, Blocks.GLASS.defaultBlockState(), 3)
                            else ->
                                level.setBlock(wallPos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
                        }
                    }
                }
            }

            if (floor == 0) {
                buildFirstFloorRooms(level, x0, floorY, z0, staircaseMinX, staircaseMaxX,
                    staircaseMinZ, staircaseMaxZ, random)
            } else {
                buildUpperFloorRooms(level, x0, floorY, z0, staircaseMinX, staircaseMaxX,
                    staircaseMinZ, staircaseMaxZ, random, floor)
            }

            for (dy in 1 until FLOOR_HEIGHT) {
                val ladderPos = BlockPos.MutableBlockPos(
                    x0 + ladderX,
                    floorY + dy,
                    z0 + ladderZ
                )
                level.setBlock(ladderPos, Blocks.LADDER.defaultBlockState(), 3)
            }

            val ladderWallPos = BlockPos.MutableBlockPos(
                x0 + ladderWallX,
                floorY + 1,
                z0 + ladderZ
            )
            if (floor > 0 && level.getBlockState(ladderWallPos).isAir) {
                level.setBlock(ladderWallPos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
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
            level.setBlock(parapetPos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
            val parapetPos2 = BlockPos.MutableBlockPos(x0 + dx, roofY + 1, z0 + BUILDING_DEPTH - 1)
            level.setBlock(parapetPos2, Blocks.STONE_BRICKS.defaultBlockState(), 3)
        }
        for (dz in 0 until BUILDING_DEPTH) {
            val parapetPos = BlockPos.MutableBlockPos(x0, roofY + 1, z0 + dz)
            level.setBlock(parapetPos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
            val parapetPos2 = BlockPos.MutableBlockPos(x0 + BUILDING_WIDTH - 1, roofY + 1, z0 + dz)
            level.setBlock(parapetPos2, Blocks.STONE_BRICKS.defaultBlockState(), 3)
        }

        QLMZombieMod.LOGGER.debug(
            "[高层建筑] 完成9层高楼生成, 位置: {}, {}, {}", x0, groundY, z0
        )
    }

    private fun buildFirstFloorRooms(
        level: net.minecraft.world.level.Level,
        x0: Int,
        floorY: Int,
        z0: Int,
        stairMinX: Int, stairMaxX: Int,
        stairMinZ: Int, stairMaxZ: Int,
        random: net.minecraft.util.RandomSource
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
                    level.setBlock(wallPos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                }
            } else {
                val wz = start.second
                for (wx in start.first..end.first) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + wx, floorY + 1, z0 + wz)
                    level.setBlock(wallPos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, Blocks.OAK_PLANKS.defaultBlockState(), 3)
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

        for ((rx, ry, rz) in roomCenters) {
            val chestPos = BlockPos.MutableBlockPos(x0 + rx, ry, z0 + rz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            fillChestWithLoot(level, chestPos, random)
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
        floor: Int
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
                    level.setBlock(wallPos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                }
            } else {
                val wz = start.second
                for (wx in start.first..end.first) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + wx, floorY + 1, z0 + wz)
                    level.setBlock(wallPos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + wx, floorY + 2, z0 + wz)
                    level.setBlock(wallPos2, Blocks.OAK_PLANKS.defaultBlockState(), 3)
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

        for ((rx, ry, rz) in roomCenters) {
            val chestPos = BlockPos.MutableBlockPos(x0 + rx, ry, z0 + rz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            fillChestWithLoot(level, chestPos, random)
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
        val chest = level.getBlockEntity(pos) as? net.minecraft.world.level.block.entity.ChestBlockEntity
            ?: return

        val useModLoot = random.nextDouble() < MOD_ITEM_CHANCE
        val lootPool = if (useModLoot) modLoot else vanillaLoot

        val itemsToAdd = 2 + random.nextInt(4)
        for (i in 0 until itemsToAdd) {
            val item = lootPool.random()
            val stack = ItemStack(item)
            stack.count = 1 + random.nextInt(6)
            chest.setItem(i % chest.containerSize, stack)
        }
        chest.setChanged()
    }

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}