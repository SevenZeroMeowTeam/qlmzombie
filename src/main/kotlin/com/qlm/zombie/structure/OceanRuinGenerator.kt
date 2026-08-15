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
object OceanRuinGenerator {

    private const val SPAWN_CHANCE = 0.40
    private const val MIN_SPACING = 3
    private const val RUIN_SIZE = 10
    private const val SEA_LEVEL = 62
    // 玩家登录时扫描周围已加载区块的半径（半径 3 = 7x7 = 49 个区块）
    private const val LOGIN_SCAN_RADIUS = 3

    private val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

    // 延迟初始化：RegistryObject.get() 必须在注册表完成注册后调用，
    // 类静态初始化时（CONSTRUCT 阶段）调用会抛出 NPE。
    private val oceanLoot by lazy {
        listOf(
            QLMItems.ZOMBIE_CORE.get(),
            QLMItems.INFECTED_ESSENCE.get(),
            QLMItems.MEDICAL_SUPPLY.get(),
            QLMItems.REINFORCED_PARTS.get(),
            QLMItems.BIOHAZARD_SAMPLE.get(),
            QLMItems.TACTICAL_AMMO.get(),
            QLMItems.SURVIVAL_KIT.get(),
            CDItems.RIFLE_AMMO.get(),
            CDItems.PISTOL_AMMO.get(),
            CDItems.SNIPER_AMMO.get(),
            CDItems.BANDAGE.get(),
            CDItems.FIRST_AID_KIT.get(),
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.COPPER_INGOT,
            Items.LAPIS_LAZULI,
            Items.EMERALD,
            Items.PRISMARINE_CRYSTALS,
            Items.PRISMARINE_SHARD,
        )
    }

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
            "[海底遗迹] 玩家 {} 登录, 延迟2秒后扫描周围区块补生成",
            player.name.string
        )
        // 延迟 40 tick (2秒) 扫描，确保玩家周围区块已加载完成
        val server = serverLevel.server
        server.tell(net.minecraft.server.TickTask(server.tickCount + 40, Runnable {
            try {
                scanAndGenerate(serverLevel, player)
            } catch (e: Exception) {
                QLMZombieMod.LOGGER.error("[海底遗迹] 延迟扫描异常: {}", e.message)
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
            "[海底遗迹] 玩家 {} 延迟扫描完成: 扫描{}区块, 新生成{}遗迹",
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

        val centerPos = BlockPos.MutableBlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8)
        val biome = level.getBiome(centerPos)
        if (!biome.`is`(BiomeTags.IS_OCEAN)) return false

        val floorY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (floorY >= SEA_LEVEL) return false

        if (!isFarEnoughFromOtherStructures(chunkX, chunkZ)) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - RUIN_SIZE / 2,
            floorY,
            chunkZ * 16 + 8 - RUIN_SIZE / 2
        )

        // 跨会话防重复：若建筑标志（箱子）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            generatedChunks.add(chunkKey)
            return false
        }

        if (level.random.nextDouble() >= SPAWN_CHANCE) return false

        return try {
            generateRuin(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.info(
                "[海底遗迹] 在区块 ({}, {}) 生成海底遗迹", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[海底遗迹] 生成失败: {}", e.message)
            false
        }
    }

    /**
     * 检测目标位置是否已存在本生成器产出的建筑（箱子标志）。
     * 用于跨会话防重复：generatedChunks 是内存 Set，重启后清空，
     * 若不检查会概率性地在旧建筑上重叠生成第二座。
     */
    private fun hasExistingStructure(
        level: net.minecraft.world.level.Level,
        origin: BlockPos
    ): Boolean {
        // 遗迹第一个箱子位置：(x0+2, floorY+1, z0+2)
        val chestPos = BlockPos(origin.x + 2, origin.y + 1, origin.z + 2)
        return level.getBlockState(chestPos).block ==
            net.minecraft.world.level.block.Blocks.CHEST
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

    private fun generateRuin(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk,
        origin: BlockPos.MutableBlockPos
    ) {
        val x0 = origin.x
        val z0 = origin.z
        val floorY = origin.y
        val random = level.random

        val pillars = listOf(
            Triple(0, 0, 3),
            Triple(RUIN_SIZE - 1, 0, 4),
            Triple(0, RUIN_SIZE - 1, 2),
            Triple(RUIN_SIZE - 1, RUIN_SIZE - 1, 3),
            Triple(RUIN_SIZE / 2, RUIN_SIZE / 2, 5),
        )

        for ((px, pz, height) in pillars) {
            for (dy in 0 until height) {
                val pillarPos = BlockPos.MutableBlockPos(x0 + px, floorY + dy, z0 + pz)
                level.setBlock(pillarPos, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 3)
            }
        }

        for (dx in 1 until RUIN_SIZE - 1) {
            for (dz in listOf(0, RUIN_SIZE - 1)) {
                if (random.nextDouble() < 0.4) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    level.setBlock(wallPos, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + dx, floorY + 1, z0 + dz)
                    level.setBlock(wallPos2, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 3)
                }
            }
        }

        for (dz in 1 until RUIN_SIZE - 1) {
            for (dx in listOf(0, RUIN_SIZE - 1)) {
                if (random.nextDouble() < 0.4) {
                    val wallPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    level.setBlock(wallPos, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 3)
                    val wallPos2 = BlockPos.MutableBlockPos(x0 + dx, floorY + 1, z0 + dz)
                    level.setBlock(wallPos2, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 3)
                }
            }
        }

        val seaweedPositions = listOf(
            Triple(2, 2, 3),
            Triple(RUIN_SIZE - 3, 3, 2),
            Triple(3, RUIN_SIZE - 3, 4),
            Triple(RUIN_SIZE - 4, RUIN_SIZE - 4, 2),
            Triple(RUIN_SIZE / 2, 2, 2),
            Triple(2, RUIN_SIZE / 2, 3),
        )

        for ((sx, sz, height) in seaweedPositions) {
            for (dy in 0 until height) {
                val seaweedPos = BlockPos.MutableBlockPos(x0 + sx, floorY + dy, z0 + sz)
                level.setBlock(seaweedPos, Blocks.KELP.defaultBlockState(), 3)
            }
        }

        val coralPositions = listOf(
            Pair(4, 4),
            Pair(RUIN_SIZE - 5, 5),
            Pair(5, RUIN_SIZE - 5),
            Pair(RUIN_SIZE - 4, RUIN_SIZE - 4),
        )

        for ((cx, cz) in coralPositions) {
            val coralTypes = listOf(
                Blocks.TUBE_CORAL,
                Blocks.BRAIN_CORAL,
                Blocks.BUBBLE_CORAL,
                Blocks.FIRE_CORAL,
                Blocks.HORN_CORAL,
            )
            val coralPos = BlockPos.MutableBlockPos(x0 + cx, floorY, z0 + cz)
            level.setBlock(coralPos, coralTypes.random().defaultBlockState(), 3)
        }

        val chest1Pos = BlockPos.MutableBlockPos(x0 + 2, floorY + 1, z0 + 2)
        level.setBlock(chest1Pos, Blocks.CHEST.defaultBlockState(), 3)
        fillChestWithLoot(level, chest1Pos, random)

        val chest2Pos = BlockPos.MutableBlockPos(x0 + RUIN_SIZE - 3, floorY + 1, z0 + RUIN_SIZE - 3)
        level.setBlock(chest2Pos, Blocks.CHEST.defaultBlockState(), 3)
        fillChestWithLoot(level, chest2Pos, random)

        for (dx in 0 until RUIN_SIZE) {
            for (dz in 0 until RUIN_SIZE) {
                if (random.nextDouble() < 0.08) {
                    val floorPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    level.setBlock(floorPos, Blocks.GRAVEL.defaultBlockState(), 3)
                }
                if (random.nextDouble() < 0.05) {
                    val floorPos = BlockPos.MutableBlockPos(x0 + dx, floorY, z0 + dz)
                    level.setBlock(floorPos, Blocks.SAND.defaultBlockState(), 3)
                }
            }
        }

        QLMZombieMod.LOGGER.debug(
            "[海底遗迹] 完成海底遗迹生成, 位置: {}, {}, {}", x0, floorY, z0
        )
    }

    private fun fillChestWithLoot(
        level: net.minecraft.world.level.Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource
    ) {
        val chest = level.getBlockEntity(pos) as? net.minecraft.world.level.block.entity.ChestBlockEntity
            ?: return

        val itemsToAdd = 4 + random.nextInt(4)
        for (i in 0 until itemsToAdd) {
            val item = oceanLoot.random()
            val stack = ItemStack(item)
            stack.count = 1 + random.nextInt(4)
            chest.setItem(i % chest.containerSize, stack)
        }
        chest.setChanged()
    }

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}