package com.qlm.zombie.ai.task

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class FollowTask(
    owner: LivingEntity,
    private val targetName: String,
    private val safeDistance: Float = 3f
) : Task(owner) {

    private var targetEntity: Player? = null
    private var lostTargetTicks: Int = 0
    private var lastTargetPos: Vec3? = null
    private var pathRecoverCooldown: Int = 0

    init {
        timeoutTicks = 4000
    }

    override fun onStart() {
        findTarget()
    }

    private fun findTarget() {
        val server = owner.level().server
        if (server == null) {
            fail("无法访问服务器")
            return
        }

        val playerList = server.playerList
        targetEntity = if (targetName.isBlank()) {
            playerList.players.firstOrNull()
        } else {
            playerList.getPlayerByName(targetName)
        }

        if (targetEntity == null) {
            QLMZombieMod.LOGGER.warn("[FollowTask] 未找到目标玩家: $targetName")
        }
    }

    override fun doTick() {
        val target = targetEntity

        if (target == null) {
            lostTargetTicks++
            if (lostTargetTicks > 100) {
                pathRecoverCooldown--
                if (pathRecoverCooldown <= 0) {
                    findTarget()
                    pathRecoverCooldown = 100
                }
                if (lostTargetTicks > 200) {
                    fail("目标丢失超过200 ticks")
                }
            }
            return
        }

        lostTargetTicks = 0
        lastTargetPos = target.position()

        val ownerPos = owner.position()
        val targetPos = target.position()
        val distance = ownerPos.distanceTo(targetPos)

        if (distance <= safeDistance) {
            owner.setDeltaMovement(0.0, owner.deltaMovement.y, 0.0)
            return
        }

        if (distance > 40.0) {
            teleportTo(targetPos)
            return
        }

        val dir = targetPos.subtract(ownerPos)
        val horizontalDist = sqrt(dir.x * dir.x + dir.z * dir.z)

        if (horizontalDist > 0.5) {
            val moveDir = dir.normalize()
            val speed = 0.2
            owner.setDeltaMovement(
                moveDir.x * speed,
                owner.deltaMovement.y,
                moveDir.z * speed
            )
        }

        val yDiff = targetPos.y - ownerPos.y
        if (yDiff > 1.0 && owner.onGround()) {
            owner.setDeltaMovement(owner.deltaMovement.add(0.0, 0.5, 0.0))
        }

        val progress = (1.0f - (distance.toFloat() / 20.0f)) * 100f
        onProgressUpdate(progress.coerceIn(0f, 100f))
    }

    private fun teleportTo(pos: Vec3) {
        val level = owner.level()
        if (!level.isClientSide) {
            owner.teleportTo(pos.x, pos.y, pos.z)
            QLMZombieMod.LOGGER.debug("[FollowTask] 传送到目标位置: ${pos.x}, ${pos.y}, ${pos.z}")
        }
    }

    override fun onStop(cancelled: Boolean) {
        if (cancelled) {
            QLMZombieMod.LOGGER.debug("[FollowTask] 跟随任务已取消")
        }
        targetEntity = null
    }

    fun isTargetFound(): Boolean = targetEntity != null
    fun getTargetName(): String = targetEntity?.name?.string ?: targetName
}