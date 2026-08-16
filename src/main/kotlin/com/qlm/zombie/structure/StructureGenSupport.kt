package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraftforge.registries.ForgeRegistries
import java.util.concurrent.ConcurrentHashMap

/**
 * 建筑生成共享支持：
 *  - 共享已生成区块集合（防止多个新建筑生成器在同一/相邻区块重叠）
 *  - 自动扫描注册的其他模组物品（无需手动注册），填充箱子
 */
object StructureGenSupport {

    /** 所有新建筑生成器共享的已生成区块集合 */
    val generatedChunks = ConcurrentHashMap.newKeySet<Long>()

    /** 已生成建筑的中心位置（用于建筑内刷怪） */
    private val buildingCenters = ConcurrentHashMap<Long, BlockPos>()

    /** 注册一个已生成的建筑中心（chunkKey -> 中心坐标） */
    fun registerBuilding(chunkKey: Long, center: BlockPos) {
        buildingCenters[chunkKey] = center.immutable()
    }

    /** 获取所有已生成建筑的中心位置 */
    fun getBuildingCenters(): Collection<BlockPos> = buildingCenters.values

    fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    /** 与已生成建筑保持最小间距 */
    fun isFarEnough(chunkX: Int, chunkZ: Int, spacing: Int): Boolean {
        for (dx in -spacing..spacing) {
            for (dz in -spacing..spacing) {
                if (dx == 0 && dz == 0) continue
                if (generatedChunks.contains(chunkKey(chunkX + dx, chunkZ + dz))) return false
            }
        }
        return true
    }

    // ================= 其他模组物品自动注册 =================
    // 延迟缓存：注册表完成后扫描一次所有非 minecraft / qlmzombie 命名空间的物品
    @Volatile
    private var modItems: List<Item>? = null

    fun getModItems(): List<Item> {
        if (modItems == null) {
            synchronized(this) {
                if (modItems == null) {
                    val scanned = ArrayList<Item>()
                    try {
                        for (entry in ForgeRegistries.ITEMS.entries) {
                            val namespace = entry.key.location().namespace
                            if (namespace != "minecraft" && namespace != "qlmzombie") {
                                val stack = ItemStack(entry.value)
                                if (!stack.isEmpty) scanned.add(entry.value)
                            }
                        }
                        QLMZombieMod.LOGGER.info("[建筑生成] 自动扫描到 {} 个其他模组物品可用于宝箱", scanned.size)
                    } catch (e: Exception) {
                        QLMZombieMod.LOGGER.warn("[建筑生成] 扫描其他模组物品失败: {}", e.message)
                    }
                    modItems = scanned
                }
            }
        }
        return modItems ?: emptyList()
    }

    /** 填充箱子：主题物品 + 概率混入其他模组物品 */
    fun fillChest(
        level: Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource,
        themedLoot: List<Item>,
        modItemChance: Double = 0.5,
        minItems: Int = 2,
        maxItems: Int = 4
    ) {
        val chest = level.getBlockEntity(pos) as? ChestBlockEntity ?: return
        val modList = getModItems()
        val itemsToAdd = minItems + random.nextInt(maxItems - minItems + 1)
        for (i in 0 until itemsToAdd) {
            val item: Item = if (modList.isNotEmpty() && random.nextDouble() < modItemChance) {
                modList[random.nextInt(modList.size)]
            } else {
                themedLoot[random.nextInt(themedLoot.size)]
            }
            val stack = ItemStack(item)
            stack.count = 1 + random.nextInt(4)
            chest.setItem(i % chest.containerSize, stack)
        }
        chest.setChanged()
    }
}
