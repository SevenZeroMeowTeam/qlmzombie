/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * RewardFunction — 奖励函数
 * 根据动作执行前后的环境变化计算即时奖励
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.qlearning;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 奖励函数
 *
 * 奖励设计:
 *   ATTACK:
 *     + 对敌人造成伤害 / 击杀 → +
 *     + 自身受伤 → -
 *   FLEE:
 *     + 远离敌人 → +
 *     + 被困 / 撞墙 → -
 *   EAT:
 *     + 饥饿时进食成功 → +
 *     + 满腹进食 → -
 *   FOLLOW:
 *     + 与主人距离合适 → +
 *     + 走丢 → -
 *   EXPLORE:
 *     + 发现新区域 → +
 *     + 无收获 → 小负值
 *   IDLE:
 *     + 危险时待命 → -
 *     + 安全时待命 → 微正
 */
public class RewardFunction {

    public static double computeReward(QLearningState before, QLearningState after,
                                       QLearningAction action, FakePlayerEntity ai,
                                       ActionContext ctx) {
        double reward = 0.0;

        switch (action) {
            case ATTACK -> {
                if (ctx.enemyKilled) reward += 5.0;
                if (ctx.dealtDamage > 0) reward += ctx.dealtDamage * 0.1;
                if (ctx.tookDamage > 0) reward -= ctx.tookDamage * 0.2;
                if (after.enemy == QLearningState.EnemyDistance.NONE
                        && before.enemy != QLearningState.EnemyDistance.NONE) {
                    reward += 2.0;
                }
            }
            case FLEE -> {
                if (before.enemy != QLearningState.EnemyDistance.NONE
                        && after.enemy == QLearningState.EnemyDistance.NONE) {
                    reward += 4.0;
                }
                if (after.health.ordinal() > before.health.ordinal()) {
                    reward += 1.0;
                }
                if (ctx.tookDamage > 0) reward -= ctx.tookDamage * 0.3;
                reward -= 0.05; // 逃跑代价
            }
            case EAT -> {
                if (before.hunger != QLearningState.HungerLevel.FULL
                        && after.hunger.ordinal() < before.hunger.ordinal()) {
                    reward += 3.0;
                } else if (before.hunger == QLearningState.HungerLevel.FULL) {
                    reward -= 1.0; // 浪费食物
                }
            }
            case FOLLOW -> {
                if (after.owner == QLearningState.OwnerDistance.CLOSE) {
                    reward += 1.0;
                } else if (after.owner == QLearningState.OwnerDistance.OUT_OF_SIGHT) {
                    reward -= 2.0;
                }
            }
            case EXPLORE -> {
                if (ctx.newChunksVisited > 0) {
                    reward += ctx.newChunksVisited * 0.5;
                }
                reward -= 0.02; // 探索代价
                if (after.enemy != QLearningState.EnemyDistance.NONE) {
                    reward -= 1.0; // 撞见敌人
                }
            }
            case IDLE -> {
                if (before.enemy == QLearningState.EnemyDistance.NONE) {
                    reward += 0.1;
                } else {
                    reward -= 2.0; // 危险时待命是坏策略
                }
            }
        }

        // 通用: 死亡惩罚
        if (ai.getHealth() <= 0.0F) {
            reward -= 20.0;
        }

        return reward;
    }

    /**
     * 动作执行上下文，由调用方填充
     */
    public static class ActionContext {
        public boolean enemyKilled = false;
        public double dealtDamage = 0.0;
        public double tookDamage = 0.0;
        public int newChunksVisited = 0;

        public void reset() {
            enemyKilled = false;
            dealtDamage = 0.0;
            tookDamage = 0.0;
            newChunksVisited = 0;
        }
    }
}
