package com.qlm.zombie.ai.task

import com.qlm.zombie.QLMZombieMod
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class BuildTask(
    owner: LivingEntity,
    private val size: Int = 5
) : Task(owner, owner.position()) {

    private data class BuildSlot(
        val pos: BlockPos,
        val block: Block
    )

    private val level = owner.level()
    private val slots = mutableListOf<BuildSlot>()
    private var currentSlotIndex: Int = 0
    private var blocksPlaced: Int = 0
    private var placeCooldown: Int = 0

    init {
        timeoutTicks = 3000
    }

    override fun onStart() {
        val center = owner.blockPosition()
        val half = size / 2

        val foundationLevel = center.y

        val floorBlock = findBlockForBuilding()

        for (x in -half..half) {
            for (z in -half..half) {
                val pos = BlockPos(center.x + x, foundationLevel, center.z + z)
                val state = level.getBlockState(pos)
                if (state.isAir) {
                    slots.add(BuildSlot(pos, floorBlock))
                }
            }
        }

        for (y in 1..size) {
            for (x in -half..half) {
                for (z in listOf(-half, half)) {
                    val pos = BlockPos(center.x + x, foundationLevel + y, center.z + z)
                    val state = level.getBlockState(pos)
                    if (state.isAir) {
                        slots.add(BuildSlot(pos, floorBlock))
                    }
                }
            }
            for (x in -half..half) {
                val pos = BlockPos(center.x + half, foundationLevel + y, center.z + x)
                val state = level.getBlockState(pos)
                if (state.isAir) {
                    slots.add(BuildSlot(pos, floorBlock))
                }
            }
        }

        currentSlotIndex = 0
        QLMZombieMod.LOGGER.info("[BuildTask] 准备建造 ${size}x${size} 小屋, 共 ${slots.size} 个位置")
    }

    private fun findBlockForBuilding(): Block {
        val player = owner as? Player
        if (player != null) {
            val inventory = player.inventory
            for (i in 0 until inventory.containerSize) {
                val stack = inventory.getItem(i)
                val block = Block.byItem(stack.item)
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    return block
                }
            }
        }
        return net.minecraft.world.level.block.Blocks.COBBLESTONE
    }

    override fun doTick() {
        placeCooldown--

        if (currentSlotIndex >= slots.size) {
            complete()
            return
        }

        if (placeCooldown > 0) return

        val slot = slots[currentSlotIndex]
        val state = level.getBlockState(slot.pos)

        if (!state.isAir) {
            level.destroyBlock(slot.pos, true)
            placeCooldown = 5
            return
        }

        if (!state.isAir) {
            currentSlotIndex++
            return
        }

        val eyePos = owner.position().add(0.0, owner.eyeHeight.toDouble(), 0.0)
        val targetCenter = Vec3.atCenterOf(slot.pos)
        val distance = eyePos.distanceTo(targetCenter)

        if (distance > 4.5) {
            moveToward(targetCenter)
            return
        }

        val blockToPlace = findBlockForBuilding()
        val placeState = blockToPlace.defaultBlockState()

        val result = level.getBlockState(slot.pos)
        if (result.isAir) {
            level.setBlock(slot.pos, placeState, 3)
            blocksPlaced++
            placeCooldown = 10

            val progress = (blocksPlaced.toFloat() / slots.size.toFloat()) * 100f
            onProgressUpdate(progress)
        }

        currentSlotIndex++
    }

    private fun moveToward(dest: Vec3) {
        val dir = dest.subtract(owner.position()).normalize()
        owner.setDeltaMovement(
            dir.x * 0.25,
            dir.y * 0.05,
            dir.z * 0.25
        )
        owner.hurtMarked = true
    }

    override fun onStop(cancelled: Boolean) {
        if (cancelled) {
            QLMZombieMod.LOGGER.debug("[BuildTask] 建造任务已取消, 已放置 $blocksPlaced 个方块")
        }
    }

    fun getBlocksPlaced(): Int = blocksPlaced
    fun getTotalSlots(): Int = slots.size
}