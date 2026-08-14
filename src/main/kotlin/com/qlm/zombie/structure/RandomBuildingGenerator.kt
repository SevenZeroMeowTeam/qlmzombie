package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.api.distmarker.Dist
import java.util.concurrent.ConcurrentHashMap

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.DEDICATED_SERVER])
object RandomBuildingGenerator {

    private const val SPAWN_CHANCE = 0.15
    private const val MIN_SPACING = 3
    private const val HUT_SIZE = 5

    private val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

    // 延迟初始化：RegistryObject.get() 必须在注册表完成注册后调用，
    // 类静态初始化时（CONSTRUCT 阶段）调用会抛出 NPE。
    private val lootItems by lazy {
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
            CDItems.SURGICAL_SCISSORS.get(),
        )
    }

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
            chunkX * 16 + 8,
            surfaceY,
            chunkZ * 16 + 8
        )

        try {
            generateHut(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.debug(
                "[随机小屋] 在区块 ({}, {}) 生成5x5小屋", chunkX, chunkZ
            )
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[随机小屋] 生成失败: {}", e.message)
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

    private fun generateHut(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk,
        origin: BlockPos.MutableBlockPos
    ) {
        val x0 = origin.x - HUT_SIZE / 2
        val z0 = origin.z - HUT_SIZE / 2
        val groundY = origin.y

        for (dx in 0 until HUT_SIZE) {
            for (dz in 0 until HUT_SIZE) {
                val bx = x0 + dx
                val bz = z0 + dz
                val isWall = dx == 0 || dx == HUT_SIZE - 1 || dz == 0 || dz == HUT_SIZE - 1

                if (isWall) {
                    for (dy in 0..2) {
                        val wallPos = BlockPos.MutableBlockPos(bx, groundY + dy, bz)
                        val isDoor = dy == 0 && dx == HUT_SIZE / 2 && dz == HUT_SIZE - 1
                        level.setBlock(
                            wallPos,
                            if (isDoor) Blocks.OAK_DOOR.defaultBlockState()
                            else Blocks.OAK_PLANKS.defaultBlockState(),
                            3
                        )
                    }
                }

                if (dx == 1 && dz == 1) {
                    val chestPos = BlockPos.MutableBlockPos(bx, groundY, bz)
                    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
                    fillChest(level, chestPos)
                }
            }
        }

        for (dx in 0 until HUT_SIZE) {
            for (dz in 0 until HUT_SIZE) {
                val roofPos = BlockPos.MutableBlockPos(x0 + dx, groundY + 3, z0 + dz)
                level.setBlock(roofPos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
            }
        }
    }

    private fun fillChest(level: net.minecraft.world.level.Level, pos: BlockPos) {
        val chest = level.getBlockEntity(pos) as? net.minecraft.world.level.block.entity.ChestBlockEntity
            ?: return

        val itemsToAdd = 3 + level.random.nextInt(3)
        for (i in 0 until itemsToAdd) {
            val item = lootItems.random()
            val stack = ItemStack(item)
            stack.count = 1 + level.random.nextInt(3)
            chest.setItem(i % chest.containerSize, stack)
        }
        chest.setChanged()
    }

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}