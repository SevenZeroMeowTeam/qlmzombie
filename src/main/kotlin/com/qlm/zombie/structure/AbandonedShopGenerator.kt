package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

object AbandonedShopGenerator : BuildingGenerator {

    private const val SHOP_WIDTH = 9
    private const val SHOP_DEPTH = 7
    private const val SHOP_HEIGHT = 4

    /** 每区块仅评估一次（无论是否生成），避免重复扫描时反复掷概率 */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    // 延迟初始化：RegistryObject.get() 必须在注册表完成注册后调用，
    // 类静态初始化时（CONSTRUCT 阶段）调用会抛出 NPE。
    private val medicalLoot by lazy {
        listOf(
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
    }

    private val ammoLoot by lazy {
        listOf(
            CDItems.RIFLE_AMMO.get(),
            CDItems.PISTOL_AMMO.get(),
            CDItems.SHOTGUN_SHELL.get(),
            CDItems.SNIPER_AMMO.get(),
            QLMItems.TACTICAL_AMMO.get(),
        )
    }

    private val foodLoot by lazy {
        listOf(
            QLMItems.EMERGENCY_RATION.get(),
            QLMItems.PURIFIED_WATER_BOTTLE.get(),
            Items.BREAD,
            Items.COOKED_BEEF,
            Items.APPLE,
            Items.GOLDEN_APPLE,
        )
    }

    private val miscLoot by lazy {
        listOf(
            QLMItems.ZOMBIE_CORE.get(),
            QLMItems.INFECTED_ESSENCE.get(),
            QLMItems.SURVIVAL_KIT.get(),
            QLMItems.REINFORCED_PARTS.get(),
            QLMItems.BIOHAZARD_SAMPLE.get(),
            QLMItems.FAKE_PLAYER_SPAWN_EGG.get(),
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

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 0) return false

        // 区块就绪后，每区块仅评估一次（无论是否生成），保持概率语义
        if (!decidedChunks.add(chunkKey)) return false

        if (level.random.nextDouble() >= QLMConfig.SHOP_CHANCE.get()) return false

        if (!StructureGenSupport.isFlatTerrain(chunk, QLMConfig.FLAT_TOLERANCE_MEDIUM.get())) return false

        if (!StructureGenSupport.isFarEnough(chunkX, chunkZ, QLMConfig.SHOP_SPACING.get())) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8 - SHOP_WIDTH / 2,
            surfaceY,
            chunkZ * 16 + 8 - SHOP_DEPTH / 2
        )

        // 跨会话防重复：若建筑标志（箱子）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            StructureGenSupport.generatedChunks.add(chunkKey)
            return false
        }

        return try {
            generateShop(level, chunk, origin)
            StructureGenSupport.generatedChunks.add(chunkKey)
            StructureGenSupport.registerBuilding(
                chunkKey,
                net.minecraft.core.BlockPos(origin.x + SHOP_WIDTH / 2, origin.y, origin.z + SHOP_DEPTH / 2)
            )
            QLMZombieMod.LOGGER.info(
                "[废弃商店] 在区块 ({}, {}) 生成废弃商店", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            decidedChunks.remove(chunkKey) // 生成异常时取消"已评估"，允许周期扫描重试
            QLMZombieMod.LOGGER.error("[废弃商店] 生成失败: {}", e.message)
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
        // 商店第一个箱子位置：(x0+1, groundY+1, z0+1)
        val chestPos = BlockPos(origin.x + 1, origin.y + 1, origin.z + 1)
        return level.getBlockState(chestPos).block ==
            net.minecraft.world.level.block.Blocks.CHEST
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
            StructureGenSupport.maybeInjectTaczWeapon(level, chestPos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
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
}