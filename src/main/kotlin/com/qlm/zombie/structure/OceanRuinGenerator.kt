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
object OceanRuinGenerator {

    private const val SPAWN_CHANCE = 0.08
    private const val MIN_SPACING = 7
    private const val RUIN_SIZE = 10
    private const val SEA_LEVEL = 62

    private val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

    private val oceanLoot = listOf(
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

        val centerPos = BlockPos.MutableBlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8)
        val biome = level.getBiome(centerPos)
        if (!biome.`is`(BiomeTags.IS_OCEAN)) return

        if (level.random.nextDouble() >= SPAWN_CHANCE) return

        if (!isFarEnoughFromOtherStructures(chunkX, chunkZ)) return

        val floorY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (floorY >= SEA_LEVEL) return

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - RUIN_SIZE / 2,
            floorY,
            chunkZ * 16 + 8 - RUIN_SIZE / 2
        )

        try {
            generateRuin(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.debug(
                "[海底遗迹] 在区块 ({}, {}) 生成海底遗迹", chunkX, chunkZ
            )
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[海底遗迹] 生成失败: {}", e.message)
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