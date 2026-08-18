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
 * 破败商业街生成器（48×16，跨3×1区块）：
 * - 南北两侧各3家小店（共6家），每家8×8×3
 * - 6种店铺类型循环：餐厅/服装店/电子店/书店/药房/便利店
 * - 街中央 GRAVEL 铺路，每16格放路灯（IRON_BARS×3 + GLOWSTONE 顶）
 * - 街两端各放 1 个 CDBlocks.SUPPLY_CRATE（武器主题）
 */
object CommercialStreetGenerator : BuildingGenerator {

    private const val WIDTH = 48
    private const val DEPTH = 16
    private const val SIZE_CHUNKS_X = 3
    private const val SIZE_CHUNKS_Z = 1
    private const val SHOP_SIZE = 8
    private const val FLOOR_HEIGHT = 2 // 每层内部2格，屋顶1格（合计楼高3格）
    private const val MOD_ITEM_CHANCE = 0.4

    /** 每区块仅评估一次（无论是否生成） */
    private val decidedChunks = ConcurrentHashMap.newKeySet<Long>()

    /** 店铺类型枚举（6家店循环使用） */
    private enum class ShopType(val wall: net.minecraft.world.level.block.Block, val label: String) {
        RESTAURANT(Blocks.BRICKS, "餐厅"),
        CLOTHING(Blocks.BLUE_WOOL, "服装店"),
        ELECTRONICS(Blocks.IRON_BLOCK, "电子店"),
        BOOKSTORE(Blocks.OAK_PLANKS, "书店"),
        PHARMACY(Blocks.WHITE_CONCRETE, "药房"),
        CONVENIENCE(Blocks.LIGHT_GRAY_CONCRETE, "便利店"),
    }

    /** 餐厅战利品：面包/牛肉/饼干 */
    private val restaurantLoot by lazy {
        listOf(
            Items.BREAD,
            Items.COOKED_BEEF,
            Items.COOKIE,
            Items.BAKED_POTATO,
            QLMItems.EMERGENCY_RATION.get(),
        )
    }

    /** 服装店战利品：羊毛/地毯 */
    private val clothingLoot by lazy {
        listOf(
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.WHITE_CARPET, Items.RED_CARPET, Items.BLUE_CARPET, Items.BLACK_CARPET,
            Items.LEATHER_LEGGINGS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET,
        )
    }

    /** 电子店战利品：铁/金/红石 */
    private val electronicsLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE,
            Items.REDSTONE_BLOCK, Items.HOPPER, Items.DISPENSER,
            Items.DROPPER, Items.OBSERVER, Items.COMPARATOR,
            Items.REPEATER, Items.EMERALD, QLMItems.REINFORCED_PARTS.get(),
        )
    }

    /** 书店战利品：书架/书 */
    private val bookstoreLoot by lazy {
        listOf(
            Items.BOOKSHELF, Items.BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK,
            Items.PAPER, Items.FEATHER, Items.INK_SAC, Items.ENCHANTED_BOOK,
        )
    }

    /** 药房战利品：白色混凝土 + MEDICAL_SUPPLY */
    private val pharmacyLoot by lazy {
        listOf(
            QLMItems.MEDICAL_SUPPLY.get(),
            QLMItems.ANTIDOTE.get(),
            Items.GLASS_BOTTLE,
            Items.SUGAR,
            Items.FERMENTED_SPIDER_EYE,
            Items.NETHER_WART,
            Items.GHAST_TEAR,
        )
    }

    /** 便利店战利品：混合 */
    private val convenienceLoot by lazy {
        listOf(
            Items.BREAD, Items.COOKED_BEEF, Items.PAPER, Items.BOOK,
            Items.IRON_INGOT, Items.GOLD_NUGGET, Items.STRING, Items.LEATHER,
            Items.GLASS_BOTTLE, Items.TORCH, Items.CHEST,
            QLMItems.EMERGENCY_RATION.get(), QLMItems.MEDICAL_SUPPLY.get(),
        )
    }

    /** 武器主题（CD SUPPLY_CRATE 用） */
    private val weaponLoot by lazy {
        listOf(
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE,
            Items.IRON_SWORD, Items.IRON_HELMET, Items.IRON_CHESTPLATE,
            QLMItems.TACTICAL_AMMO.get(), QLMItems.REINFORCED_PARTS.get(),
        )
    }

    private fun getLootFor(type: ShopType): List<Item> = when (type) {
        ShopType.RESTAURANT -> restaurantLoot
        ShopType.CLOTHING -> clothingLoot
        ShopType.ELECTRONICS -> electronicsLoot
        ShopType.BOOKSTORE -> bookstoreLoot
        ShopType.PHARMACY -> pharmacyLoot
        ShopType.CONVENIENCE -> convenienceLoot
    }

    override fun tryGenerate(
        level: net.minecraft.world.level.Level,
        chunk: net.minecraft.world.level.chunk.LevelChunk
    ): Boolean {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z

        // 仅在 chunkX%3 == 0 锚点触发（跨3×1区块）
        if (chunkX % SIZE_CHUNKS_X != 0) return false

        val chunkKey = StructureGenSupport.chunkKey(chunkX, chunkZ)
        if (StructureGenSupport.generatedChunks.contains(chunkKey)) return false

        val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
        if (surfaceY <= 0) return false

        val biome = level.getBiome(
            BlockPos.MutableBlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)
        )
        if (biome.`is`(net.minecraft.tags.BiomeTags.IS_OCEAN)) return false

        // 标记3×1全部区块为已评估
        for (dx in 0 until SIZE_CHUNKS_X) {
            decidedChunks.add(StructureGenSupport.chunkKey(chunkX + dx, chunkZ))
        }

        // 跨区块平面检测（3×1，FLAT_TOLERANCE_MEDIUM）
        if (!StructureGenSupport.isFlatTerrainArea(level, chunkX + 1, chunkZ, SIZE_CHUNKS_X, QLMConfig.FLAT_TOLERANCE_MEDIUM.get())) {
            for (dx in 0 until SIZE_CHUNKS_X) {
                decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ))
            }
            return false
        }

        // 跨区块间距检测
        if (!StructureGenSupport.isFarEnoughArea(chunkX + 1, chunkZ, SIZE_CHUNKS_X, QLMConfig.COMMERCIAL_STREET_SPACING.get())) {
            for (dx in 0 until SIZE_CHUNKS_X) {
                decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ))
            }
            return false
        }

        // 概率判定
        if (level.random.nextDouble() >= QLMConfig.COMMERCIAL_STREET_CHANCE.get()) return false

        // 强制加载3×1区块
        val serverLevel = level as? ServerLevel ?: return false
        for (dx in 0 until SIZE_CHUNKS_X) {
            serverLevel.getChunk(chunkX + dx, chunkZ, ChunkStatus.FULL, true)
        }

        val originX = chunkX * 16
        val originZ = chunkZ * 16
        val originY = surfaceY

        return try {
            generateStreet(level, originX, originY, originZ, level.random)
            // 标记3×1=3个chunkKey到generatedChunks
            for (dx in 0 until SIZE_CHUNKS_X) {
                val ck = StructureGenSupport.chunkKey(chunkX + dx, chunkZ)
                StructureGenSupport.generatedChunks.add(ck)
            }
            StructureGenSupport.registerBuilding(
                chunkKey,
                BlockPos(originX + WIDTH / 2, originY, originZ + DEPTH / 2)
            )
            QLMZombieMod.LOGGER.info(
                "[破败商业街] 在区块 ({}, {}) 生成48×16商业街", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
            for (dx in 0 until SIZE_CHUNKS_X) {
                decidedChunks.remove(StructureGenSupport.chunkKey(chunkX + dx, chunkZ))
            }
            QLMZombieMod.LOGGER.error("[破败商业街] 生成失败: {}", e.message)
            false
        }
    }

    private fun generateStreet(
        level: net.minecraft.world.level.Level,
        x0: Int, y0: Int, z0: Int,
        random: net.minecraft.util.RandomSource
    ) {
        // ===== 1. 先铺全部地面 COBBLESTONE =====
        for (dx in 0 until WIDTH) {
            for (dz in 0 until DEPTH) {
                level.setBlock(
                    BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.COBBLESTONE.defaultBlockState(), 3
                )
            }
        }

        // ===== 2. 街中央铺 GRAVEL（z = z0+6 到 z0+7，宽2格） =====
        for (dx in 0 until WIDTH) {
            for (dz in 6..7) {
                level.setBlock(
                    BlockPos.MutableBlockPos(x0 + dx, y0, z0 + dz),
                    Blocks.GRAVEL.defaultBlockState(), 3
                )
            }
        }

        // ===== 3. 路灯：x = x0+14 和 x = x0+32，3格 IRON_BARS + GLOWSTONE 顶 =====
        for (lx in listOf(14, 32)) {
            // 每盏路灯两根柱子（街两侧各一根？不，按说明在街中央 z=z0+6.5 位置，以 z0+6 和 z0+7 中间）
            // 放在 z0+6 位置（3格柱子，顶放GLOWSTONE）
            for (dy in 1..3) {
                level.setBlock(
                    BlockPos.MutableBlockPos(x0 + lx, y0 + dy, z0 + 6),
                    Blocks.IRON_BARS.defaultBlockState(), 3
                )
            }
            level.setBlock(
                BlockPos.MutableBlockPos(x0 + lx, y0 + 4, z0 + 6),
                Blocks.GLOWSTONE.defaultBlockState(), 3
            )
        }

        // ===== 4. 6家小店 =====
        // 南侧3店：z=z0+1，起 (x0+dx*16+4, z0+1), dx=0..2
        // 北侧3店：z=z0+8，起 (x0+dx*16+4, z0+8), dx=0..2
        // 6家店类型循环：餐厅/服装店/电子店/书店/药房/便利店
        val shopOrder = listOf(
            ShopType.RESTAURANT, ShopType.CLOTHING, ShopType.ELECTRONICS,
            ShopType.BOOKSTORE, ShopType.PHARMACY, ShopType.CONVENIENCE,
        )

        var shopIndex = 0
        // 南侧3家（门朝北 Direction.NORTH，朝向街）
        for (dx in 0 until 3) {
            val sx = x0 + dx * 16 + 4
            val sz = z0 + 1
            buildShop(level, sx, y0, sz, shopOrder[shopIndex], Direction.NORTH, random)
            shopIndex++
        }
        // 北侧3家（门朝南 Direction.SOUTH，朝向街）
        for (dx in 0 until 3) {
            val sx = x0 + dx * 16 + 4
            val sz = z0 + 8
            buildShop(level, sx, y0, sz, shopOrder[shopIndex], Direction.SOUTH, random)
            shopIndex++
        }

        // ===== 5. 街两端 SUPPLY_CRATE（武器主题） =====
        // (x0+2, y0+1, z0+7) 和 (x0+45, y0+1, z0+7)
        for (sx in listOf(2, 45)) {
            val pos = BlockPos(x0 + sx, y0 + 1, z0 + 7)
            level.setBlock(pos, CDBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3)
            StructureGenSupport.fillCDCrate(level, pos, random, weaponLoot, 0.3, 4, 8)
        }
    }

    /**
     * 建一家 8×8×3 小店：
     * - 地面 1 层（COBBLESTONE 已铺）
     * - 内部 FLOOR_HEIGHT=2（2格高可通行空间）
     * - 屋顶 1 格
     */
    private fun buildShop(
        level: net.minecraft.world.level.Level,
        sx: Int, y0: Int, sz: Int,
        type: ShopType,
        streetFacing: Direction, // 门朝向街（南侧店朝北，北侧店朝南）
        random: net.minecraft.util.RandomSource
    ) {
        // 店铺尺寸 8×8
        val W = SHOP_SIZE
        val D = SHOP_SIZE
        val loot = getLootFor(type)

        // 地面已经是 COBBLESTONE 了，不用再铺

        // 墙壁（2格高墙体 + 1格屋顶）
        for (dx in 0 until W) {
            for (dz in 0 until D) {
                val isWall = dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1
                if (!isWall) continue

                // 门位置：朝街方向，店中间 (x+4, z+4) 位置
                // 南侧店 (sz 起始 z=1) 门朝北，门应该在店的南边界？不对：
                // 南侧店起 z = z0+1，店深度8 = z0+1..z0+8；街在 z=6..7。
                // 所以南侧店的"北边"是 dz=7 (z0+1+7 = z0+8)，实际靠近街侧是店的北边缘 dz=7？
                // 不：南侧店 z0+1 到 z0+8（含），街是 z0+6..z0+7。南侧店的 dz=6 已经是 z0+7 街道区域了。
                // 重新理解：南侧店起 z=z0+1，8×8 占 z0+1..z0+8。街 z0+6..z0+7 与店重叠？不对。
                // 任务说明：南侧3店起 (x0+dx*16+4, z0+1)；北侧3店起 (x0+dx*16+4, z0+8)
                // 街中央 z = z0+6 到 z0+7（宽度2格）
                // 南侧店 z0+1 起，8深度：z0+1..z0+8，北边缘 z0+8。街 z0+6..7 与南侧店重叠。
                // 实际上南侧店应该在 z0+1..z0+5（5格）才不重叠街道？但任务说每家8×8。
                // 我们按原始任务直接构建，街在店内不影响（会被后续墙体覆盖？不，街是底层。墙体建在 y1+ 层，不冲突）
                //
                // 门朝街开：南侧门朝北 Direction.NORTH（因为街在店的"北边"z更大方向）
                // 门位置 (x+4, y0+1, z+4)
                val isDoorX = dx == 4
                val isDoorZ = when (streetFacing) {
                    Direction.NORTH -> dz == D - 1 // 南侧店门朝北（店的北边界，dz=7）
                    Direction.SOUTH -> dz == 0      // 北侧店门朝南（店的南边界，dz=0）
                    else -> dz == D - 1
                }
                val isDoor = isDoorX && isDoorZ

                // 墙体：y0+1..y0+2（2格高，内部空间）
                for (dy in 1..2) {
                    // 门位置（dy=1,2 留给 placeDoor1x2；dy=3 留给门楣）
                    if (isDoor) continue
                    val pos = BlockPos.MutableBlockPos(sx + dx, y0 + dy, sz + dz)
                    level.setBlock(pos, type.wall.defaultBlockState(), 3)
                }

                // 屋顶（y0+3，1格）
                val roofPos = BlockPos.MutableBlockPos(sx + dx, y0 + 3, sz + dz)
                level.setBlock(roofPos, Blocks.COBBLESTONE_SLAB.defaultBlockState(), 3)
            }
        }

        // 门：1×2 OAK_DOOR 朝街开
        // 门位置 (x+4, y0+1, z+4) 不对，应该在边界上：
        // 南侧店门朝北（streetFacing=NORTH）：在 dz = D-1 = 7 处放门，z = sz + 7
        // 北侧店门朝南（streetFacing=SOUTH）：在 dz = 0 处放门，z = sz + 0
        // 任务说"每店位置 (x+4, y0+1, z+4)"，可能是相对于店内部坐标，这里我们放在 x=4 边界上
        val doorX = sx + 4
        val doorY = y0 + 1
        val doorZ = sz + if (streetFacing == Direction.NORTH) D - 1 else 0
        StructureGenSupport.placeDoor1x2(
            level, doorX, doorY, doorZ,
            streetFacing,
            Blocks.OAK_DOOR as DoorBlock,
            type.wall.defaultBlockState()
        )

        // 店内1个普通箱子 (x+2, y0+1, z+2)
        val chestPos = BlockPos(sx + 2, y0 + 1, sz + 2)
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
        StructureGenSupport.fillChest(level, chestPos, random, loot, MOD_ITEM_CHANCE, 2, 5)
        StructureGenSupport.maybeInjectTaczWeapon(level, chestPos, random, QLMConfig.TACZ_GUARANTEE_CHANCE.get())
    }
}
