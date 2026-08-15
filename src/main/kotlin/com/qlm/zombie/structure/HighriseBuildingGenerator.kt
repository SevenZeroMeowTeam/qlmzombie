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
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.concurrent.ConcurrentHashMap

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object HighriseBuildingGenerator {

    private const val SPAWN_CHANCE = 0.15
    private const val MIN_SPACING = 4
    private const val BUILDING_WIDTH = 13
    private const val BUILDING_DEPTH = 9
    private const val FLOORS = 9
    private const val FLOOR_HEIGHT = 4
    private const val STAIRCASE_SIZE = 3
    private const val MOD_ITEM_CHANCE = 0.15
    // 玩家登录时扫描周围已加载区块的半径（半径 3 = 7x7 = 49 个区块）
    private const val LOGIN_SCAN_RADIUS = 3

    private val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

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

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val levelAccessor = event.level
        if (levelAccessor.isClientSide) return

        val level = levelAccessor as? net.minecraft.world.level.Level ?: return
        val chunk = event.chunk as? net.minecraft.world.level.chunk.LevelChunk ?: return
        tryGenerate(level, chunk)
    }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity ?: return
        val level = player.level()
        if (level.isClientSide) return
        val serverLevel = level as? net.minecraft.server.level.ServerLevel ?: return

        QLMZombieMod.LOGGER.info(
            "[高层建筑] 玩家 {} 登录, 延迟2秒后扫描周围区块补生成",
            player.name.string
        )
        // 延迟 40 tick (2秒) 扫描，确保玩家周围区块已加载完成
        val server = serverLevel.server
        server.tell(net.minecraft.server.TickTask(server.tickCount + 40, Runnable {
            try {
                scanAndGenerate(serverLevel, player)
            } catch (e: Exception) {
                QLMZombieMod.LOGGER.error("[高层建筑] 延迟扫描异常: {}", e.message)
            }
        }))
    }

    private fun scanAndGenerate(
        serverLevel: net.minecraft.server.level.ServerLevel,
        player: net.minecraft.world.entity.player.Player
    ) {
        val centerChunkX = player.blockPosition().x shr 4
        val centerChunkZ = player.blockPosition().z shr 4
        var scanned = 0
        var generated = 0
        for (dx in -LOGIN_SCAN_RADIUS..LOGIN_SCAN_RADIUS) {
            for (dz in -LOGIN_SCAN_RADIUS..LOGIN_SCAN_RADIUS) {
                val chunk = serverLevel.chunkSource.getChunkNow(centerChunkX + dx, centerChunkZ + dz)
                if (chunk != null) {
                    scanned++
                    if (tryGenerate(serverLevel, chunk)) generated++
                }
            }
        }
        QLMZombieMod.LOGGER.info(
            "[高层建筑] 玩家 {} 延迟扫描完成: 扫描{}区块, 新生成{}高楼",
            player.name.string, scanned, generated
        )
    }

    private fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        val chunkKey = chunkKey(chunkX, chunkZ)
        if (generatedChunks.contains(chunkKey)) return false

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY < 40) return false

        val biome = level.getBiome(
            BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)
        )
        if (biome.`is`(BiomeTags.IS_OCEAN)) return false

        if (!isFarEnoughFromOtherStructures(chunkX, chunkZ)) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - BUILDING_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - BUILDING_DEPTH / 2
        )

        // 跨会话防重复：若建筑标志（双层 STONE 地板）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            generatedChunks.add(chunkKey)
            return false
        }

        if (level.random.nextDouble() >= SPAWN_CHANCE) return false

        return try {
            generateHighrise(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.info(
                "[高层建筑] 在区块 ({}, {}) 生成9层高楼", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
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