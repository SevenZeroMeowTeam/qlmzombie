package com.qlm.zombie.craftingdead.entity;

import com.qlm.zombie.craftingdead.item.CDItems;
import com.qlm.zombie.craftingdead.item.grenade.FlashbangGrenadeItem;
import com.qlm.zombie.craftingdead.item.grenade.FragmentGrenadeItem;
import com.qlm.zombie.craftingdead.item.grenade.MolotovCocktailItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Random;

public class ThrownGrenadeEntity extends ThrowableItemProjectile {

    public enum GrenadeType {
        FRAGMENT,
        FLASHBANG,
        MOLOTOV
    }

    private final GrenadeType grenadeType;

    public ThrownGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
        this.grenadeType = GrenadeType.FRAGMENT;
    }

    public ThrownGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level, GrenadeType grenadeType) {
        super(entityType, level);
        this.grenadeType = grenadeType;
    }

    public ThrownGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, LivingEntity shooter, Level level, GrenadeType grenadeType) {
        super(entityType, shooter, level);
        this.grenadeType = grenadeType;
    }

    @Override
    protected Item getDefaultItem() {
        switch (this.grenadeType) {
            case FRAGMENT:
                return CDItems.CD_FRAGMENT_GRENADE.get();
            case FLASHBANG:
                return CDItems.CD_FLASHBANG.get();
            case MOLOTOV:
                return CDItems.CD_MOLOTOV.get();
            default:
                return CDItems.CD_FRAGMENT_GRENADE.get();
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            switch (this.grenadeType) {
                case FRAGMENT:
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), 4.0F, Level.ExplosionInteraction.TNT);
                    break;
                case FLASHBANG:
                    AABB aabb = this.getBoundingBox().inflate(15.0D);
                    List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, aabb);
                    for (LivingEntity entity : entities) {
                        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    }
                    break;
                case MOLOTOV:
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), 2.5F, Level.ExplosionInteraction.TNT);
                    BlockPos centerPos = this.blockPosition();
                    Random random = new Random();
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            for (int y = -1; y <= 1; y++) {
                                if (random.nextFloat() < 0.4F) {
                                    BlockPos firePos = centerPos.offset(x, y, z);
                                    if (this.level().getBlockState(firePos).isAir() || this.level().getBlockState(firePos).canBeReplaced()) {
                                        this.level().setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                                    }
                                }
                            }
                        }
                    }
                    break;
            }
            this.discard();
        }
    }
}
