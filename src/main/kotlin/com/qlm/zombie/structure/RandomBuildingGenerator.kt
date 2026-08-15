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
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.concurrent.ConcurrentHashMap

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object RandomBuildingGenerator {

    private const val SPAWN_CHANCE = 0.35
    private const val MIN_SPACING = 2
    private const val HUT_SIZE = 5
    // 玩家登录时扫描周围已加载区块的半径（半径 3 = 7x7 = 49 个区块）
    private const val LOGIN_SCAN_RADIUS = 3

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
        tryGenerate(level, chunk)
    }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity ?: return
        val level = player.level()
        if (level.isClientSide) return
        val serverLevel = level as? net.minecraft.server.level.ServerLevel ?: return

        QLMZombieMod.LOGGER.info(
            "[随机小屋] 玩家 {} 登录, 延迟2秒后扫描周围区块补生成",
            player.name.string
        )
        // 延迟 40 tick (2秒) 扫描，确保玩家周围区块已加载完成
        // 玩家登录瞬间 spawn chunks 可能仍在异步加载，getChunkNow 会返回 null
        val server = serverLevel.server
        server.tell(net.minecraft.server.TickTask(server.tickCount + 40, Runnable {
            try {
                scanAndGenerate(serverLevel, player)
            } catch (e: Exception) {
                QLMZombieMod.LOGGER.error("[随机小屋] 延迟扫描异常: {}", e.message)
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
            "[随机小屋] 玩家 {} 延迟扫描完成: 扫描{}区块, 新生成{}小屋",
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
        if (surfaceY <= 0) return false

        val origin = BlockPos.MutableBlockPos(
            chunkX * 16 + 8,
            surfaceY,
            chunkZ * 16 + 8
        )

        // 跨会话防重复：若建筑标志（箱子）已存在，记入缓存并跳过
        if (hasExistingStructure(level, origin)) {
            generatedChunks.add(chunkKey)
            return false
        }

        if (level.random.nextDouble() >= SPAWN_CHANCE) return false
        if (!isFarEnoughFromOtherStructures(chunkX, chunkZ)) return false

        return try {
            generateHut(level, chunk, origin)
            generatedChunks.add(chunkKey)
            QLMZombieMod.LOGGER.info(
                "[随机小屋] 在区块 ({}, {}) 生成5x5小屋", chunkX, chunkZ
            )
            true
        } catch (e: Exception) {
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

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}