package com.qlm.zombie.entity;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class QLMEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, QLMZombieMod.MOD_ID);

    public static final RegistryObject<EntityType<FakePlayerEntity>> FAKE_PLAYER =
            ENTITY_TYPES.register("fake_player",
                    () -> EntityType.Builder.of(FakePlayerEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build("fake_player"));
}