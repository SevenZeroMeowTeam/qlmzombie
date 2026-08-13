package com.qlm.zombie.craftingdead.entity

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.effect.CDEffects
import net.minecraft.core.BlockPos
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableProjectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object CDEntities {
    private val ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, QLMZombieMod.MOD_ID)

    @JvmField
    val SOLDIER_ZOMBIE: RegistryObject<EntityType<SoldierZombieEntity>> = ENTITY_TYPES.register("soldier_zombie") {
        EntityType.Builder.of(::SoldierZombieEntity, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("soldier_zombie")
    }

    @JvmField
    val SCIENTIST_ZOMBIE: RegistryObject<EntityType<ScientistZombieEntity>> = ENTITY_TYPES.register("scientist_zombie") {
        EntityType.Builder.of(::ScientistZombieEntity, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("scientist_zombie")
    }

    @JvmField
    val CIVILIAN_ZOMBIE: RegistryObject<EntityType<CivilianZombieEntity>> = ENTITY_TYPES.register("civilian_zombie") {
        EntityType.Builder.of(::CivilianZombieEntity, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("civilian_zombie")
    }

    @JvmField
    val THROWN_GRENADE: RegistryObject<EntityType<ThrownGrenadeEntity>> = ENTITY_TYPES.register("thrown_grenade") {
        EntityType.Builder.of(::ThrownGrenadeEntity, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("thrown_grenade")
    }

    fun register(eventBus: IEventBus) {
        ENTITY_TYPES.register(eventBus)
    }

    fun registerAttributes(event: EntityAttributeCreationEvent) {
        event.put(SOLDIER_ZOMBIE.get(), SoldierZombieEntity.createAttributes().build())
        event.put(SCIENTIST_ZOMBIE.get(), ScientistZombieEntity.createAttributes().build())
        event.put(CIVILIAN_ZOMBIE.get(), CivilianZombieEntity.createAttributes().build())
    }
}

// ===== Soldier Zombie =====

class SoldierZombieEntity(type: EntityType<SoldierZombieEntity>, level: Level) : Zombie(type, level) {

    init {
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET))
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE))
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
        this.setItemSlot(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            val nearby = level().getEntitiesOfClass(LivingEntity::class.java, this.boundingBox.inflate(3.0))
            for (entity in nearby) {
                if (entity !== this && entity !is SoldierZombieEntity) {
                    entity.addEffect(MobEffectInstance(CDEffects.BLEEDING.get(), 40, 0, false, false, true))
                }
            }
        }
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder {
            return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 35.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 5.0)
                .add(Attributes.FOLLOW_RANGE, 35.0)
        }
    }
}

// ===== Scientist Zombie =====

class ScientistZombieEntity(type: EntityType<ScientistZombieEntity>, level: Level) : Zombie(type, level) {

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity
        val msgId = source.msgId
        if (attacker is LivingEntity && (msgId == "arrow" || msgId == "trident" || msgId == "fireworks" || msgId == "thrown")) {
            attacker.addEffect(MobEffectInstance(MobEffects.POISON, 60, 0))
        }
        return super.hurt(source, amount)
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder {
            return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 25.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
        }
    }
}

// ===== Civilian Zombie =====

class CivilianZombieEntity(type: EntityType<CivilianZombieEntity>, level: Level) : Zombie(type, level) {

    companion object {
        fun createAttributes(): AttributeSupplier.Builder {
            return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 15.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 25.0)
        }
    }
}
