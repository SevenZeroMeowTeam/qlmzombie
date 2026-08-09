package com.qlm.zombie.craftingdead.entity;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.craftingdead.entity.zombie.CivilianZombie;
import com.qlm.zombie.craftingdead.entity.zombie.ScientistZombie;
import com.qlm.zombie.craftingdead.entity.zombie.SoldierZombie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CDEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, QLMZombieMod.MOD_ID);

    // ==================== 投掷物实体 ====================
    public static final RegistryObject<EntityType<ThrownGrenadeEntity>> THROWN_GRENADE = ENTITY_TYPES.register("cd_thrown_grenade",
            () -> EntityType.Builder.<ThrownGrenadeEntity>of(ThrownGrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("cd_thrown_grenade"));

    // ==================== 僵尸变种实体 ====================
    public static final RegistryObject<EntityType<SoldierZombie>> SOLDIER_ZOMBIE = ENTITY_TYPES.register("cd_soldier_zombie",
            () -> EntityType.Builder.of(SoldierZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("cd_soldier_zombie"));

    public static final RegistryObject<EntityType<ScientistZombie>> SCIENTIST_ZOMBIE = ENTITY_TYPES.register("cd_scientist_zombie",
            () -> EntityType.Builder.of(ScientistZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("cd_scientist_zombie"));

    public static final RegistryObject<EntityType<CivilianZombie>> CIVILIAN_ZOMBIE = ENTITY_TYPES.register("cd_civilian_zombie",
            () -> EntityType.Builder.of(CivilianZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("cd_civilian_zombie"));
}
