package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

object RandomBuildingGenerator : BuildingGenerator {

    private const val SPAWN_CHANCE = 0.35
    private const val MIN_SPACING = 2
    private const val HUT_SIZE = 5

    /** 每区块仅评估一次（无论是否生成），避免重复扫描时反复掷概率 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

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

    override fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        val chunkKey = StructureGenSupport.chunkKey(chunkX, chunkZ)
        // 该区块已有其他废弃建筑，跳过防止重叠
        if (StructureGenSupport.generatedChunks.contains(chunkKey)) return false

        // 先确认区块地形已就绪（heightmap 有效）。若 ChunkEvent.Load 触发过早、
        // 区块尚未生成完，heightmap 可能为 0 —— 此时不得标记"已评估"，
        // 否则该区块会被永久跳过、建筑永不生成（旧代码在就绪检查前就标记）。
        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 0) return false

        // 区块就绪后，每区块仅评估一次（无论是否生成），保持概率语义
        if (!decidedChunks.add(chunkKey)) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8,
            surfaceY,
            chunkZ * 16 + 8
        )

        // 跨会话防重复：若建筑标志（箱子）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            StructureGenSupport.generatedChunks.add(chunkKey)
            return false
        }

        if (level.random.nextDouble() >= SPAWN_CHANCE) return false
        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, MIN_SPACING)) return false

        return try {
            generateHut(level, chunk, origin)
            StructureGenSupport.generatedChunks.add(chunkKey)
            StructureGenSupport.registerBuilding(
                chunkKey,
                net.minecraft.core.BlockPos(origin.x, origin.y, origin.z)
            )
            QLMZombieMod.LOGGER.info(
                "[随机小屋] 在区块 ({}, {}) 生成5x5小屋", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            decidedChunks.remove(chunkKey) // 生成异常时取消"已评估"，允许周期扫描重试
            QLMZombieMod.LOGGER.error("[随机小屋] 生成失败: {}", e.message)
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
        // 小屋箱子位置：(x0+1, groundY, z0+1) = (origin.x - HUT_SIZE/2 + 1, origin.y, origin.z - HUT_SIZE/2 + 1)
        val chestPos = BlockPos(
            origin.x - HUT_SIZE / 2 + 1,
            origin.y,
            origin.z - HUT_SIZE / 2 + 1
        )
        return level.getBlockState(chestPos).block ==
            net.minecraft.world.level.block.Blocks.CHEST
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
                        // 门：两格高（dy=0 下半 + dy=1 上半），位于 +z 墙正中，朝南外开，
                        // 上方 dy=2 留作门楣木板，玩家可右键开门进出
                        val isDoorPos = dx == HUT_SIZE / 2 && dz == HUT_SIZE - 1
                        val state = when {
                            isDoorPos && dy == 0 ->
                                Blocks.OAK_DOOR.defaultBlockState()
                                    .setValue(DoorBlock.FACING, Direction.SOUTH)
                                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                            isDoorPos && dy == 1 ->
                                Blocks.OAK_DOOR.defaultBlockState()
                                    .setValue(DoorBlock.FACING, Direction.SOUTH)
                                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                            else -> Blocks.OAK_PLANKS.defaultBlockState()
                        }
                        level.setBlock(wallPos, state, 3)
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
}