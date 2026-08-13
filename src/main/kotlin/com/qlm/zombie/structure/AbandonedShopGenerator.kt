package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
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
object AbandonedShopGenerator {

    private const val SPAWN_CHANCE = 0.04
    private const val MIN_SPACING = 6
    private const val SHOP_WIDTH = 9
    private const val SHOP_DEPTH = 7
    private const val SHOP_HEIGHT = 4

    private val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

    private val medicalLoot = listOf(
        CDItems.BANDAGE.get(),
        CDItems.FIRST_AID_KIT.get(),
        CDItems.ADRENALINE_SYRINGE.get(),
        CDItems.PAINKILLERS.get(),
        CDItems.TOURNIQUET.get(),
        CDItems.SALINE_BAG.get(),
        CDItems.SPLINT.get(),
        CDItems.SURGICAL_SCISSORS.get(),
        QLMItems.MEDICAL_SUPPLY.get(),
        QLMItems.ANTIDOTE.get(),
    )

    private val ammoLoot = listOf(
        CDItems.RIFLE_AMMO.get(),
        CDItems.PISTOL_AMMO.get(),
        CDItems.SHOTGUN_SHELL.get(),
        CDItems.SNIPER_AMMO.get(),
        QLMItems.TACTICAL_AMMO.get(),
    )

    private val foodLoot = listOf(
        QLMItems.EMERGENCY_RATION.get(),
        QLMItems.PURIFIED_WATER_BOTTLE.get(),
        Items.BREAD,
        Items.COOKED_BEEF,
        Items.APPLE,
        Items.GOLDEN_APPLE,
    )

    private val miscLoot = listOf(
        QLMItems.ZOMBIE_CORE.get(),
        QLMItems.INFECTED_ESSENCE.get(),
        QLMItems.SURVIVAL_KIT.get(),
        QLMItems.REINFORCED_PARTS.get(),
        QLMItems.BIOHAZARD_SAMPLE.get(),
        QLMItems.FAKE_PLAYER_SPAWN_EGG.get(),
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

        if (!isFarEnoughFromOtherStructures(chunkX, chunkZ)) return

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 0) return

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - SHOP_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - SHOP_DEPTH / 2
        )

        try {
            generateShop(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.debug(
                "[废弃商店] 在区块 ({}, {}) 生成废弃商店", chunkX, chunkZ
            )
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[废弃商店] 生成失败: {}", e.message)
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

    private fun generateShop(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk,
        origin: BlockPos.MutableBlockPos
    ) {
        val x0 = origin.x
        val z0 = origin.z
        val groundY = origin.y
        val random = level.random

        for (dx in 0 until SHOP_WIDTH) {
            for (dz in 0 until SHOP_DEPTH) {
                val isWall = dx == 0 || dx == SHOP_WIDTH - 1 || dz == 0 || dz == SHOP_DEPTH - 1

                if (isWall) {
                    for (dy in 0 until SHOP_HEIGHT) {
                        val wallPos = BlockPos.MutableBlockPos(x0 + dx, groundY + dy, z0 + dz)
                        val isBroken = dy == 1 && random.nextDouble() < 0.25
                        when {
                            isBroken -> { }
                            dy == SHOP_HEIGHT - 1 ->
                                level.setBlock(wallPos, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3)
                            dx == SHOP_WIDTH / 2 && dz == SHOP_DEPTH - 1 && dy == 0 ->
                                level.setBlock(wallPos, Blocks.DARK_OAK_DOOR.defaultBlockState(), 3)
                            else ->
                                level.setBlock(wallPos, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3)
                        }
                    }
                }
            }
        }

        for (dx in 1 until SHOP_WIDTH - 1) {
            for (dz in 1 until SHOP_DEPTH - 1) {
                val shelfY = groundY
                val northShelf = dx % 3 == 1 && dz == 1
                val southShelf = dx % 3 == 1 && dz == SHOP_DEPTH - 2
                if (northShelf || southShelf) {
                    for (dy in 0..2) {
                        val shelfPos = BlockPos.MutableBlockPos(
                            x0 + dx,
                            groundY + dy,
                            z0 + if (northShelf) 2 else SHOP_DEPTH - 3
                        )
                        level.setBlock(shelfPos, Blocks.DARK_OAK_SLAB.defaultBlockState(), 3)
                    }
                }
            }
        }

        val chestSpots = listOf(
            Triple(x0 + 1, groundY + 1, z0 + 1),
            Triple(x0 + SHOP_WIDTH - 2, groundY + 1, z0 + 1),
            Triple(x0 + 1, groundY + 1, z0 + SHOP_DEPTH - 2),
            Triple(x0 + SHOP_WIDTH - 2, groundY + 1, z0 + SHOP_DEPTH - 2),
            Triple(x0 + SHOP_WIDTH / 2, groundY + 1, z0 + SHOP_DEPTH / 2),
        )

        for ((cx, cy, cz) in chestSpots) {
            val chestPos = BlockPos.MutableBlockPos(cx, cy, cz)
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
            val chest = level.getBlockEntity(chestPos) as? net.minecraft.world.level.block.entity.ChestBlockEntity
            if (chest != null) {
                val lootCategory = random.nextInt(4)
                val loot = when (lootCategory) {
                    0 -> medicalLoot
                    1 -> ammoLoot
                    2 -> foodLoot
                    else -> miscLoot
                }
                val itemsToAdd = 2 + random.nextInt(4)
                for (i in 0 until itemsToAdd) {
                    val item = loot.random()
                    val stack = ItemStack(item)
                    stack.count = 1 + random.nextInt(4)
                    chest.setItem(i % chest.containerSize, stack)
                }
                chest.setChanged()
            }
        }

        for (dx in 0 until SHOP_WIDTH) {
            for (dz in 0 until SHOP_DEPTH) {
                val roofPos = BlockPos.MutableBlockPos(x0 + dx, groundY + SHOP_HEIGHT, z0 + dz)
                val isBroken = random.nextDouble() < 0.15
                if (!isBroken) {
                    level.setBlock(roofPos, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3)
                }
            }
        }

        for (dx in 0 until SHOP_WIDTH) {
            val pillarPos = BlockPos.MutableBlockPos(x0 + dx, groundY + SHOP_HEIGHT + 1, z0)
            level.setBlock(pillarPos, Blocks.DARK_OAK_FENCE.defaultBlockState(), 3)
        }
    }

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}