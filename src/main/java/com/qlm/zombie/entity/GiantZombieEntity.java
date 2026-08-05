package com.qlm.zombie.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class GiantZombieEntity extends Zombie {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SPAWNED_CHILDREN_TAG = "qlmzombie.giant_spawned_children";
    private boolean hasSpawnedChildren = false;
    private int roarCooldown = 0;

    public GiantZombieEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setCustomName(net.minecraft.network.chat.Component.literal("巨人僵尸"));
        this.setCustomNameVisible(true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BreakDoorGoal(this, (difficulty) -> true));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.6D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 25.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                          MobSpawnType spawnType, @Nullable SpawnGroupData groupData,
                                          @Nullable net.minecraft.nbt.CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        this.setHealth(this.getMaxHealth());
        this.hasSpawnedChildren = tag != null && tag.getBoolean(SPAWNED_CHILDREN_TAG);
        return result;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (roarCooldown > 0) {
                roarCooldown--;
            }

            checkSpawnChildren();
        }
    }

    private void checkSpawnChildren() {
        if (hasSpawnedChildren) return;

        float healthPercent = this.getHealth() / this.getMaxHealth();
        if (healthPercent <= 0.5F) {
            spawnZombieChildren();
            hasSpawnedChildren = true;
        }
    }

    private void spawnZombieChildren() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        LOGGER.info("[QLM Zombie] 巨人僵尸血量低于50%，开始投掷小鬼僵尸！");

        this.playSound(SoundEvents.ZOMBIE_AMBIENT, 2.0F, 0.5F);

        for (int i = 0; i < 4; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(serverLevel);
            if (zombie == null) continue;

            Vec3 throwDirection = new Vec3(
                    (this.random.nextDouble() - 0.5) * 2.0,
                    1.5 + this.random.nextDouble() * 0.5,
                    (this.random.nextDouble() - 0.5) * 2.0
            ).normalize();

            Vec3 spawnPos = this.position().add(throwDirection.scale(2.0));

            zombie.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, this.random.nextFloat() * 360.0F, 0.0F);
            zombie.setBaby(true);
            zombie.getPersistentData().putBoolean("qlm_horde_monster", true);

            serverLevel.addFreshEntity(zombie);

            zombie.setDeltaMovement(throwDirection.scale(1.5));

            for (int j = 0; j < 10; j++) {
                serverLevel.sendParticles(
                        ParticleTypes.POOF,
                        spawnPos.x, spawnPos.y, spawnPos.z,
                        1, 0, 0, 0, 0.1
                );
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);

        if (result && !this.level().isClientSide && roarCooldown <= 0) {
            float healthPercent = this.getHealth() / this.getMaxHealth();
            if (healthPercent <= 0.75F && healthPercent > 0.5F) {
                roar();
            } else if (healthPercent <= 0.25F) {
                roar();
            }
        }

        return result;
    }

    private void roar() {
        this.playSound(SoundEvents.ZOMBIE_AMBIENT, 3.0F, 0.3F);
        roarCooldown = 60;

        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(20.0))) {
            if (entity instanceof Player player) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§c巨人僵尸发出了震耳欲聋的咆哮！")
                );
            }
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(SPAWNED_CHILDREN_TAG, hasSpawnedChildren);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hasSpawnedChildren = tag.getBoolean(SPAWNED_CHILDREN_TAG);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 4.0F;
    }
}
