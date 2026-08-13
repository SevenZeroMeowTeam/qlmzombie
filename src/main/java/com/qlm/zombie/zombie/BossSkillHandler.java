package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Boss技能处理器（含粒子特效）：
 * - 小Boss：范围震地（粒子爆炸+烟尘，对周围玩家造成伤害+击退+缓慢）
 * - 大Boss阶段技能：阶段2召唤小Boss，阶段3狂暴+召唤（粒子升级）
 * - Boss死亡时粒子爆炸特效
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class BossSkillHandler {

    // 小Boss技能参数
    private static final double MINI_BOSS_AOE_RANGE = 5.0;
    private static final float MINI_BOSS_AOE_DAMAGE = 15.0f;
    private static final double MINI_BOSS_KNOCKBACK = 2.0;
    private static final int MINI_BOSS_SLOWNESS_DURATION = 100;
    private static final int MINI_BOSS_SLOWNESS_LEVEL = 1;

    // 大Boss技能参数
    private static final double BIG_BOSS_AOE_RANGE = 8.0;
    private static final float BIG_BOSS_AOE_DAMAGE = 30.0f;
    private static final int BIG_BOSS_WEAKNESS_DURATION = 200;
    private static final int BIG_BOSS_WEAKNESS_LEVEL = 1;

    // 技能冷却（tick）
    private static final int MINI_BOSS_SKILL_COOLDOWN = 100;
    private static final int BIG_BOSS_SKILL_COOLDOWN = 80;

    /** 小Boss被攻击时概率触发范围震地 */
    @SubscribeEvent
    public static void onBossHurt(LivingHurtEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) return;
        if (entity.level().isClientSide()) return;

        CompoundTag tag = zombie.getPersistentData();
        if (!tag.getBoolean(ZombieHordeHandler.NBT_IS_BOSS)) return;
        String bossType = tag.getString(ZombieHordeHandler.NBT_BOSS_TYPE);

        ServerLevel level = (ServerLevel) zombie.level();

        if ("mini".equals(bossType)) {
            triggerMiniBossSkill(zombie, level);
        } else if ("big".equals(bossType)) {
            triggerBigBossSkill(zombie, level, tag);
        }
    }

    /** 小Boss技能：范围震地（含粒子特效） */
    private static void triggerMiniBossSkill(Zombie boss, ServerLevel level) {
        long gameTime = level.getGameTime();
        CompoundTag tag = boss.getPersistentData();
        long lastSkill = tag.getLong("qlm_last_skill_time");
        if (gameTime - lastSkill < MINI_BOSS_SKILL_COOLDOWN) return;
        tag.putLong("qlm_last_skill_time", gameTime);

        if (boss.getRandom().nextDouble() > 0.3) return;

        Vec3 bossPos = boss.position();

        // ========== 粒子特效：震地爆炸 ==========
        // 1. 大爆炸粒子（中心）
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            bossPos.x, bossPos.y + 0.5, bossPos.z,
            1, 0, 0, 0, 0);

        // 2. 烟尘粒子（环形扩散）
        for (int i = 0; i < 30; i++) {
            double angle = boss.getRandom().nextDouble() * 2 * Math.PI;
            double radius = 1.0 + boss.getRandom().nextDouble() * 3.0;
            double px = bossPos.x + Math.cos(angle) * radius;
            double pz = bossPos.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                px, bossPos.y + 0.2, pz,
                1, 0, 0.1, 0, 0.05);
        }

        // 3. 火焰粒子（震地裂纹感）
        for (int i = 0; i < 15; i++) {
            level.sendParticles(ParticleTypes.FLAME,
                bossPos.x + (boss.getRandom().nextDouble() - 0.5) * 4,
                bossPos.y + 0.1,
                bossPos.z + (boss.getRandom().nextDouble() - 0.5) * 4,
                1, 0, 0.05, 0, 0.02);
        }

        // 4. 石块粒子（飞溅）
        for (int i = 0; i < 20; i++) {
            level.sendParticles(ParticleTypes.CRIT,
                bossPos.x + (boss.getRandom().nextDouble() - 0.5) * 5,
                bossPos.y + 0.5,
                bossPos.z + (boss.getRandom().nextDouble() - 0.5) * 5,
                1, (boss.getRandom().nextDouble() - 0.5) * 0.5, 0.3, (boss.getRandom().nextDouble() - 0.5) * 0.5, 0.1);
        }

        // 5. 音效
        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.8f + boss.getRandom().nextFloat() * 0.4f);

        // ========== 伤害效果 ==========
        AABB aabb = boss.getBoundingBox().inflate(MINI_BOSS_AOE_RANGE);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
            ServerPlayer.class, aabb,
            p -> p.isAlive() && p.distanceToSqr(boss) <= MINI_BOSS_AOE_RANGE * MINI_BOSS_AOE_RANGE
        );

        for (ServerPlayer player : nearbyPlayers) {
            player.hurt(player.damageSources().mobAttack(boss), MINI_BOSS_AOE_DAMAGE);
            Vec3 knockback = player.position().subtract(boss.position()).normalize()
                .scale(MINI_BOSS_KNOCKBACK).add(0, 0.5, 0);
            player.setDeltaMovement(player.getDeltaMovement().add(knockback));
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                MINI_BOSS_SLOWNESS_DURATION, MINI_BOSS_SLOWNESS_LEVEL));
            player.sendSystemMessage(Component.literal("§c§l⚡ 小Boss释放了震地！"));
        }

        if (!nearbyPlayers.isEmpty()) {
            QLMZombieMod.LOGGER.debug("[Boss技能] 小Boss 震地(粒子特效) 击中 {} 个玩家", nearbyPlayers.size());
        }
    }

    /** 大Boss技能：范围震地+虚弱（含粒子特效） */
    private static void triggerBigBossSkill(Zombie boss, ServerLevel level, CompoundTag tag) {
        long gameTime = level.getGameTime();
        long lastSkill = tag.getLong("qlm_last_skill_time");
        if (gameTime - lastSkill < BIG_BOSS_SKILL_COOLDOWN) return;
        tag.putLong("qlm_last_skill_time", gameTime);

        if (boss.getRandom().nextDouble() > 0.4) return;

        int phase = tag.getInt(ZombieHordeHandler.NBT_BOSS_PHASE);
        float damage = BIG_BOSS_AOE_DAMAGE;
        double range = BIG_BOSS_AOE_RANGE;

        if (phase >= 2) {
            damage *= 1.5f;
            range *= 1.2;
        }
        if (phase >= 3) {
            damage *= 2.0f;
            range *= 1.5;
        }

        Vec3 bossPos = boss.position();

        // ========== 粒子特效（不同阶段不同效果） ==========
        // 1. 核心爆炸
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            bossPos.x, bossPos.y + 1, bossPos.z,
            1, 0, 0, 0, 0);

        // 2. 阶段特定粒子
        int particleCount = 40 + phase * 20;
        double maxRadius = 2.0 + phase * 1.5;

        // 烟尘环
        for (int i = 0; i < particleCount; i++) {
            double angle = boss.getRandom().nextDouble() * 2 * Math.PI;
            double radius = 1.0 + boss.getRandom().nextDouble() * maxRadius;
            double px = bossPos.x + Math.cos(angle) * radius;
            double pz = bossPos.z + Math.sin(angle) * radius;

            if (phase >= 3) {
                // 阶段3：火焰+熔岩+灵魂火
                level.sendParticles(ParticleTypes.FLAME,
                    px, bossPos.y + 0.5, pz, 1, 0, 0.2, 0, 0.05);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    px, bossPos.y + 1.0, pz, 1, 0, 0.1, 0, 0.03);
                level.sendParticles(ParticleTypes.LAVA,
                    px, bossPos.y + 0.1, pz, 1, 0, 0.05, 0, 0.02);
            } else if (phase >= 2) {
                // 阶段2：火焰+烟尘
                level.sendParticles(ParticleTypes.FLAME,
                    px, bossPos.y + 0.3, pz, 1, 0, 0.1, 0, 0.03);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    px, bossPos.y + 0.2, pz, 1, 0, 0.1, 0, 0.05);
            } else {
                // 阶段1：大烟尘
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    px, bossPos.y + 0.2, pz, 1, 0, 0.1, 0, 0.05);
            }
        }

        // 3. 石块飞溅
        for (int i = 0; i < 15 + phase * 10; i++) {
            level.sendParticles(ParticleTypes.CRIT,
                bossPos.x + (boss.getRandom().nextDouble() - 0.5) * range,
                bossPos.y + 0.5 + boss.getRandom().nextDouble() * 0.5,
                bossPos.z + (boss.getRandom().nextDouble() - 0.5) * range,
                1, (boss.getRandom().nextDouble() - 0.5) * 0.8, 0.5, (boss.getRandom().nextDouble() - 0.5) * 0.8, 0.2);
        }

        // 4. 音效
        float pitch = 0.6f - phase * 0.1f;
        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, pitch);
        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
            SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.5f, 0.5f);

        // ========== 伤害效果 ==========
        final double finalRange = range;
        AABB aabb = boss.getBoundingBox().inflate(finalRange);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
            ServerPlayer.class, aabb,
            p -> p.isAlive() && p.distanceToSqr(boss) <= finalRange * finalRange
        );

        for (ServerPlayer player : nearbyPlayers) {
            player.hurt(player.damageSources().mobAttack(boss), damage);
            Vec3 knockback = player.position().subtract(boss.position()).normalize()
                .scale(3.0).add(0, 0.8, 0);
            player.setDeltaMovement(player.getDeltaMovement().add(knockback));
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                BIG_BOSS_WEAKNESS_DURATION, BIG_BOSS_WEAKNESS_LEVEL));

            String phaseMsg = switch (phase) {
                case 2 -> "§c§l⚡ 大Boss释放了强力震地！";
                case 3 -> "§4§l⚡ 大Boss释放了狂暴震地！";
                default -> "§c§l⚡ 大Boss释放了震地！";
            };
            player.sendSystemMessage(Component.literal(phaseMsg));
        }

        if (!nearbyPlayers.isEmpty()) {
            QLMZombieMod.LOGGER.debug("[Boss技能] 大Boss 震地(阶段{}) 击中 {} 个玩家", phase, nearbyPlayers.size());
        }
    }

    /** Boss死亡时粒子爆炸特效 */
    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) return;
        if (entity.level().isClientSide()) return;

        CompoundTag tag = zombie.getPersistentData();
        if (!tag.getBoolean(ZombieHordeHandler.NBT_IS_BOSS)) return;

        String bossType = tag.getString(ZombieHordeHandler.NBT_BOSS_TYPE);
        ServerLevel level = (ServerLevel) zombie.level();
        Vec3 pos = zombie.position();

        // ========== Boss死亡粒子特效 ==========
        // 大爆炸
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);

        if ("big".equals(bossType)) {
            // 大Boss死亡：大量粒子
            for (int i = 0; i < 50; i++) {
                double angle = zombie.getRandom().nextDouble() * 2 * Math.PI;
                double radius = zombie.getRandom().nextDouble() * 5;
                double px = pos.x + Math.cos(angle) * radius;
                double pz = pos.z + Math.sin(angle) * radius;

                level.sendParticles(ParticleTypes.FLAME,
                    px, pos.y + 0.5 + zombie.getRandom().nextDouble() * 2, pz,
                    1, 0, 0.1, 0, 0.05);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    px, pos.y + 1 + zombie.getRandom().nextDouble() * 2, pz,
                    1, 0, 0.1, 0, 0.03);
                level.sendParticles(ParticleTypes.LAVA,
                    px, pos.y + 0.1, pz, 1, 0, 0.05, 0, 0.02);
            }
            level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);
        } else {
            // 小Boss死亡：中等粒子
            for (int i = 0; i < 20; i++) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x + (zombie.getRandom().nextDouble() - 0.5) * 4,
                    pos.y + 0.5 + zombie.getRandom().nextDouble() * 1.5,
                    pos.z + (zombie.getRandom().nextDouble() - 0.5) * 4,
                    1, 0, 0.1, 0, 0.05);
            }
            level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.7f);
        }

        // ========== 通知消息 ==========
        Component msg;
        if ("mini".equals(bossType)) {
            msg = Component.literal("§a§l✔ 小Boss 已被击杀！");
        } else if ("big".equals(bossType)) {
            msg = Component.literal("§6§l✔✔✔ 大Boss 已被击杀！恭喜！");
        } else {
            return;
        }

        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(msg);
        }

        QLMZombieMod.LOGGER.info("[Boss技能] {} 已被击杀", bossType.equals("mini") ? "小Boss" : "大Boss");
    }
}