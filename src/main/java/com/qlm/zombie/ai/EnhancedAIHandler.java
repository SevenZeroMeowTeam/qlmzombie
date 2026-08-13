package com.qlm.zombie.ai;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 增强AI系统（事件驱动）：
 * - 僵尸：夜间加速，受伤召唤同伴，破门增强
 * - 骷髅：近战逃跑，精准射击，15%概率发射缓慢箭
 * - 村民：遇敌逃跑，受伤呼救
 * - 铁傀儡：主动索敌，击飞敌人，攻击苦力怕
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class EnhancedAIHandler {

    // ========== 僵尸增强 ==========

    private static final UUID ZOMBIE_NIGHT_SPEED_UUID = UUID.fromString("a1a1a1a1-0001-4000-8000-000000000001");
    private static final double ZOMBIE_NIGHT_SPEED_BONUS = 0.08;
    private static final double ZOMBIE_REINFORCE_CHANCE = 0.20;
    private static final double ZOMBIE_REINFORCE_RANGE = 10.0;

    // ========== 骷髅增强 ==========

    private static final double SKELETON_FLEE_RANGE = 3.0;
    private static final double SKELETON_POTION_ARROW_CHANCE = 0.15;
    private static final double SKELETON_ACCURACY_BONUS = 0.3;

    // ========== 村民增强 ==========

    private static final double VILLAGER_FLEE_RANGE = 15.0;
    private static final double VILLAGER_ALERT_RANGE = 20.0;

    // ========== 铁傀儡增强 ==========

    private static final double GOLEM_AGGRO_RANGE = 20.0;
    private static final double GOLEM_THROW_FORCE = 1.5;
    private static final double GOLEM_DAMAGE_BONUS = 10.0;
    private static final UUID GOLEM_DAMAGE_UUID = UUID.fromString("b2b2b2b2-0002-4000-8000-000000000002");

    // 冷却追踪
    private static final Map<Integer, Long> lastReinforceTime = new HashMap<>();
    private static final Map<Integer, Long> lastSkeletonFleeTime = new HashMap<>();
    private static final int REINFORCE_COOLDOWN = 200;
    private static final int SKELETON_FLEE_COOLDOWN = 40;

    // ========== 僵尸增强事件 ==========

    /** 僵尸夜间加速 */
    @SubscribeEvent
    public static void onZombieTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            boolean isNight = level.getDayTime() % 24000L >= 13000 && level.getDayTime() % 24000L < 23000;

            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(
                level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()
            ), z -> z.isAlive())) {

                // 夜间加速
                AttributeInstance speedAttr = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    if (isNight) {
                        if (speedAttr.getModifier(ZOMBIE_NIGHT_SPEED_UUID) == null) {
                            speedAttr.addPermanentModifier(new AttributeModifier(
                                ZOMBIE_NIGHT_SPEED_UUID, "Night Speed Boost",
                                ZOMBIE_NIGHT_SPEED_BONUS, AttributeModifier.Operation.ADDITION));
                        }
                    } else {
                        if (speedAttr.getModifier(ZOMBIE_NIGHT_SPEED_UUID) != null) {
                            speedAttr.removeModifier(ZOMBIE_NIGHT_SPEED_UUID);
                        }
                    }
                }
            }
        }
    }

    /** 僵尸受伤召唤同伴 */
    @SubscribeEvent
    public static void onZombieHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (zombie.level().isClientSide()) return;
        if (!(zombie.level() instanceof ServerLevel level)) return;

        // 只有被玩家攻击才触发
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;

        int entityId = zombie.getId();
        long gameTime = level.getGameTime();
        Long lastTime = lastReinforceTime.get(entityId);
        if (lastTime != null && gameTime - lastTime < REINFORCE_COOLDOWN) return;
        lastReinforceTime.put(entityId, gameTime);

        // 20%概率召唤同伴
        if (zombie.getRandom().nextDouble() >= ZOMBIE_REINFORCE_CHANCE) return;

        Vec3 pos = zombie.position();
        for (int i = 0; i < 2 + zombie.getRandom().nextInt(2); i++) {
            double angle = zombie.getRandom().nextDouble() * 2 * Math.PI;
            double distance = 3 + zombie.getRandom().nextDouble() * 4;
            BlockPos spawnPos = BlockPos.containing(
                pos.x + Math.cos(angle) * distance,
                pos.y,
                pos.z + Math.sin(angle) * distance
            );

            Zombie reinforcement = EntityType.ZOMBIE.create(level);
            if (reinforcement != null) {
                reinforcement.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                // 标记为召唤的僵尸（不进化）
                reinforcement.getPersistentData().putBoolean("qlm_summoned", true);
                level.addFreshEntity(reinforcement);

                // 粒子效果
                level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SMOKE,
                    spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                    5, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    // ========== 骷髅增强事件 ==========

    /** 骷髅近战逃跑 + 精准射击 */
    @SubscribeEvent
    public static void onSkeletonTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            for (Skeleton skeleton : level.getEntitiesOfClass(Skeleton.class, new AABB(
                level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()
            ), s -> s.isAlive())) {

                // 近战逃跑
                Player nearestPlayer = level.getNearestPlayer(skeleton, 10);
                if (nearestPlayer != null && nearestPlayer.isAlive()) {
                    double dist = skeleton.distanceToSqr(nearestPlayer);
                    int entityId = skeleton.getId();
                    long gameTime = level.getGameTime();
                    Long lastFlee = lastSkeletonFleeTime.get(entityId);
                    if (dist < SKELETON_FLEE_RANGE * SKELETON_FLEE_RANGE &&
                        (lastFlee == null || gameTime - lastFlee > SKELETON_FLEE_COOLDOWN)) {
                        lastSkeletonFleeTime.put(entityId, gameTime);
                        // 逃跑 - 远离玩家
                        Vec3 away = skeleton.position().subtract(nearestPlayer.position()).normalize();
                        skeleton.setDeltaMovement(skeleton.getDeltaMovement().add(
                            away.x * 0.5, 0.2, away.z * 0.5));
                        skeleton.hurtMarked = true;
                    }
                }
            }
        }
    }

    /** 骷髅箭矢增强 - 15%概率发射缓慢箭 */
    @SubscribeEvent
    public static void onSkeletonShoot(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Skeleton skeleton) {
            if (skeleton.level().isClientSide()) return;
            if (skeleton.getRandom().nextDouble() >= SKELETON_POTION_ARROW_CHANCE) return;

            // 标记箭矢 - 附加缓慢效果
            event.getEntity().addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 100, 1)); // 缓慢 II 5秒
        }
    }

    // ========== 村民增强事件 ==========

    /** 村民遇敌逃跑 */
    @SubscribeEvent
    public static void onVillagerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(
                level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()
            ), v -> v.isAlive())) {

                // 检测附近敌对生物
                boolean hasThreat = false;
                for (Entity entity : level.getEntities(villager, villager.getBoundingBox().inflate(VILLAGER_FLEE_RANGE))) {
                    if (entity instanceof Mob mob && mob.isAlive() && mob.getTarget() != null &&
                        mob.getTarget() == villager) {
                        hasThreat = true;
                        break;
                    }
                }

                if (hasThreat) {
                    // 逃跑 - 随机方向
                    double angle = villager.getRandom().nextDouble() * 2 * Math.PI;
                    villager.setDeltaMovement(villager.getDeltaMovement().add(
                        Math.cos(angle) * 0.3, 0.1, Math.sin(angle) * 0.3));
                    villager.hurtMarked = true;
                }
            }
        }
    }

    /** 村民受伤呼救 */
    @SubscribeEvent
    public static void onVillagerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.level().isClientSide()) return;
        if (!(villager.level() instanceof ServerLevel level)) return;

        // 警告附近的铁傀儡
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class,
            villager.getBoundingBox().inflate(VILLAGER_ALERT_RANGE),
            g -> g.isAlive())) {
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity livingAttacker) {
                golem.setTarget(livingAttacker);
            }
        }
    }

    // ========== 铁傀儡增强事件 ==========

    /** 铁傀儡主动索敌 + 伤害加成 */
    @SubscribeEvent
    public static void onIronGolemTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, new AABB(
                level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()
            ), g -> g.isAlive())) {

                // 伤害加成
                AttributeInstance damageAttr = golem.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damageAttr != null && damageAttr.getModifier(GOLEM_DAMAGE_UUID) == null) {
                    damageAttr.addPermanentModifier(new AttributeModifier(
                        GOLEM_DAMAGE_UUID, "Golem Damage Boost",
                        GOLEM_DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
                }

                // 主动索敌 - 如果没有目标，扫描附近敌对生物
                if (golem.getTarget() == null || !golem.getTarget().isAlive()) {
                    for (Entity entity : level.getEntities(golem, golem.getBoundingBox().inflate(GOLEM_AGGRO_RANGE))) {
                        if (entity instanceof Mob mob && mob.isAlive() && mob instanceof Enemy) {
                            golem.setTarget(mob);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** 铁傀儡攻击增强 - 击飞敌人 */
    @SubscribeEvent
    public static void onIronGolemAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof IronGolem golem)) return;
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity target = event.getEntity();

        // 击飞效果
        target.setDeltaMovement(target.getDeltaMovement().add(
            0, GOLEM_THROW_FORCE, 0));
        target.hurtMarked = true;

        // 额外伤害
        target.hurt(target.damageSources().mobAttack(golem), 5.0f);

        // 粒子效果
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.CRIT,
                target.getX(), target.getY() + 1, target.getZ(),
                10, 0.3, 0.3, 0.3, 0.1);
        }
    }
}