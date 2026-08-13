package com.qlm.zombie.ai.task

import com.qlm.zombie.QLMZombieMod
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.Tags

abstract class Task(
    val owner: LivingEntity,
    val targetPos: Vec3? = null,
    val playerOwner: Player? = null
) {
    enum class TaskState {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    companion object {
        const val TARGET_SEARCH_TIMEOUT: Int = 200

        @JvmStatic
        fun isLogBlock(state: BlockState): Boolean {
            return state.`is`(BlockTags.LOGS)
        }

        @JvmStatic
        fun isOreBlock(state: BlockState): Boolean {
            return state.`is`(Tags.Blocks.ORES)
        }
    }

    // Java-compatible secondary constructor: Task(ai, owner)
    constructor(ai: LivingEntity, owner: Player) : this(ai, null, owner)

    var state: TaskState = TaskState.PENDING
        protected set

    var progress: Float = 0f
        protected set

    var timeoutTicks: Int = 600
    protected var ticksExecuted: Int = 0
    var failureReason: String = ""
        protected set

    @JvmField
    protected var noTargetTicks: Int = 0

    private var startTimeMs: Long = 0L

    open fun start() {
        state = TaskState.RUNNING
        ticksExecuted = 0
        startTimeMs = System.currentTimeMillis()
        progress = 0f
        onStart()
    }

    protected open fun onStart() {}

    open fun tick() {
        if (state != TaskState.RUNNING) return

        ticksExecuted++
        if (ticksExecuted >= timeoutTicks) {
            fail("任务超时 (${ticksExecuted} ticks)")
            return
        }

        try {
            doTick()
        } catch (e: Exception) {
            fail("任务异常: ${e.message}")
        }
    }

    protected abstract fun doTick()

    open fun stop() {
        if (state == TaskState.RUNNING) {
            state = TaskState.COMPLETED
            onStop(false)
        }
    }

    protected open fun onStop(cancelled: Boolean) {}

    protected fun complete() {
        state = TaskState.COMPLETED
        onStop(false)
    }

    protected fun fail(reason: String) {
        if (state == TaskState.RUNNING) {
            state = TaskState.FAILED
            failureReason = reason
            onStop(true)
        }
    }

    open fun isComplete(): Boolean = state == TaskState.COMPLETED
    open fun isFailed(): Boolean = state == TaskState.FAILED
    open fun isActive(): Boolean = state == TaskState.RUNNING

    open fun onProgressUpdate(newProgress: Float) {
        progress = newProgress.coerceIn(0f, 100f)
    }

    fun getBlockPos(): BlockPos? {
        return targetPos?.let { BlockPos(it.x.toInt(), it.y.toInt(), it.z.toInt()) }
    }

    // ---- Java-compatible helper methods ----

    open fun getName(): String = this.javaClass.simpleName.lowercase()

    open fun isActiveTask(): Boolean = true

    protected fun finish() {
        complete()
    }

    protected fun notifyOwner(message: String) {
        val player = playerOwner
        if (player is ServerPlayer) {
            player.sendSystemMessage(Component.literal(message))
        }
    }

    protected fun notifyOwnerSystem(message: String) {
        QLMZombieMod.LOGGER.info("[AI任务] {}", message)
        notifyOwner(message)
    }

    protected fun findNearestHostile(radius: Int): LivingEntity? {
        val level = owner.level()
        val aabb = AABB.ofSize(owner.position(), radius.toDouble(), radius.toDouble(), radius.toDouble())
        val monsters = level.getEntitiesOfClass(Monster::class.java, aabb) { it.isAlive }
        return monsters.minByOrNull { it.distanceToSqr(owner) }
    }

    protected fun resetNoTargetCounter() {
        noTargetTicks = 0
    }

    protected fun handleNoTarget(timeout: Int): Boolean {
        noTargetTicks++
        return noTargetTicks <= timeout
    }

    protected fun isTimedOut(): Boolean {
        return ticksExecuted >= timeoutTicks
    }

    protected fun getElapsedTime(): Long {
        return System.currentTimeMillis() - startTimeMs
    }
}
