package com.qlm.zombie.structure

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.item.QLMItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.registries.ForgeRegistries
import java.util.concurrent.ConcurrentHashMap

/**
 * 废弃建筑生成器统一接口。
 * 所有生成器实现该接口，由 [BuildingGenScheduler] 统一驱动，
 * 保证生成时机可靠（区块加载 + 登录扫描 + 周期扫描三层兜底）。
 */
interface BuildingGenerator {
    /**
     * 尝试在指定区块生成建筑。
     * 实现方需保证：
     *  - 每区块仅评估一次（decidedChunks），多次调用幂等
     *  - 生成成功后写入 [StructureGenSupport.generatedChunks] 防止重叠
     * @return 是否成功生成了建筑
     */
    fun tryGenerate(level: Level, chunk: LevelChunk): Boolean
}

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

    /** 填充箱子：主题物品 + 概率混入其他模组物品（保证至少 1 个其他模组物品） */
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
        var modAdded = false
        for (i in 0 until itemsToAdd) {
            // 默认按概率选其他模组物品；最后一个槽位若还没混入过则强制补一个，保证宝箱必有其他模组物品
            var useMod = modList.isNotEmpty() && random.nextDouble() < modItemChance
            if (modList.isNotEmpty() && !modAdded && i == itemsToAdd - 1) {
                useMod = true
            }
            val item: Item = if (useMod) {
                modAdded = true
                modList[random.nextInt(modList.size)]
            } else {
                themedLoot[random.nextInt(themedLoot.size)]
            }
            val stack = ItemStack(item)
            stack.count = 1 + random.nextInt(4)
            chest.setItem(i % chest.containerSize, stack)
        }
        chest.setChanged()
        // 五类废弃建筑物资注入（军用/医用/平民/稀有平民/警用）
        maybeInjectSupplies(level, pos, random)
    }

    // ================= 废弃建筑五类物资 =================
    // 用户需求：军用/医用/平民/稀有平民/警用物资添加到废弃建筑物中。
    // 下界/末地物品（下界合金锭/末影珍珠等）不放箱子——它们只能靠击杀敌对生物获取。

    /** 军用物资：CD 枪械/弹药/防具/投掷物 */
    private val militarySupplies by lazy {
        listOf(
            CDItems.AK47.get(), CDItems.M4A1.get(), CDItems.MP5.get(),
            CDItems.RIFLE_AMMO.get(), CDItems.PISTOL_AMMO.get(),
            CDItems.SHOTGUN_SHELL.get(), CDItems.SNIPER_AMMO.get(),
            CDItems.BALLISTIC_HELMET.get(), CDItems.PLATE_CARRIER.get(),
            CDItems.TACTICAL_VEST.get(), CDItems.COMBAT_BOOTS.get(),
            CDItems.FRAGMENT_GRENADE.get(), CDItems.FLASHBANG.get(),
            QLMItems.TACTICAL_AMMO.get(),
        )
    }

    /** 医用物资：CD 医疗物品 + QLM 医疗补给/解毒剂 */
    private val medicalSupplies by lazy {
        listOf(
            CDItems.BANDAGE.get(), CDItems.FIRST_AID_KIT.get(),
            CDItems.ADRENALINE_SYRINGE.get(), CDItems.PAINKILLERS.get(),
            CDItems.TOURNIQUET.get(), CDItems.SALINE_BAG.get(),
            CDItems.SPLINT.get(), CDItems.SURGICAL_SCISSORS.get(),
            QLMItems.MEDICAL_SUPPLY.get(), QLMItems.ANTIDOTE.get(),
            QLMItems.PURIFIED_WATER_BOTTLE.get(),
        )
    }

    /** 平民物资：食物/基础工具/日用品 */
    private val civilianSupplies by lazy {
        listOf(
            Items.BREAD, Items.APPLE, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN, Items.BAKED_POTATO,
            Items.IRON_INGOT, Items.IRON_SWORD, Items.IRON_PICKAXE,
            Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HELMET,
            Items.STRING, Items.LEATHER, Items.PAPER, Items.COAL, Items.STICK,
            QLMItems.EMERGENCY_RATION.get(), QLMItems.SURVIVAL_KIT.get(),
        )
    }

    /** 稀有平民物资：金银/钻石/附魔书/经验瓶等稀有品 */
    private val rareCivilianSupplies by lazy {
        listOf(
            Items.GOLD_INGOT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.DIAMOND, Items.EMERALD, Items.DIAMOND_SWORD, Items.DIAMOND_CHESTPLATE,
            Items.ENCHANTED_BOOK, Items.EXPERIENCE_BOTTLE,
            QLMItems.REINFORCED_PARTS.get(), QLMItems.BIOHAZARD_SAMPLE.get(),
        )
    }

    /** 警用物资：CD 手枪/近战/盾牌 */
    private val policeSupplies by lazy {
        listOf(
            CDItems.GLOCK17.get(), CDItems.DESERT_EAGLE.get(),
            CDItems.PISTOL_AMMO.get(),
            CDItems.COMBAT_KNIFE.get(), CDItems.BOWIE_KNIFE.get(), CDItems.CROWBAR.get(),
            Items.SHIELD, Items.IRON_SWORD, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
        )
    }

    /**
     * 向箱子按概率注入 1-2 个五类物资（民用/医用常见，军用/警用中等，稀有较低）。
     * 在 fillChest / fillCDCrate 末尾调用。
     */
    fun maybeInjectSupplies(
        level: Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource,
        chance: Double = 0.6
    ) {
        if (random.nextDouble() >= chance) return
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity !is Container) return
        val container = blockEntity
        val count = 1 + random.nextInt(2)
        for (i in 0 until count) {
            // 权重：民用 30% / 医用 25% / 军用 20% / 警用 15% / 稀有 10%
            val roll = random.nextDouble()
            val list = when {
                roll < 0.30 -> civilianSupplies
                roll < 0.55 -> medicalSupplies
                roll < 0.75 -> militarySupplies
                roll < 0.90 -> policeSupplies
                else -> rareCivilianSupplies
            }
            if (list.isEmpty()) continue
            val stack = ItemStack(list[random.nextInt(list.size)])
            stack.count = 1 + random.nextInt(3)
            for (slot in 0 until container.containerSize) {
                if (container.getItem(slot).isEmpty) {
                    container.setItem(slot, stack)
                    break
                }
            }
        }
        if (container is ChestBlockEntity) container.setChanged()
        else container.setChanged()
    }

    // ================= 平面地形检测 =================

    /**
     * 单区块平面检测：采样区块内多个点的高度，计算 max-min 高度差。
     * @param chunk 待检测区块
     * @param tolerance 容差（如 3 = 高度差不超过 3 格视为平坦）
     * @param sampleStep 采样步长（如 4 = 每 4 格采样一次，共 4x4=16 个点）
     * @return true 如果地形平坦
     */
    fun isFlatTerrain(
        chunk: LevelChunk,
        tolerance: Int = 3,
        sampleStep: Int = 4
    ): Boolean {
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (dx in 0 until 16 step sampleStep) {
            for (dz in 0 until 16 step sampleStep) {
                val y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz)
                if (y <= 0) return false
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return (maxY - minY) <= tolerance
    }

    /**
     * 跨区块平面检测：在 sizeChunks×sizeChunks 范围内每区块采样中心点。
     * 用于大型建筑（如128×128军事基地）的预选址。
     * @return true 如果所有区块已加载且地形平坦
     */
    fun isFlatTerrainArea(
        level: Level,
        centerChunkX: Int,
        centerChunkZ: Int,
        sizeChunks: Int,
        tolerance: Int = 4
    ): Boolean {
        val half = sizeChunks / 2
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        val serverLevel = level as? ServerLevel ?: return false
        for (cx in -half until half) {
            for (cz in -half until half) {
                val chunk = serverLevel.chunkSource.getChunkNow(centerChunkX + cx, centerChunkZ + cz)
                    ?: return false // 周边未加载，等下次补足
                val y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
                if (y <= 0) return false
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return (maxY - minY) <= tolerance
    }

    /**
     * 跨区块间距检测：检查 sizeChunks×sizeChunks 范围内是否有已生成建筑。
     */
    fun isFarEnoughArea(
        chunkX: Int,
        chunkZ: Int,
        sizeChunks: Int,
        spacing: Int
    ): Boolean {
        val half = sizeChunks / 2
        for (dx in (-half - spacing) until (half + spacing + 1)) {
            for (dz in (-half - spacing) until (half + spacing + 1)) {
                if (generatedChunks.contains(chunkKey(chunkX + dx, chunkZ + dz))) return false
            }
        }
        return true
    }

    // ================= TACZ 武器保底 =================

    @Volatile
    private var taczWeapons: List<Item>? = null

    /**
     * 扫描 TACZ (Timeless and Classics Zero) 模组的武器物品。
     * TACZ 物品注册命名空间为 "tacz"。
     */
    fun findTaczWeapons(): List<Item> {
        if (taczWeapons == null) {
            synchronized(this) {
                if (taczWeapons == null) {
                    val scanned = ArrayList<Item>()
                    try {
                        for (entry in ForgeRegistries.ITEMS.entries) {
                            val loc = entry.key.location()
                            if (loc.namespace == "tacz" ||
                                loc.path.contains("tacz") ||
                                loc.path.contains("gun")) {
                                val path = loc.path
                                // 排除配件/弹药
                                if (path.contains("ammo") || path.contains("scope") ||
                                    path.contains("magazine") || path.contains("stock") ||
                                    path.contains("barrel") || path.contains("grip") ||
                                    path.contains("muzzle") || path.contains("bayonet")) continue
                                scanned.add(entry.value)
                            }
                        }
                        QLMZombieMod.LOGGER.info(
                            "[建筑生成] 扫描到 {} 个 TACZ 武器物品可用于保底", scanned.size
                        )
                    } catch (e: Exception) {
                        QLMZombieMod.LOGGER.warn("[建筑生成] TACZ 武器扫描失败: {}", e.message)
                    }
                    taczWeapons = scanned
                }
            }
        }
        return taczWeapons ?: emptyList()
    }

    /**
     * 5% 概率向箱子塞入一把 TACZ 武器作为保底。
     * 应在 fillChest / fillCDCrate 之后调用。
     */
    fun maybeInjectTaczWeapon(
        level: Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource,
        chance: Double = 0.05
    ) {
        if (random.nextDouble() >= chance) return
        val taczList = findTaczWeapons()
        if (taczList.isEmpty()) return
        val chest = level.getBlockEntity(pos) as? ChestBlockEntity ?: return
        for (slot in 0 until chest.containerSize) {
            if (chest.getItem(slot).isEmpty) {
                val weapon = taczList[random.nextInt(taczList.size)]
                chest.setItem(slot, ItemStack(weapon))
                chest.setChanged()
                return
            }
        }
        // 无空槽则替换最后一个
        val weapon = taczList[random.nextInt(taczList.size)]
        chest.setItem(chest.containerSize - 1, ItemStack(weapon))
        chest.setChanged()
    }

    // ================= CD 风格箱子填充 =================

    /**
     * 填充 CD 风格箱子（SupplyCrateBlockEntity 等）。
     * 与 fillChest 区别：更多物品数量，主题物品为主，5% 保底 TACZ 武器。
     * 若 CD 箱子未实现 Container 接口，则 fallback 到 fillChest。
     */
    fun fillCDCrate(
        level: Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource,
        themedLoot: List<Item>,
        modItemChance: Double = 0.3,
        minItems: Int = 4,
        maxItems: Int = 8
    ) {
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity is net.minecraft.world.Container) {
            val container = blockEntity
            val modList = getModItems()
            val itemsToAdd = minItems + random.nextInt(maxItems - minItems + 1)
            var modAdded = false
            for (i in 0 until itemsToAdd) {
                var useMod = modList.isNotEmpty() && random.nextDouble() < modItemChance
                if (modList.isNotEmpty() && !modAdded && i == itemsToAdd - 1) useMod = true
                val item = if (useMod) {
                    modAdded = true
                    modList[random.nextInt(modList.size)]
                } else themedLoot[random.nextInt(themedLoot.size)]
                val stack = ItemStack(item)
                stack.count = 1 + random.nextInt(3)
                container.setItem(i % container.containerSize, stack)
            }
            if (blockEntity is ChestBlockEntity) blockEntity.setChanged()
            else blockEntity.setChanged()
        } else {
            // CD 箱子未实现 Container，fallback
            fillChest(level, pos, random, themedLoot, modItemChance, minItems, maxItems)
        }
        // 5% 保底 TACZ 武器
        maybeInjectTaczWeapon(level, pos, random, 0.05)
        // 五类废弃建筑物资注入（军用/医用/平民/稀有平民/警用）
        maybeInjectSupplies(level, pos, random)
    }

    // ================= 统一门放置工具 =================

    /**
     * 在墙体上放置 1 格宽 × 2 格高的门。
     * 统一所有建筑生成器的门规格，方便其他模组的防御物品
     * （如铁丝网、荆棘、爆炸陷阱等）在门口留 1 格通道进出。
     *
     * @param level 世界
     * @param x 门 X 坐标
     * @param y 门下格 Y 坐标
     * @param z 门 Z 坐标
     * @param facing 门朝向（朝外）
     * @param doorBlock 门方块（Blocks.IRON_DOOR / OAK_DOOR 等）
     * @param lintelBlock 门楣方块（一般为墙体材质）
     */
    fun placeDoor1x2(
        level: Level,
        x: Int, y: Int, z: Int,
        facing: Direction,
        doorBlock: DoorBlock,
        lintelBlock: BlockState
    ) {
        val lowerPos = BlockPos(x, y, z)
        val upperPos = BlockPos(x, y + 1, z)
        val lintelPos = BlockPos(x, y + 2, z)

        level.setBlock(
            lowerPos,
            doorBlock.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
            3
        )
        level.setBlock(
            upperPos,
            doorBlock.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
            3
        )
        // 门楣（防止僵尸从门顶进入）
        level.setBlock(lintelPos, lintelBlock, 3)
    }
}
