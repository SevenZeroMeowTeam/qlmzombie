package com.qlm.zombie.ai.task

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.entity.LivingEntity
import java.util.PriorityQueue

class TaskRunner(val owner: LivingEntity) {

    private data class PrioritizedTask(
        val task: Task,
        val priority: Int,
        val id: Long
    ) : Comparable<PrioritizedTask> {
        override fun compareTo(other: PrioritizedTask): Int {
            val cmp = Integer.compare(other.priority, this.priority)
            return if (cmp != 0) cmp else java.lang.Long.compare(this.id, other.id)
        }
    }

    private val taskQueue = PriorityQueue<PrioritizedTask>()
    private var currentTask: Task? = null
    private var nextId: Long = 0

    var onTaskComplete: ((Task) -> Unit)? = null
    var onTaskFail: ((Task, String) -> Unit)? = null

    val isBusy: Boolean get() = currentTask?.isActive() == true
    val current: Task? get() = currentTask
    val queueSize: Int get() = taskQueue.size

    fun addTask(task: Task, priority: Int = 0) {
        val prioritized = PrioritizedTask(task, priority, nextId++)
        taskQueue.offer(prioritized)
        QLMZombieMod.LOGGER.debug("[TaskRunner] 任务已加入队列: ${task.javaClass.simpleName} (优先级=$priority)")
    }

    fun tick() {
        val current = currentTask

        if (current != null) {
            current.tick()

            when {
                current.isComplete() -> {
                    QLMZombieMod.LOGGER.debug("[TaskRunner] 任务完成: ${current.javaClass.simpleName}")
                    onTaskComplete?.invoke(current)
                    this.currentTask = null
                    pollNext()
                }
                current.isFailed() -> {
                    val reason = current.failureReason
                    QLMZombieMod.LOGGER.warn("[TaskRunner] 任务失败: ${current.javaClass.simpleName} - $reason")
                    onTaskFail?.invoke(current, reason)
                    this.currentTask = null
                    pollNext()
                }
            }
        } else {
            pollNext()
        }
    }

    private fun pollNext() {
        val next = taskQueue.poll()
        if (next != null) {
            currentTask = next.task
            currentTask!!.start()
            QLMZombieMod.LOGGER.debug("[TaskRunner] 开始执行: ${currentTask!!.javaClass.simpleName}")
        }
    }

    fun hasActiveTask(): Boolean = isBusy

    fun getCurrentTaskName(): String? = currentTask?.getName()

    fun clearQueue() {
        taskQueue.clear()
        currentTask?.stop()
        currentTask = null
    }

    fun interruptCurrent() {
        currentTask?.let { task ->
            task.stop()
            QLMZombieMod.LOGGER.debug("[TaskRunner] 任务被中断: ${task.javaClass.simpleName}")
        }
        currentTask = null
        pollNext()
    }

    fun getStatus(): Map<String, Any> {
        val task = currentTask
        return mapOf(
            "busy" to isBusy,
            "currentTask" to (task?.javaClass?.simpleName ?: "none"),
            "progress" to (task?.progress ?: 0f),
            "state" to (task?.state?.name ?: "IDLE"),
            "queueSize" to queueSize
        )
    }
}