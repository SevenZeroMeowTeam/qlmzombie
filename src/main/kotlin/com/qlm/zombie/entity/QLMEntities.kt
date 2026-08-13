package com.qlm.zombie.entity

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object QLMEntities {
    private val ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, QLMZombieMod.MOD_ID)

    @JvmField
    val FAKE_PLAYER: RegistryObject<EntityType<FakePlayerEntity>> = ENTITY_TYPES.register("fake_player") {
        EntityType.Builder.of(::FakePlayerEntity, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("fake_player")
    }

    @JvmField
    val GIANT_ZOMBIE: RegistryObject<EntityType<GiantZombieEntity>> = ENTITY_TYPES.register("giant_zombie") {
        EntityType.Builder.of(::GiantZombieEntity, MobCategory.MONSTER)
            .sized(2.4F, 5.6F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("giant_zombie")
    }

    fun register(eventBus: IEventBus) {
        ENTITY_TYPES.register(eventBus)
    }

    fun registerAttributes(event: EntityAttributeCreationEvent) {
        event.put(FAKE_PLAYER.get(), FakePlayerEntity.createAttributes().build())
        event.put(GIANT_ZOMBIE.get(), GiantZombieEntity.createAttributes().build())
    }
}
