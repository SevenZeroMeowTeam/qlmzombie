package com.qlm.zombie.cloudai.util;

import com.qlm.zombie.cloudai.core.CloudAiConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * AI 战斗辅助工具
 * - 距离判断
 * - 冷却计算
 * - 最近敌对搜索
 * - 速度倍率
 */
public final class AiAttackHelper {

    private AiAttackHelper() {}

    /** 判断两个实体是否在攻击范围内（近战 3 格） */
    public static boolean isWithinMeleeRange(Entity attacker, Entity target) {
        if (attacker == null || target == null) return false;
        double distSq = attacker.distanceToSqr(target);
        // 近战判定距离（考虑碰撞箱），3.5 格平方 = 12.25
        return distSq <= 12.25D;
    }

    /** 判断是否达到攻击冷却（tick） */
    public static boolean isAttackCooldownReady(int lastAttackTick, int currentTick) {
        return (currentTick - lastAttackTick) >= CloudAiConstants.AI_ATTACK_COOLDOWN_TICK;
    }

    /** 计算下次可攻击 tick */
    public static int nextAttackTick(int currentTick) {
        return currentTick + CloudAiConstants.AI_ATTACK_COOLDOWN_TICK;
    }

    /** 搜索最近敌对生物（带半径限制） */
    public static LivingEntity findNearestHostile(Level level, Entity center, double radius) {
        return EnvScan.nearestHostile(level, center, radius);
    }

    /** 获取 AI 移动速度倍率（默认 1.2x） */
    public static float getSpeedMultiplier() {
        return CloudAiConstants.AI_SPEED_MULTIPLIER;
    }

    /** 获取 AI 移动速度倍率（带难度修正） */
    public static float getSpeedMultiplier(float difficultyFactor) {
        return CloudAiConstants.AI_SPEED_MULTIPLIER * difficultyFactor;
    }
}
