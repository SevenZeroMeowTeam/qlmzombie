package com.qlm.zombie.entity

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.level.Level

class GiantZombieEntity(type: EntityType<GiantZombieEntity>, level: Level) : Zombie(type, level) {

    companion object {
        fun createAttributes(): AttributeSupplier.Builder {
            return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ARMOR, 5.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
        }
    }
}
