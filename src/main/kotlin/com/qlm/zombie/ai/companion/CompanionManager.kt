package com.qlm.zombie.ai.companion

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.ai.task.TaskRunner
import com.qlm.zombie.entity.FakePlayerEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CompanionManager {

    data class CompanionInfo(
        val uuid: UUID,
        var name: String,
        var affinity: Float = 0f,
        val taskRunner: TaskRunner,
        var entity: FakePlayerEntity? = null,
        var ownerId: UUID? = null
    ) {
        val isTamed: Boolean get() = affinity >= 100f
    }

    private val companions = ConcurrentHashMap<UUID, CompanionInfo>()

    companion object {
        private val instance = CompanionManager()

        @JvmStatic
        fun getInstance(): CompanionManager = instance
    }

    fun spawnCompanion(
        level: Level,
        owner: Player,
        name: String = "AI伴侣",
        pos: Vec3 = owner.position()
    ): CompanionInfo? {
        val fakePlayer = FakePlayerEntity(
            com.qlm.zombie.entity.QLMEntities.FAKE_PLAYER.get(),
            level
        )
        fakePlayer.setPos(pos.x, pos.y, pos.z)
        fakePlayer.customName = net.minecraft.network.chat.Component.literal(name)

        level.addFreshEntity(fakePlayer)

        val info = CompanionInfo(
            uuid = fakePlayer.uuid,
            name = name,
            taskRunner = TaskRunner(fakePlayer),
            entity = fakePlayer,
            ownerId = owner.uuid
        )

        info.taskRunner.onTaskComplete = { task ->
            QLMZombieMod.LOGGER.info("[Companion] ${info.name} 任务完成: ${task.javaClass.simpleName}")
        }
        info.taskRunner.onTaskFail = { task, reason ->
            QLMZombieMod.LOGGER.warn("[Companion] ${info.name} 任务失败: $reason")
        }

        companions[fakePlayer.uuid] = info

        QLMZombieMod.LOGGER.info("[Companion] 伴侣已生成: $name (UUID=${fakePlayer.uuid})")
        return info
    }

    fun despawnCompanion(uuid: UUID): Boolean {
        val info = companions.remove(uuid) ?: return false
        info.entity?.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
        QLMZombieMod.LOGGER.info("[Companion] 伴侣已移除: ${info.name}")
        return true
    }

    fun getCompanion(uuid: UUID): CompanionInfo? = companions[uuid]

    fun getCompanionsOf(owner: Player): List<CompanionInfo> {
        return companions.values.filter { it.ownerId == owner.uuid }
    }

    fun getByName(name: String): CompanionInfo? {
        return companions.values.firstOrNull { it.name == name }
    }

    fun getAll(): List<CompanionInfo> = companions.values.toList()

    fun tickAll() {
        val iterator = companions.entries.iterator()
        while (iterator.hasNext()) {
            val (_, info) = iterator.next()
            val entity = info.entity
            if (entity == null || !entity.isAlive || entity.level().isClientSide) {
                continue
            }
            info.taskRunner.tick()
        }
    }

    fun tame(uuid: UUID, amount: Float): Boolean {
        val info = companions[uuid] ?: return false
        val wasTamed = info.isTamed
        info.affinity = (info.affinity + amount).coerceIn(0f, 100f)
        if (!wasTamed && info.isTamed) {
            QLMZombieMod.LOGGER.info("[Companion] ${info.name} 已被驯服!")
        }
        return info.isTamed
    }

    fun setEquipment(uuid: UUID, slot: Int, itemStack: net.minecraft.world.item.ItemStack): Boolean {
        val info = companions[uuid] ?: return false
        val entity = info.entity ?: return false
        when (slot) {
            0 -> entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, itemStack)
            1 -> entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, itemStack)
            2 -> entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, itemStack)
            3 -> entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, itemStack)
            4 -> entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, itemStack)
            5 -> entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, itemStack)
        }
        return true
    }

    fun getStatus(uuid: UUID): Map<String, Any>? {
        val info = companions[uuid] ?: return null
        val entity = info.entity
        return mapOf(
            "name" to info.name,
            "affinity" to info.affinity,
            "tamed" to info.isTamed,
            "alive" to (entity?.isAlive ?: false),
            "task" to info.taskRunner.getStatus(),
            "position" to (entity?.position()?.let { mapOf("x" to it.x, "y" to it.y, "z" to it.z) } ?: emptyMap())
        )
    }

    fun killAll() {
        companions.values.forEach { info ->
            info.entity?.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
        }
        companions.clear()
        QLMZombieMod.LOGGER.info("[Companion] 所有伴侣已移除")
    }

    fun getOwnerOf(uuid: UUID): UUID? = companions[uuid]?.ownerId
}