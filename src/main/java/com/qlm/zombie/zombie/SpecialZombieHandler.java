package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 特殊僵尸系统（扩展版）：
 * - 烈焰僵尸：攻击附带火焰
 * - 剧毒僵尸：攻击附带中毒
 * - 铁甲僵尸：高护甲，慢速，坦克
 * - 跳跃僵尸：跳跃攻击
 * - 原版：召唤僵尸、木桶僵尸、巨人僵尸
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class SpecialZombieHandler {

    // NBT标记
    public static final String NBT_ZOMBIE_TYPE = "qlm_special_zombie_type";
    public static final String TYPE_GIANT = "giant";
    public static final String TYPE_BARREL = "barrel";
    public static final String TYPE_SUMMONER = "summoner";
    public static final String TYPE_FIRE = "fire";
    public static final String TYPE_POISON = "poison";
    public static final String TYPE_ARMORED = "armored";
    public static final String TYPE_LEAPER = "leaper";
    // 远程僵尸
    public static final String TYPE_THROWER = "thrower";   // 投掷僵尸 - 投掷石块
    public static final String TYPE_SPITTER = "spitter";   // 吐息僵尸 - 喷吐毒液
    public static final String TYPE_BOMBER = "bomber";     // 爆破僵尸 - 投掷炸弹
    public static final String TYPE_TNT_THROWER = "tnt_thrower"; // 投手僵尸 - 丢点燃TNT
    public static final String TYPE_SUICIDE = "suicide";   // 自爆僵尸 - 冲向玩家爆炸
    public static final String TYPE_ARCHER = "archer";     // 弓箭手僵尸 - 射箭

    // 召唤僵尸参数
    private static final double SUMMON_CHANCE = 0.3;
    private static final int SUMMON_COOLDOWN = 100;
    private static final Map<Integer, Long> lastSummonTime = new HashMap<>();

    @SubscribeEvent
    public static void onSpecialZombieTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            if (level.getGameTime() % 10 != 0) continue;

            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class,
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                z -> z.isAlive())) {

                CompoundTag tag = zombie.getPersistentData();
                String type = tag.getString(NBT_ZOMBIE_TYPE);
                if (type.isEmpty()) continue;

                long gameTime = level.getGameTime();

                switch (type) {
                    case TYPE_GIANT -> handleGiantZombie(zombie, level, gameTime);
                    case TYPE_BARREL -> handleBarrelZombie(zombie, level, tag, gameTime);
                    case TYPE_SUMMONER -> handleSummonerZombie(zombie, level, tag, gameTime);
                    case TYPE_FIRE -> handleFireZombie(zombie, level, tag, gameTime);
                    case TYPE_POISON -> handlePoisonZombie(zombie, level, tag, gameTime);
                    case TYPE_ARMORED -> handleArmoredZombie(zombie, level, tag, gameTime);
                    case TYPE_LEAPER -> handleLeaperZombie(zombie, level, tag, gameTime);
                    case TYPE_THROWER -> handleThrowerZombie(zombie, level, tag, gameTime);
                    case TYPE_SPITTER -> handleSpitterZombie(zombie, level, tag, gameTime);
                    case TYPE_BOMBER -> handleBomberZombie(zombie, level, tag, gameTime);
                    case TYPE_TNT_THROWER -> handleTntThrowerZombie(zombie, level, tag, gameTime);
                    case TYPE_SUICIDE -> handleSuicideZombie(zombie, level, tag, gameTime);
                    case TYPE_ARCHER -> handleArcherZombie(zombie, level, tag, gameTime);
                }
            }
        }
    }

    /** 巨人僵尸：每5秒范围震地；血量降至250时投掷小鬼僵尸 */
    private static void handleGiantZombie(Zombie zombie, ServerLevel level, long gameTime) {
        CompoundTag tag = zombie.getPersistentData();

        // 血量 <= 250：投掷小鬼僵尸（巨人共500血，半血即触发）
        if (zombie.getHealth() <= 250.0f && zombie.getTarget() != null) {
            long lastThrow = tag.getLong("qlm_giant_throw_time");
            if (gameTime - lastThrow >= 300) { // 每15秒投掷
                tag.putLong("qlm_giant_throw_time", gameTime);
                throwImpZombie(zombie, level);
            }
        }

        if (zombie.getTarget() == null) return;

        long lastSlam = tag.getLong("qlm_giant_slam_time");
        if (gameTime - lastSlam < 100) return;
        tag.putLong("qlm_giant_slam_time", gameTime);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
            zombie.getBoundingBox().inflate(4),
            e -> e.isAlive() && e != zombie && e.distanceToSqr(zombie) <= 16)) {

            if (target instanceof ServerPlayer) {
                target.hurt(target.damageSources().mobAttack(zombie), 8.0f);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
            zombie.getX(), zombie.getY(), zombie.getZ(), 10, 1.5, 0.5, 1.5, 0.1);
    }

    /** 巨人投掷小鬼僵尸：生成1-2只小鬼并向目标方向抛射 */
    private static void throwImpZombie(Zombie giant, ServerLevel level) {
        LivingEntity target = giant.getTarget();
        int count = 1 + giant.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            Zombie imp = EntityType.ZOMBIE.create(level);
            if (imp == null) continue;
            imp.setBaby(true);
            imp.setPos(giant.getX(), giant.getEyeY() - 0.3, giant.getZ());
            imp.setCustomName(Component.literal("§e[小鬼僵尸]"));
            imp.setCustomNameVisible(true);
            var speedAttr = imp.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(0.35);
            if (target != null) {
                imp.setTarget(target);
                // 向目标方向抛射
                double dx = target.getX() - giant.getX();
                double dy = target.getEyeY() - giant.getEyeY();
                double dz = target.getZ() - giant.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 0) {
                    imp.setDeltaMovement(dx / dist * 1.1, dy / dist * 1.1 + 0.4, dz / dist * 1.1);
                    imp.hurtMarked = true;
                }
            }
            level.addFreshEntity(imp);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                imp.getX(), imp.getY(), imp.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
        }
        if (target instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("§c§l⚠ 巨人僵尸向你投掷了小鬼僵尸！"));
        }
    }

    /** 木桶僵尸：半血丢出小鬼僵尸 */
    private static void handleBarrelZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        float healthPercent = zombie.getHealth() / zombie.getMaxHealth();
        if (healthPercent > 0.5f) return;

        long lastSpawn = tag.getLong("qlm_barrel_spawn_time");
        if (gameTime - lastSpawn < 200) return;
        tag.putLong("qlm_barrel_spawn_time", gameTime);

        int count = 1 + zombie.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            Zombie babyZombie = EntityType.ZOMBIE.create(level);
            if (babyZombie != null) {
                babyZombie.setPos(zombie.getX() + (zombie.getRandom().nextDouble() - 0.5) * 2,
                    zombie.getY(), zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5) * 2);
                babyZombie.setBaby(true);
                babyZombie.setCustomName(Component.literal("§e[小鬼僵尸]"));
                babyZombie.setCustomNameVisible(true);
                if (zombie.getTarget() != null) babyZombie.setTarget(zombie.getTarget());
                level.addFreshEntity(babyZombie);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    babyZombie.getX(), babyZombie.getY(), babyZombie.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
            }
        }

        if (!level.players().isEmpty()) {
            level.players().forEach(p -> {
                if (p.distanceToSqr(zombie) <= 400)
                    p.sendSystemMessage(Component.literal("§e§l⚠ 木桶僵尸丢出了小鬼僵尸！"));
            });
        }
    }

    /** 召唤僵尸：每5秒召唤普通僵尸 */
    private static void handleSummonerZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        int entityId = zombie.getId();
        Long lastTime = lastSummonTime.get(entityId);
        if (lastTime != null && gameTime - lastTime < SUMMON_COOLDOWN) return;
        lastSummonTime.put(entityId, gameTime);

        if (zombie.getRandom().nextDouble() > SUMMON_CHANCE) return;

        int count = 1 + zombie.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            Zombie summoned = EntityType.ZOMBIE.create(level);
            if (summoned != null) {
                double angle = zombie.getRandom().nextDouble() * 2 * Math.PI;
                double dist = 2 + zombie.getRandom().nextDouble() * 2;
                summoned.setPos(zombie.getX() + Math.cos(angle) * dist, zombie.getY(), zombie.getZ() + Math.sin(angle) * dist);
                summoned.getPersistentData().putBoolean("qlm_summoned", true);
                if (zombie.getTarget() != null) summoned.setTarget(zombie.getTarget());
                level.addFreshEntity(summoned);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    summoned.getX(), summoned.getY(), summoned.getZ(), 5, 0.2, 0.3, 0.2, 0.05);
            }
        }
    }

    /** 烈焰僵尸：攻击时点燃目标 */
    private static void handleFireZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;

        long lastFire = tag.getLong("qlm_fire_time");
        if (gameTime - lastFire < 40) return;
        tag.putLong("qlm_fire_time", gameTime);

        LivingEntity target = zombie.getTarget();
        if (target.distanceToSqr(zombie) <= 9) {
            target.setRemainingFireTicks(100); // 5秒火焰
            // 粒子特效
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                target.getX(), target.getY(), target.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** 剧毒僵尸：攻击时附加中毒效果 */
    private static void handlePoisonZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;

        long lastPoison = tag.getLong("qlm_poison_time");
        if (gameTime - lastPoison < 60) return;
        tag.putLong("qlm_poison_time", gameTime);

        LivingEntity target = zombie.getTarget();
        if (target.distanceToSqr(zombie) <= 9) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1)); // 中毒 II 5秒
            // 粒子特效
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                target.getX(), target.getY() + 1, target.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** 铁甲僵尸：高护甲，伤害减免 */
    private static void handleArmoredZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        // 铁甲僵尸自带伤害减免 (通过高护甲值已实现)
        // 主动索敌范围内的玩家
        if (zombie.getTarget() != null) {
            // 铁甲僵尸攻击附带击退
            long lastKnock = tag.getLong("qlm_armor_knock_time");
            if (gameTime - lastKnock < 40) return;
            tag.putLong("qlm_armor_knock_time", gameTime);

            LivingEntity target = zombie.getTarget();
            if (target.distanceToSqr(zombie) <= 9) {
                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.5, 0));
                target.hurtMarked = true;
            }
        }
    }

    /** 跳跃僵尸：跳跃攻击 */
    private static void handleLeaperZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;

        long lastLeap = tag.getLong("qlm_leap_time");
        if (gameTime - lastLeap < 60) return;
        tag.putLong("qlm_leap_time", gameTime);

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);

        // 距离3-8格时跳跃
        if (dist >= 9 && dist <= 64) {
            // 跳跃向目标
            double dx = target.getX() - zombie.getX();
            double dz = target.getZ() - zombie.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            zombie.setDeltaMovement(
                (dx / distance) * 1.2,
                0.5,
                (dz / distance) * 1.2
            );
            zombie.hurtMarked = true;

            // 跳跃粒子
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                zombie.getX(), zombie.getY(), zombie.getZ(), 5, 0.3, 0.1, 0.3, 0.05);
        }
    }

    // ====== 远程僵尸攻击 ======
    private static final Map<Integer, Long> lastRangedAttack = new HashMap<>();
    private static final int RANGED_COOLDOWN = 60; // 3秒

    /** 投掷僵尸：投掷雪球造成伤害 */
    private static void handleThrowerZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;
        int entityId = zombie.getId();
        Long lastTime = lastRangedAttack.get(entityId);
        if (lastTime != null && gameTime - lastTime < RANGED_COOLDOWN) return;
        lastRangedAttack.put(entityId, gameTime);

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);
        if (dist < 9 || dist > 100) return; // 3-10格

        // 投掷雪球（模拟石块）
        net.minecraft.world.entity.projectile.Snowball snowball = new net.minecraft.world.entity.projectile.Snowball(
            zombie.level(), zombie);
        double dx = target.getX() - zombie.getX();
        double dy = target.getEyeY() - zombie.getEyeY();
        double dz = target.getZ() - zombie.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        snowball.shoot(dx / distance * 1.5, dy / distance * 1.5, dz / distance * 1.5, 1.5f, 2.0f);
        snowball.setNoGravity(false);
        level.addFreshEntity(snowball);
        zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // 粒子特效
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
            zombie.getX(), zombie.getEyeY(), zombie.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
    }

    /** 吐息僵尸：喷吐毒液弹 */
    private static void handleSpitterZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;
        int entityId = zombie.getId();
        Long lastTime = lastRangedAttack.get(entityId);
        if (lastTime != null && gameTime - lastTime < RANGED_COOLDOWN) return;
        lastRangedAttack.put(entityId, gameTime);

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);
        if (dist < 9 || dist > 100) return;

        // 投掷鸡蛋模拟毒液弹
        net.minecraft.world.entity.projectile.ThrownEgg egg = new net.minecraft.world.entity.projectile.ThrownEgg(
            zombie.level(), zombie);
        double dx = target.getX() - zombie.getX();
        double dy = target.getEyeY() - zombie.getEyeY();
        double dz = target.getZ() - zombie.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        egg.shoot(dx / distance * 1.5, dy / distance * 1.5, dz / distance * 1.5, 1.5f, 3.0f);
        egg.setNoGravity(false);
        level.addFreshEntity(egg);
        zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // 绿色粒子
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
            zombie.getX(), zombie.getEyeY(), zombie.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
    }

    /** 爆破僵尸：投掷炸弹（火焰弹） */
    private static void handleBomberZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;
        int entityId = zombie.getId();
        Long lastTime = lastRangedAttack.get(entityId);
        if (lastTime != null && gameTime - lastTime < RANGED_COOLDOWN * 2) return; // 6秒冷却
        lastRangedAttack.put(entityId, gameTime);

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);
        if (dist < 16 || dist > 144) return; // 4-12格

        // 投掷火焰弹
        net.minecraft.world.entity.projectile.SmallFireball fireball = new net.minecraft.world.entity.projectile.SmallFireball(
            zombie.level(), zombie, target.getX() - zombie.getX(), target.getEyeY() - zombie.getEyeY(), target.getZ() - zombie.getZ());
        fireball.setPos(fireball.getX(), zombie.getEyeY(), fireball.getZ());
        level.addFreshEntity(fireball);
        zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // 火焰粒子
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
            zombie.getX(), zombie.getEyeY(), zombie.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
    }

    // ====== 投手僵尸：丢点燃TNT ======
    private static void handleTntThrowerZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;
        int entityId = zombie.getId();
        Long lastTime = lastRangedAttack.get(entityId);
        if (lastTime != null && gameTime - lastTime < RANGED_COOLDOWN * 3) return; // 9秒冷却
        lastRangedAttack.put(entityId, gameTime);

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);
        if (dist < 25 || dist > 169) return; // 5-13格

        // 生成点燃的TNT并投掷向玩家
        net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(
            zombie.level(), zombie.getX(), zombie.getEyeY(), zombie.getZ(), zombie);
        // 设置TNT fuse时间（4秒）
        tnt.setFuse(80);

        // 计算投掷方向
        double dx = target.getX() - zombie.getX();
        double dy = target.getEyeY() - zombie.getEyeY();
        double dz = target.getZ() - zombie.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        tnt.setDeltaMovement(
            dx / distance * 1.2,
            dy / distance * 1.2 + 0.3,
            dz / distance * 1.2
        );
        tnt.setNoGravity(false);
        level.addFreshEntity(tnt);
        zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // 警告
        if (target instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("§6⚠ 投手僵尸丢出了点燃的TNT！快躲开！").withStyle(net.minecraft.ChatFormatting.GOLD));
        }

        // 火焰粒子
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
            zombie.getX(), zombie.getEyeY(), zombie.getZ(), 10, 0.5, 0.3, 0.5, 0.1);
    }

    // ====== 自爆僵尸：冲向玩家爆炸 ======
    private static void handleSuicideZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);

        // 在5格内引爆
        if (dist <= 25) {
            // 爆炸前红色粒子警告
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                zombie.getX(), zombie.getY() + 1, zombie.getZ(), 5, 0.5, 0.5, 0.5, 0.1);

            // 立即爆炸
            level.explode(zombie, zombie.getX(), zombie.getY(), zombie.getZ(), 3.0f, true, Level.ExplosionInteraction.MOB);
            zombie.discard(); // 移除自爆僵尸

            if (target instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§4§l💥 自爆僵尸在你身边爆炸了！").withStyle(net.minecraft.ChatFormatting.DARK_RED));
            }
            return;
        }

        // 10格内加速冲向玩家
        if (dist <= 100) {
            double dx = target.getX() - zombie.getX();
            double dz = target.getZ() - zombie.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            zombie.setDeltaMovement(
                dx / d * 0.5,
                zombie.getDeltaMovement().y,
                dz / d * 0.5
            );
            zombie.hurtMarked = true;

            // 靠近时闪烁红色粒子
            if (dist <= 64) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                    zombie.getX(), zombie.getY() + 1, zombie.getZ(), 2, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    // ====== 弓箭手僵尸：射箭 ======
    private static final Map<Integer, Long> lastArcherShoot = new HashMap<>();
    private static final int ARCHER_COOLDOWN = 40; // 2秒

    private static void handleArcherZombie(Zombie zombie, ServerLevel level, CompoundTag tag, long gameTime) {
        if (zombie.getTarget() == null) return;
        int entityId = zombie.getId();
        Long lastTime = lastArcherShoot.get(entityId);
        if (lastTime != null && gameTime - lastTime < ARCHER_COOLDOWN) return;
        lastArcherShoot.put(entityId, gameTime);

        LivingEntity target = zombie.getTarget();
        double dist = zombie.distanceToSqr(target);
        if (dist < 16 || dist > 144) return; // 4-12格

        // 创建并射出箭矢
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
            zombie.level(), zombie);
        double dx = target.getX() - zombie.getX();
        double dy = target.getEyeY() - zombie.getEyeY();
        double dz = target.getZ() - zombie.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        arrow.shoot(dx / distance * 1.6, dy / distance * 1.6, dz / distance * 1.6, 1.6f, 3.0f);
        arrow.setNoGravity(false);
        level.addFreshEntity(arrow);
        zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // 粒子特效
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
            zombie.getX(), zombie.getEyeY(), zombie.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
    }

    /** 特殊僵尸死亡处理 */
    @SubscribeEvent
    public static void onSpecialZombieDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (zombie.level().isClientSide()) return;

        CompoundTag tag = zombie.getPersistentData();
        String type = tag.getString(NBT_ZOMBIE_TYPE);
        if (type.isEmpty()) return;

        if (TYPE_BARREL.equals(type)) {
            if (zombie.level() instanceof ServerLevel level) {
                // 木桶僵尸死亡：释放 3-4 只小鬼
                int count = 3 + zombie.getRandom().nextInt(2);
                for (int i = 0; i < count; i++) {
                    Zombie baby = EntityType.ZOMBIE.create(level);
                    if (baby != null) {
                        baby.setPos(zombie.getX() + (zombie.getRandom().nextDouble() - 0.5),
                            zombie.getY(), zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5));
                        baby.setBaby(true);
                        baby.setCustomName(Component.literal("§e[小鬼僵尸]"));
                        baby.setCustomNameVisible(true);
                        if (zombie.getTarget() != null) baby.setTarget(zombie.getTarget());
                        level.addFreshEntity(baby);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            baby.getX(), baby.getY(), baby.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
                    }
                }
                for (var p : level.players()) {
                    if (p.distanceToSqr(zombie) <= 400)
                        p.sendSystemMessage(Component.literal("§e§l⚠ 木桶僵尸被击杀，释放了 " + count + " 只小鬼僵尸！"));
                }
            }
        }
        if (TYPE_GIANT.equals(type) && zombie.level() instanceof ServerLevel level) {
            // 巨人僵尸死亡：释放 2 只小鬼
            for (int i = 0; i < 2; i++) {
                Zombie baby = EntityType.ZOMBIE.create(level);
                if (baby != null) {
                    baby.setPos(zombie.getX() + (zombie.getRandom().nextDouble() - 0.5),
                        zombie.getY(), zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5));
                    baby.setBaby(true);
                    baby.setCustomName(Component.literal("§e[小鬼僵尸]"));
                    baby.setCustomNameVisible(true);
                    level.addFreshEntity(baby);
                }
            }
        }

        // 烈焰僵尸死亡爆炸
        if (TYPE_FIRE.equals(type) && zombie.level() instanceof ServerLevel level) {
            for (int i = 0; i < 10; i++) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    zombie.getX() + (zombie.getRandom().nextDouble() - 0.5) * 2,
                    zombie.getY() + zombie.getRandom().nextDouble() * 2,
                    zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5) * 2,
                    1, 0, 0.1, 0, 0.05);
            }
        }

        // 剧毒僵尸死亡毒雾
        if (TYPE_POISON.equals(type) && zombie.level() instanceof ServerLevel level) {
            for (int i = 0; i < 10; i++) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                    zombie.getX() + (zombie.getRandom().nextDouble() - 0.5) * 2,
                    zombie.getY() + zombie.getRandom().nextDouble() * 2,
                    zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5) * 2,
                    1, 0, 0.1, 0, 0.05);
            }
        }
    }

    /** 僵尸生成时转换为特殊类型 */
    @SubscribeEvent
    public static void onZombieSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (zombie.level().isClientSide()) return;
        if (!(zombie.level() instanceof ServerLevel level)) return;

        if (zombie.getPersistentData().contains(NBT_ZOMBIE_TYPE)) return;
        if (zombie.getPersistentData().getBoolean("qlm_is_boss")) return;
        if (zombie.getPersistentData().getBoolean("qlm_summoned")) return;

        long day = level.getDayTime() / 24000L;
        double rand = zombie.getRandom().nextDouble();

        // 各类型概率
        double baseChance = 0.02;
        double multiplier = 1.0;
        if (day >= 25) multiplier = 1.5;
        if (day >= 50) multiplier = 2.0;
        if (day >= 100) multiplier = 3.0;

        double giantChance = baseChance * multiplier;
        double barrelChance = baseChance * 1.2 * multiplier;
        double summonerChance = baseChance * 1.2 * multiplier;
        double fireChance = baseChance * 1.5 * multiplier;
        double poisonChance = baseChance * 1.5 * multiplier;
        double armoredChance = baseChance * multiplier;
        double leaperChance = baseChance * 1.3 * multiplier;
        double throwerChance = baseChance * 1.2 * multiplier;
        double spitterChance = baseChance * 1.2 * multiplier;
        double bomberChance = baseChance * 0.8 * multiplier;
        double tntThrowerChance = baseChance * 0.8 * multiplier;
        double suicideChance = baseChance * 1.2 * multiplier;
        double archerChance = baseChance * 1.2 * multiplier;

        CompoundTag tag = zombie.getPersistentData();

        if (rand < giantChance) {
            applySpecialType(zombie, tag, TYPE_GIANT, "§c§l巨人僵尸", 500.0, 20.0, 10.0);
        } else if (rand < giantChance + barrelChance) {
            applySpecialType(zombie, tag, TYPE_BARREL, "§6木桶僵尸", 150.0, 10.0, 4.0);
        } else if (rand < giantChance + barrelChance + summonerChance) {
            applySpecialType(zombie, tag, TYPE_SUMMONER, "§5召唤僵尸", 80.0, 8.0, 2.0);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance) {
            applySpecialType(zombie, tag, TYPE_FIRE, "§c烈焰僵尸", 40.0, 12.0, 2.0);
            zombie.setRemainingFireTicks(99999); // 永久火焰外观
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance) {
            applySpecialType(zombie, tag, TYPE_POISON, "§a剧毒僵尸", 35.0, 8.0, 2.0);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance) {
            applySpecialType(zombie, tag, TYPE_ARMORED, "§7铁甲僵尸", 60.0, 12.0, 16.0);
            // 铁甲僵尸装备铁甲
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            zombie.setDropChance(EquipmentSlot.HEAD, 0.3f);
            zombie.setDropChance(EquipmentSlot.CHEST, 0.3f);
            zombie.setDropChance(EquipmentSlot.LEGS, 0.3f);
            zombie.setDropChance(EquipmentSlot.FEET, 0.3f);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance) {
            applySpecialType(zombie, tag, TYPE_LEAPER, "§b跳跃僵尸", 30.0, 6.0, 0.0);
            var jumpAttr = zombie.getAttribute(Attributes.JUMP_STRENGTH);
            if (jumpAttr != null) jumpAttr.setBaseValue(0.6);
        // ====== 远程僵尸 ======
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance + throwerChance) {
            applySpecialType(zombie, tag, TYPE_THROWER, "§e投掷僵尸", 35.0, 5.0, 2.0);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SNOWBALL, 16));
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance + throwerChance + spitterChance) {
            applySpecialType(zombie, tag, TYPE_SPITTER, "§a吐息僵尸", 30.0, 4.0, 1.0);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance + throwerChance + spitterChance + bomberChance) {
            applySpecialType(zombie, tag, TYPE_BOMBER, "§c爆破僵尸", 40.0, 6.0, 2.0);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance + throwerChance + spitterChance + bomberChance + tntThrowerChance) {
            applySpecialType(zombie, tag, TYPE_TNT_THROWER, "§6投手僵尸", 35.0, 4.0, 2.0);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance + throwerChance + spitterChance + bomberChance + tntThrowerChance + suicideChance) {
            applySpecialType(zombie, tag, TYPE_SUICIDE, "§4自爆僵尸", 20.0, 0.0, 0.0);
            // 自爆僵尸速度极快
            var speedAttr = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(0.35);
        } else if (rand < giantChance + barrelChance + summonerChance + fireChance + poisonChance + armoredChance + leaperChance + throwerChance + spitterChance + bomberChance + tntThrowerChance + suicideChance + archerChance) {
            applySpecialType(zombie, tag, TYPE_ARCHER, "§e弓箭手僵尸", 40.0, 8.0, 4.0);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
    }

    private static void applySpecialType(Zombie zombie, CompoundTag tag, String type, String name,
                                          double health, double damage, double armor) {
        tag.putString(NBT_ZOMBIE_TYPE, type);
        var healthAttr = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(health);
            zombie.setHealth((float) health);
        }
        var damageAttr = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(damage);
        var armorAttr = zombie.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(armor);
        zombie.setCustomName(Component.literal(name));
        zombie.setCustomNameVisible(true);
        zombie.setPersistenceRequired();
        QLMZombieMod.LOGGER.debug("[特殊僵尸] {} 生成于 {}", name, zombie.blockPosition());
    }

    /** 远程僵尸投射物击中处理 */
    @SubscribeEvent
    public static void onRangedZombieProjectile(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()) return;
        if (!(event.getProjectile().getOwner() instanceof Zombie zombie)) return;

        String type = zombie.getPersistentData().getString(NBT_ZOMBIE_TYPE);
        if (type.isEmpty()) return;

        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof LivingEntity target)) return;
        if (target == zombie) return;

        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.Snowball) {
            if (TYPE_THROWER.equals(type)) {
                // 投掷僵尸雪球命中：造成伤害
                target.hurt(target.damageSources().mobAttack(zombie), 8.0f);
                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.3, 0));
                target.hurtMarked = true;
                event.getProjectile().discard();
            }
        } else if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.ThrownEgg) {
            if (TYPE_SPITTER.equals(type)) {
                // 吐息僵尸毒液蛋命中：中毒+伤害
                target.hurt(target.damageSources().mobAttack(zombie), 6.0f);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                event.getProjectile().discard();
            }
        } else if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.SmallFireball) {
            if (TYPE_BOMBER.equals(type)) {
                // 爆破僵尸火焰弹命中：爆炸+火焰
                target.hurt(target.damageSources().mobAttack(zombie), 12.0f);
                target.setRemainingFireTicks(60);
                target.level().explode(target, target.getX(), target.getY(), target.getZ(), 1.5f, false, Level.ExplosionInteraction.NONE);
                event.getProjectile().discard();
            }
        }
    }
}