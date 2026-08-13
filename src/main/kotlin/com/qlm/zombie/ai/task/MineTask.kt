package com.qlm.zombie.ai.task

import com.qlm.zombie.QLMZombieMod
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

class MineTask(
    owner: LivingEntity,
    private val radius: Int = 5,
    private val filter: String = ""
) : Task(owner, owner.position()) {

    private var currentTarget: BlockPos? = null
    private var blocksMined: Int = 0
    private var totalBlocksInRadius: Int = 0
    private var searchCooldown: Int = 0
    private val level = owner.level()

    init {
        timeoutTicks = 2000
    }

    override fun onStart() {
        findNextTarget()
    }

    override fun doTick() {
        searchCooldown--

        val target = currentTarget
        if (target == null) {
            if (searchCooldown <= 0) {
                findNextTarget()
                searchCooldown = 20
            }
            return
        }

        if (!isBlockValid(target)) {
            findNextTarget()
            return
        }

        val ownerPos = owner.position()
        val targetCenter = Vec3.atCenterOf(target)
        val distance = ownerPos.distanceTo(targetCenter)

        if (distance > 6.0) {
            moveToward(targetCenter)
            return
        }

        val state = level.getBlockState(target)
        if (!state.isAir) {
            level.destroyBlock(target, true)
            blocksMined++
            val progress = if (totalBlocksInRadius > 0) {
                (blocksMined.toFloat() / totalBlocksInRadius.toFloat()) * 100f
            } else {
                blocksMined.toFloat()
            }
            onProgressUpdate(progress)
            currentTarget = null
            findNextTarget()
        }

        if (blocksMined >= totalBlocksInRadius && totalBlocksInRadius > 0) {
            complete()
        }

        if (blocksMined == 0 && currentTarget == null && searchCooldown <= 0) {
            complete()
        }
    }

    private fun findNextTarget() {
        val center = owner.blockPosition()
        val startX = center.x - radius
        val endX = center.x + radius
        val startZ = center.z - radius
        val endZ = center.z + radius
        val startY = max(center.y - 3, level.minBuildHeight)
        val endY = min(center.y + 3, level.maxBuildHeight)

        totalBlocksInRadius = 0

        outer@ for (y in startY..endY) {
            for (x in startX..endX) {
                for (z in startZ..endZ) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (isBlockValid(state, pos)) {
                        totalBlocksInRadius++
                        if (currentTarget == null) {
                            currentTarget = pos
                        }
                    }
                }
            }
        }

        if (currentTarget == null) {
            complete()
        }
    }

    private fun isBlockValid(state: BlockState, pos: BlockPos): Boolean {
        if (state.isAir) return false

        if (filter.isEmpty()) return true

        val blockName = BuiltInRegistries.BLOCK.getKey(state.block).toString()

        return when (filter) {
            "tree" -> blockName.contains("log") || blockName.contains("leaves")
            "enemy" -> false
            else -> blockName.contains(filter, ignoreCase = true)
        }
    }

    private fun isBlockValid(pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return !state.isAir
    }

    private fun moveToward(dest: Vec3) {
        val dir = dest.subtract(owner.position()).normalize()
        owner.setDeltaMovement(
            dir.x * 0.25,
            dir.y * 0.1,
            dir.z * 0.25
        )
        owner.hurtMarked = true
    }

    override fun onStop(cancelled: Boolean) {
        if (cancelled) {
            QLMZombieMod.LOGGER.debug("[MineTask] 挖矿任务已取消, 已挖掘 $blocksMined 个方块")
        }
    }

    fun getBlocksMined(): Int = blocksMined
}